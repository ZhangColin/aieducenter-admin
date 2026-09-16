package com.aieducenter.admin.aiplatform.application;

import java.util.List;

import com.aieducenter.admin.aiplatform.application.dto.command.AiplatformPriceEntryRepriceCommand;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformPriceEntryQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformUnitPriceEntryRepriceResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformUnitPriceEntryResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformPriceEntryListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformPriceEntryRepriceWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformUnitPriceEntryRepriceWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformUnitPriceEntryWireResponse;
import com.aieducenter.admin.aiplatform.infrastructure.AiplatformClient;
import com.cartisan.web.response.PageResponse;
import org.springframework.stereotype.Service;

/**
 * aiplatform 单价表域 BFF 应用服务——核价（含历史行清单）＋原子改价（关旧行＋开新行单调用、
 * 可预发布未来生效）＋停用（即时下架），issue #68。provider 的「开行」端点不建北向
 * （种子脚本通道，spec #62）——web 改价只走原子 reprice。
 *
 * <p>admin 作为 BFF：调接口 + DTO 转换，不持业务逻辑、不持单价数据（单价表与重叠校验归
 * aiplatform 计量上下文）。预发布（未来 effectiveFrom）与同键区间重叠校验语义由 provider 承担，
 * 本层不代填时点、不预判区间——METER_004/005/006/007/008/009/010 错误信封已由
 * {@link AiplatformClient} 统一翻译为 {@link AiplatformUpstreamException} 原样透传（spec #62 定稿），
 * 本层不再 try/catch。操作者身份经框架 {@code RequestContext}→{@code X-User-Id/X-User-Name}
 * 自动透传（AppService 不经手），provider 落痕改价新行/停用被关行。</p>
 *
 * <p>分页（spec #62 平台分页统一决议目标态）：北向请求 page <strong>1-based</strong>，出站直传
 * 零换算；回显取 provider 回报的 page/size 原值（含 provider clamp 后的值），BFF 不重复夹取。</p>
 *
 * @since 0.1.0
 */
@Service
public class AiplatformPriceEntryAppService {

    private final AiplatformClient aiplatformClient;

    public AiplatformPriceEntryAppService(AiplatformClient aiplatformClient) {
        this.aiplatformClient = aiplatformClient;
    }

    /**
     * 单价行清单（透传 aiplatform，含历史行——价史全貌）——provider/model 精确等值过滤
     * （可缺省＝全量行）；排序服务端定死＝生效起点倒序（新段在前）；effectiveTo 为 null 即
     * 当前行。行带操作者两列（最近管理动作留痕；存量行/种子脚本种入行落 null）。
     */
    public PageResponse<AiplatformUnitPriceEntryResponse> list(AiplatformPriceEntryQuery query,
                                                               int page, int size) {
        PageResponse<AiplatformUnitPriceEntryWireResponse> wirePage = aiplatformClient.listPriceEntries(
                new AiplatformPriceEntryListWireRequest(query.provider(), query.model()), page, size);
        List<AiplatformUnitPriceEntryResponse> items = wirePage.items().stream()
                .map(AiplatformPriceEntryAppService::toEntry)
                .toList();
        // 回显 provider 的 page/size 原值（1-based，含 clamp 后值）——不是北向入参回声
        return new PageResponse<>(items, wirePage.total(), wirePage.page(), wirePage.size());
    }

    /**
     * 原子改价（透传 aiplatform）——单调用关当前行＋开新行（同事务，中途任一守卫失败两行都不动）。
     * effectiveFrom 可空＝缺省即时、含未来时点＝预发布（对齐供应商凌晨调价，窗口前旧价仍生效）；
     * 预发布与重叠校验语义归 provider。回执 {@code {closed, opened}} 双行（被关行保留原开行留痕，
     * 新行带改价操作者）。
     */
    public AiplatformUnitPriceEntryRepriceResponse reprice(String id,
                                                           AiplatformPriceEntryRepriceCommand command) {
        AiplatformUnitPriceEntryRepriceWireResponse receipt = aiplatformClient.repricePriceEntry(id,
                new AiplatformPriceEntryRepriceWireRequest(
                        command.unitPrice(), command.currency(), command.effectiveFrom()));
        return new AiplatformUnitPriceEntryRepriceResponse(toEntry(receipt.closed()), toEntry(receipt.opened()));
    }

    /**
     * 停用单价行（透传 aiplatform）——即时生效：关当前行不接新行，此后该匹配键用量进 unpriced
     * （缺价不伪装 0）；对未生效的预发布行停用＝钳到自身起点成空区间（从未生效）。回执＝被关行
     * 单行（停用操作者落被关行——唯一落点）。
     */
    public AiplatformUnitPriceEntryResponse deactivate(String id) {
        return toEntry(aiplatformClient.deactivatePriceEntry(id));
    }

    private static AiplatformUnitPriceEntryResponse toEntry(AiplatformUnitPriceEntryWireResponse wire) {
        return new AiplatformUnitPriceEntryResponse(
                wire.id(), wire.provider(), wire.model(), wire.tokenKind(), wire.tokenKindName(),
                wire.unitPrice(), wire.currency(), wire.effectiveFrom(), wire.effectiveTo(),
                wire.operatorId(), wire.operatorName());
    }
}
