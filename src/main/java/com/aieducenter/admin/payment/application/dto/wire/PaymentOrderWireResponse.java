package com.aieducenter.admin.payment.application.dto.wire;

import java.time.LocalDateTime;

/**
 * payment {@code PaymentOrder} 列表项的 wire 镜像——仅包含 BFF 需要的字段。
 *
 * <p>用于 Jackson 反序列化 {@link com.cartisan.openapi.client.OpenApiClient} 响应，
 * 不与 payment 内部 DTO 耦合。字段以 payment 实现契约为准。</p>
 *
 * <p>枚举出口规则（ADR-0009）：每个枚举字段以 payment {@code BaseEnum} 的 <strong>Integer code</strong>
 * 承载，并配 {@code *Name} 中文名（{@code status}/{@code statusName}、{@code payMode}/{@code payModeName}、
 * {@code accessType}/{@code accessTypeName}、{@code paymentChannel}/{@code paymentChannelName}）。中文名由
 * payment 出口提供、admin 透传，不在 admin 侧做 code→中文映射。</p>
 *
 * <p>金额出口规则（ADR-0011）：{@code amount} 为 <strong>Long（分）</strong>，与 payment
 * {@code PaymentOrderResponse.amount} 同型透传、零换算。</p>
 *
 * @since 0.1.0
 */
public record PaymentOrderWireResponse(

        String paymentOrderNo,

        String businessOrderNo,

        String businessSystemName,

        /** 订单状态（payment BaseEnum code） */
        Integer status,

        /** 订单状态中文名（payment 出口提供） */
        String statusName,

        /** 支付金额（分） */
        Long amount,

        /** 支付方式（payment BaseEnum code） */
        Integer payMode,

        /** 支付方式中文名（payment 出口提供） */
        String payModeName,

        /** 接入类型（payment BaseEnum code） */
        Integer accessType,

        /** 接入类型中文名（payment 出口提供） */
        String accessTypeName,

        /** 支付渠道（payment BaseEnum code） */
        Integer paymentChannel,

        /** 支付渠道中文名（payment 出口提供） */
        String paymentChannelName,

        LocalDateTime paidAt,

        LocalDateTime createdAt
) {
}
