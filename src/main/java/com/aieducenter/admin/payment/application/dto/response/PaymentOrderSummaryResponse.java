package com.aieducenter.admin.payment.application.dto.response;

import java.time.LocalDateTime;

/**
 * 支付订单列表项响应——由 {@link com.aieducenter.admin.payment.application.dto.wire.PaymentOrderWireResponse}
 * 映射而来，屏蔽 wire 层细节。
 *
 * <p>枚举出口规则（ADR-0009）：凡枚举字段配中文名——{@code status}/{@code statusName}、{@code payMode}/
 * {@code payModeName}、{@code accessType}/{@code accessTypeName}、{@code paymentChannel}/
 * {@code paymentChannelName}，枚举 code 为 Integer。前端直读 {@code *Name} 显示，不在端侧做枚举→中文映射。
 * 中文名由 payment 出口提供、admin 透传。</p>
 *
 * <p>金额出口规则（ADR-0011）：{@code amount} 为 <strong>Long（分）</strong>，与 payment 同型透传、零换算；
 * 北向 JSON 中呈 string 形态（框架全局 Long→{@code ToStringSerializer}），前端算术入口统一转 {@code Number}。</p>
 *
 * @since 0.1.0
 */
public record PaymentOrderSummaryResponse(

        String paymentOrderNo,

        String businessOrderNo,

        String businessSystemName,

        Integer status,

        String statusName,

        /** 支付金额（分） */
        Long amount,

        Integer payMode,

        String payModeName,

        Integer accessType,

        String accessTypeName,

        Integer paymentChannel,

        String paymentChannelName,

        LocalDateTime paidAt,

        LocalDateTime createdAt
) {
}
