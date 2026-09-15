package com.aieducenter.admin.aiplatform.application.dto.response;

import java.time.LocalDateTime;

/**
 * AI 平台订单清单条目——北向出口，逐字镜像 aiplatform {@code BackofficeOrderSummaryResponse}
 * （#156 四维检索），无增删字段、无换型（spec #62 忠实透传）。
 *
 * <p>类型口径：{@code id}/{@code projectId} 为 String（TSID 十进制串）；{@code status} 为
 * Integer code + {@code statusName} 中文名（前端直读 *Name）；{@code amount} 为 Long（分）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformOrderSummaryResponse(

        /** 订单标识（TSID 十进制字符串） */
        String id,

        /** 所属项目标识（TSID 十进制字符串） */
        String projectId,

        /** 项目名（软引用缺档为 null） */
        String projectName,

        /** 下单用户显示名（下单账号可空或缺档为 null） */
        String ownerDisplayName,

        /** 订单状态（1=待报价 2=已报价 3=已支付 4=已归档 5=已取消） */
        Integer status,

        /** 状态中文名（provider 出口提供） */
        String statusName,

        /** 当前总价（分；待报价 NULL） */
        Long amount,

        /** 币种（v1 恒 CNY；待报价 NULL） */
        String currency,

        /** 下单时间 */
        LocalDateTime createdAt,

        /** 首次报价时点（改价不刷新；待报价 NULL） */
        LocalDateTime quotedAt
) {
}
