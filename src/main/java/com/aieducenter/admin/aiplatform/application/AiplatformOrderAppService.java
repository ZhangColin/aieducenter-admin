package com.aieducenter.admin.aiplatform.application;

import java.util.List;

import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformOrderQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformOrderDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformOrderSummaryResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformPriceEntryResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformSourcePackageResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderSummaryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformPriceEntryWireResponse;
import com.aieducenter.admin.aiplatform.infrastructure.AiplatformClient;
import com.cartisan.openapi.client.BinaryResponse;
import com.cartisan.web.response.PageResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

/**
 * aiplatform 订单域 BFF 应用服务——订单读路径聚合（清单四维检索 / 详情含价目历史 / 源码包）。
 *
 * <p>admin 作为 BFF：调接口 + DTO 转换，不持业务逻辑、不持订单数据、不记业务审计（审计归
 * aiplatform）。下游错误已由 {@link AiplatformClient} 统一翻译为 {@link AiplatformUpstreamException}
 * （provider 信封原样透传，spec #62 定稿：aiplatform 不做映射），本层不再 try/catch。</p>
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
}
