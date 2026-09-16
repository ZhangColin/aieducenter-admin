package com.aieducenter.admin.aiplatform.application;

import java.util.List;

import com.aieducenter.admin.aiplatform.application.dto.command.AiplatformOrderCancelCommand;
import com.aieducenter.admin.aiplatform.application.dto.command.AiplatformOrderQuoteCommand;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformOrderQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformOrderDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformOrderResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformOrderSummaryResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformPriceEntryResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformSourcePackageResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderCancelWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderQuoteWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderSummaryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformPriceEntryWireResponse;
import com.aieducenter.admin.aiplatform.infrastructure.AiplatformClient;
import com.cartisan.openapi.client.BinaryResponse;
import com.cartisan.web.response.PageResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

/**
 * aiplatform 订单域 BFF 应用服务——订单读路径聚合（清单四维检索 / 详情含价目历史 / 源码包）
 * ＋写路径三操作（报价/改价、运营取消、重试归档，issue #70）。
 *
 * <p>admin 作为 BFF：调接口 + DTO 转换，不持业务逻辑、不持订单数据、不记业务审计（审计归
 * aiplatform）。下游错误已由 {@link AiplatformClient} 统一翻译为 {@link AiplatformUpstreamException}
 * （provider 信封原样透传，spec #62 定稿：aiplatform 不做映射），本层不再 try/catch。「已报价态
 * 重复提交＝改价」等状态机语义由 provider 承担，本层不解释不预判。操作者身份经框架
 * {@code RequestContext}→{@code X-User-Id/X-User-Name} 自动透传（AppService 不经手），
 * provider 落痕价目行/订单行。</p>
 *
 * <p>分页（spec #62，平台分页统一决议目标态——首个按目标态实现的北向面）：北向请求 page
 * <strong>1-based</strong>，出站直传零换算；回显取 provider 回报的 page/size 原值（含 provider
 * clamp 后的值），BFF 不重复夹取。</p>
 *
 * @since 0.1.0
 */
@Service
public class AiplatformOrderAppService {

    private final AiplatformClient aiplatformClient;

    public AiplatformOrderAppService(AiplatformClient aiplatformClient) {
        this.aiplatformClient = aiplatformClient;
    }

    /**
     * 订单清单（四维检索，透传 aiplatform）——状态多选/创建时间区间/externalId/订单号精确，
     * 新单在前（provider 定死 TSID 倒序）。
     */
    public PageResponse<AiplatformOrderSummaryResponse> list(AiplatformOrderQuery query, int page, int size) {
        PageResponse<AiplatformOrderSummaryWireResponse> wirePage = aiplatformClient.listOrders(
                new AiplatformOrderListWireRequest(query.status(), query.createdFrom(), query.createdTo(),
                        query.externalId(), query.orderId()),
                page, size);
        List<AiplatformOrderSummaryResponse> items = wirePage.items().stream()
                .map(AiplatformOrderAppService::toSummary)
                .toList();
        // 回显 provider 的 page/size 原值（1-based，含 clamp 后值）——不是北向入参回声
        return new PageResponse<>(items, wirePage.total(), wirePage.page(), wirePage.size());
    }

    /**
     * 订单详情（透传 aiplatform）——报价依据全量：PRD 快照、价目历史（append-only 带操作者）、
     * 状态时点组。
     */
    public AiplatformOrderDetailResponse getDetail(String id) {
        return toDetail(aiplatformClient.getOrder(id));
    }

    /**
     * 下载订单源码包（透传 aiplatform）——tar.gz 二进制流 + provider 响应头 raw 值。
     */
    public AiplatformSourcePackageResponse getSourcePackage(String id) {
        BinaryResponse binary = aiplatformClient.downloadSourcePackage(id);
        // HttpHeaders 大小写不敏感取值；provider 契约恒带两头——缺头兜底为通用二进制附件（HTTP 合法性，
        // 非契约发明）
        String contentType = binary.headers().firstValue(HttpHeaders.CONTENT_TYPE)
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        String contentDisposition = binary.headers().firstValue(HttpHeaders.CONTENT_DISPOSITION)
                .orElse("attachment");
        return new AiplatformSourcePackageResponse(binary.body(), contentType, contentDisposition);
    }

