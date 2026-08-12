package com.aieducenter.admin.payment.application.dto.query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通道交互日志列表查询参数（BFF 透传 payment）。
 *
 * <p>筛选字段对齐 payment {@code GET /api/v1/payment-logs} 查询参数；{@code logTypes} 为多选，
 * 经 {@link com.aieducenter.admin.payment.infrastructure.PaymentClient} 展开为重复的 {@code logType} 参数。
 * 字段最终以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record PaymentLogQuery(

        /** 关联支付订单号 */
        String paymentOrderNo,

        /** 关联退款订单号 */
        String refundOrderNo,

        /** 日志类型多选（PAYMENT_REQUEST / PAYMENT_QUERY / PAYMENT_CANCEL / REFUND_REQUEST / REFUND_QUERY / PAYMENT_CALLBACK）；空 = 不限 */
        List<String> logTypes,

        /** 银行接口（如 ICBC_PAY） */
        String bankInterface,

        /** 是否成功 */
        Boolean success,

        /** 业务返回码 */
        String returnCode,

        /** 创建时间起（含） */
        LocalDateTime createdAtFrom,

        /** 创建时间止（含） */
        LocalDateTime createdAtTo
) {
}
