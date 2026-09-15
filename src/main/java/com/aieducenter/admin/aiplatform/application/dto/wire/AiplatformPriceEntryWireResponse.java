package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.time.LocalDateTime;

/**
 * aiplatform 价目历史条目的 wire 镜像——与 aiplatform {@code BackofficePriceEntryResponse}
 * （#155 已冻结）字段同构，订单详情 {@link AiplatformOrderDetailWireResponse#priceEntries} 内嵌。
 *
 * <p>append-only 价目行的操作者两肢（{@code operatorId}/{@code operatorName}）为 admin 侧管理员
 * 标识（X-User-Id/X-User-Name 透传头落痕）；存量行/无头落 NULL，原值透传。</p>
 *
 * @since 0.1.0
 */
public record AiplatformPriceEntryWireResponse(

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
