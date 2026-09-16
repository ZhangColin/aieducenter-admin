package com.aieducenter.admin.aiplatform.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 平台订单写路径回执——北向出口，逐字镜像 aiplatform {@code OrderResponse}（用户面
 * 订单详情同构 DTO，下单/详情/取消/报价/支付共用拼装，13 字段含内嵌改价历史）。报价/
 * 取消/重试归档三写操作共用本回执：报价回执＝落定后的订单现值（金额/备注取最新价目行）＋
 * append-only 改价历史；取消回执＝已取消终态（cancelledAt 落定）；重试归档回执＝已归档
 * 终态（paidAt/archivedAt 双时点）。
 *
 * <p>与 {@link AiplatformOrderDetailResponse}（后台详情）的差异：无项目名/下单用户昵称/
 * PRD 快照/操作者留痕两肢——provider 回执即用户面 DTO；内嵌价目行为五字段（无操作者
 * 留痕）。回执即库内事实，查全量留痕转后台详情读口。</p>
 *
 * @since 0.1.0
 */
public record AiplatformOrderResponse(

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
     * 改价历史条目——北向出口，逐字镜像 aiplatform {@code PriceEntryResponse}（用户面）：
     * 时间 + 金额 + 备注（无操作者留痕——操作者两肢只在后台详情的价目行上）。
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
