package com.aieducenter.admin.payment.application.dto.wire;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * payment {@code PaymentOrder} 详情的 wire 镜像——完整聚合投影。
 *
 * <p>与列表项 {@link PaymentOrderWireResponse} 同源（payment {@code PaymentOrder} 聚合），
 * 但承载<strong>详情全貌</strong>投影：列表项为扫描精简、详情为完整聚合，二者独立演进
 * （payment 契约定型后，详情端可能新增列表不需要的字段）。当前字段为 payment 数据模型速查所列
 * （issue #37），最终字段以 payment 实现契约为准。</p>
 *
 * @since 0.1.0
 */
public record PaymentOrderDetailWireResponse(

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
