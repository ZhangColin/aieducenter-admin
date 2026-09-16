package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.time.LocalDateTime;
import java.util.List;

/**
 * aiplatform 订单写路径回执的 wire 镜像——与 aiplatform {@code OrderResponse}（用户面
 * 订单详情同构 DTO，下单/详情/取消/报价/支付共用拼装）字段同构（13 字段，含内嵌改价历史），
 * 逐字镜像、无增删。报价/取消/重试归档三写操作共用本回执。
 *
 * <p>与 {@link AiplatformOrderDetailWireResponse}（后台详情 21 字段）的差异：无
 * projectName/ownerDisplayName/prdSnapshot/取消归档操作者留痕两肢——回执是 provider 的
 * 用户面 DTO（含 {@code statusName} 中文名）；内嵌价目行为<strong>五字段</strong>
 * {@link PriceEntry}（无操作者留痕，区别于后台详情的七字段价目行）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformOrderWireResponse(

        /** 订单标识（TSID 十进制字符串） */
        String id,

        /** 所属项目标识（TSID 十进制字符串） */
        String projectId,

        /** 订单状态（1=待报价 2=已报价 3=已支付 4=已归档 5=已取消） */
        Integer status,

        /** 状态中文名（provider 出口提供） */
        String statusName,

        /** 当前总价（分；待报价 NULL，改价取最新价目行） */
        Long amount,

        /** 币种（v1 恒 CNY；待报价 NULL） */
        String currency,

        /** 当前后台备注（最新价目行 note；待报价 NULL） */
        String note,

        /** 首次报价时点（改价不刷新；待报价 NULL） */
        LocalDateTime quotedAt,

        /** 改价历史（新 → 旧：时间 + 金额 + 备注，只追加；待报价空表） */
        List<PriceEntry> priceEntries,

        /** 下单时间（快照冻结时点） */
        LocalDateTime createdAt,

        /** 取消时点（未取消 NULL） */
        LocalDateTime cancelledAt,

        /** 支付成功时点（未支付 NULL） */
        LocalDateTime paidAt,

        /** 归档时点（未归档 NULL） */
        LocalDateTime archivedAt
) {

    /**
     * 改价历史条目的 wire 镜像——与 aiplatform {@code PriceEntryResponse}（用户面，#29
     * 交易环②）字段同构：时间 + 金额 + 备注（无操作者留痕——操作者两肢只在后台详情的
     * 价目行上，见 {@link AiplatformPriceEntryWireResponse}）。
     *
     * @param id        价目行标识（TSID 十进制字符串，时间有序）
     * @param amount    本次报价金额（分）
     * @param currency  币种（v1 恒 CNY）
     * @param note      报价备注（后台文本；可空）
     * @param createdAt 报价/改价时间
     */
    public record PriceEntry(
            String id,
            Long amount,
            String currency,
            String note,
            LocalDateTime createdAt
    ) {
    }
}