    /**
     * 提交报价/改价（透传 aiplatform，issue #70）——「已报价态重复提交＝改价」语义由 provider
     * 承担（状态不变、append-only 价目行留痕、订单现值取最新行），BFF 不解释不预判。回执＝
     * provider 用户面同构 OrderResponse（金额/备注取最新价目行＋改价历史新→旧）。
     */
    public AiplatformOrderResponse quote(String id, AiplatformOrderQuoteCommand command) {
        return toReceipt(aiplatformClient.quoteOrder(id,
                new AiplatformOrderQuoteWireRequest(command.amount(), command.note())));
    }

    /**
     * 运营取消订单（透传 aiplatform，issue #70）——限未支付态（语义与用户取消一致：项目解冻
     * 回迭代），原因必填（运营内部口径留档）。回执＝已取消终态（cancelledAt 落定）。
     */
    public AiplatformOrderResponse cancel(String id, AiplatformOrderCancelCommand command) {
        return toReceipt(aiplatformClient.cancelOrder(id,
                new AiplatformOrderCancelWireRequest(command.reason())));
    }

    /**
     * 重试归档（透传 aiplatform，issue #70）——已支付未归档卡单的手动补完结（一事务内订单＋
     * 项目归档，补通知与知识沉淀 best-effort），幂等由 provider 既有守卫保证。回执＝已归档
     * 终态（paidAt/archivedAt 双时点）。
     */
    public AiplatformOrderResponse retryArchive(String id) {
        return toReceipt(aiplatformClient.retryArchiveOrder(id));
    }

    private static AiplatformOrderSummaryResponse toSummary(AiplatformOrderSummaryWireResponse wire) {
        return new AiplatformOrderSummaryResponse(
                wire.id(), wire.projectId(), wire.projectName(), wire.ownerDisplayName(),
                wire.status(), wire.statusName(), wire.amount(), wire.currency(),
                wire.createdAt(), wire.quotedAt());
    }

    private static AiplatformOrderDetailResponse toDetail(AiplatformOrderDetailWireResponse wire) {
        return new AiplatformOrderDetailResponse(
                wire.id(), wire.projectId(), wire.projectName(), wire.ownerDisplayName(),
                wire.status(), wire.statusName(), wire.amount(), wire.currency(), wire.note(),
                wire.priceEntries() == null ? null : wire.priceEntries().stream()
                        .map(AiplatformOrderAppService::toPriceEntry)
                        .toList(),
                wire.prdSnapshot(),
                wire.createdAt(), wire.quotedAt(), wire.paidAt(), wire.archivedAt(),
                wire.archiveOperatorId(), wire.archiveOperatorName(),
                wire.cancelledAt(), wire.cancelReason(), wire.cancelOperatorId(), wire.cancelOperatorName());
    }

    private static AiplatformPriceEntryResponse toPriceEntry(AiplatformPriceEntryWireResponse wire) {
        return new AiplatformPriceEntryResponse(
                wire.id(), wire.amount(), wire.currency(), wire.note(),
                wire.operatorId(), wire.operatorName(), wire.createdAt());
    }

    /** 三写操作共用回执映射：wire（provider 用户面同构 OrderResponse）→ 北向逐字段（五字段价目行）。 */
    private static AiplatformOrderResponse toReceipt(AiplatformOrderWireResponse wire) {
        return new AiplatformOrderResponse(
                wire.id(), wire.projectId(), wire.status(), wire.statusName(),
                wire.amount(), wire.currency(), wire.note(), wire.quotedAt(),
                wire.priceEntries() == null ? null : wire.priceEntries().stream()
                        .map(AiplatformOrderAppService::toReceiptEntry)
                        .toList(),
                wire.createdAt(), wire.cancelledAt(), wire.paidAt(), wire.archivedAt());
    }

    private static AiplatformOrderResponse.PriceEntry toReceiptEntry(AiplatformOrderWireResponse.PriceEntry wire) {
        return new AiplatformOrderResponse.PriceEntry(
                wire.id(), wire.amount(), wire.currency(), wire.note(), wire.createdAt());
    }
}
