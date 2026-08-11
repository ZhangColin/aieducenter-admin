package com.aieducenter.admin.payment.application.dto.wire;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * payment {@code PaymentOrder} 列表项的 wire 镜像——仅包含 BFF 需要的字段。
 *
 * <p>用于 Jackson 反序列化 {@link com.cartisan.openapi.client.OpenApiClient} 响应，
 * 不与 payment 内部 DTO 耦合。字段最终以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record PaymentOrderWireResponse(

        String paymentOrderNo,

        String businessOrderNo,

        String businessSystemName,

        /** 订单状态（PENDING / PAID / FAILED / CANCELLED / EXPIRED） */
        String status,

        BigDecimal amount,

        /** 支付方式（WECHAT / ALIPAY / UNIONPAY） */
        String payMode,

        String accessType,

        String paymentChannel,

        LocalDateTime paidAt,

        LocalDateTime createdAt
) {
}