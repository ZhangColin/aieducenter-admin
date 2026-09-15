package com.aieducenter.admin.aiplatform.application.dto.response;

import java.time.LocalDateTime;

/**
 * AI 平台订单价目历史条目——北向出口，逐字镜像 aiplatform {@code BackofficePriceEntryResponse}
 * （订单详情 {@link AiplatformOrderDetailResponse#priceEntries} 内嵌）。
 *
 * <p>操作者两肢为 admin 侧管理员标识（X-User-Id/X-User-Name 透传落痕）；存量行/无头 NULL。
 * 金额 {@code amount} 为 <strong>Long（分）</strong>，同型零换算（spec #62 类型口径）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformPriceEntryResponse(

        /** 价目行标识（TSID 十进制字符串，时间有序） */
        String id,

        /** 本次报价金额（分） */
        Long amount,

        /** 币种（v1 恒 CNY） */
        String currency,

        /** 报价备注（后台文本；可空） */
        String note,

        /** 操作者标识（admin 侧管理员 TSID 十进制串；存量行/无头 NULL） */
        String operatorId,

        /** 操作者昵称（直读展示；存量行/无头 NULL） */
        String operatorName,

        /** 报价/改价时间 */
        LocalDateTime createdAt
) {
}
