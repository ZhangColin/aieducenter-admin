package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.time.LocalDateTime;

/**
 * aiplatform 订单清单条目的 wire 镜像——与 aiplatform {@code BackofficeOrderSummaryResponse}
 * （#156 四维检索已冻结）字段同构。用于 Jackson 反序列化
 * {@link com.cartisan.openapi.client.OpenApiClient} 响应，不与 aiplatform 内部 DTO 耦合。
 *
 * <p>类型口径（spec #62）：{@code id}/{@code projectId} 为 <strong>String</strong>（TSID 十进制串）；
 * {@code status} 为 <strong>Integer code</strong> + {@code statusName} 中文名（provider 已配对，
 * BFF 透传不做 code→中文映射）；{@code amount} 为 <strong>Long（分）</strong>，同型零换算。</p>
 *
 * @since 0.1.0
 */
public record AiplatformOrderSummaryWireResponse(

        /** 订单标识（TSID 十进制字符串） */
        String id,

        /** 所属项目标识（TSID 十进制字符串） */
        String projectId,

        /** 项目名（软引用缺档为 null） */
        String projectName,

        /** 下单用户显示名（跨 BC 软引用取名；下单账号可空或缺档为 null） */
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
