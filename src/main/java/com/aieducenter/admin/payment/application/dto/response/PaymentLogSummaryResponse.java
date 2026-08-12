package com.aieducenter.admin.payment.application.dto.response;

import java.time.LocalDateTime;

/**
 * 通道交互日志列表项响应——由 {@link com.aieducenter.admin.payment.application.dto.wire.PaymentLogWireResponse}
 * 映射而来，屏蔽 wire 层细节。
 *
 * <p>用于排查银行/通道网关的机机交互问题（CONTEXT.md · PaymentLog）。payment 的 {@code PaymentLog}
 * 全字段为基础类型（无枚举语义），admin 原值透传、不做翻译。</p>
 *
 * @since 0.1.0
 */
public record PaymentLogSummaryResponse(

        Long id,

        String paymentOrderNo,

        String refundOrderNo,

        String logType,

        String bankCode,

        String bankInterface,

        Integer httpStatus,

        String returnCode,

        String returnMsg,

        Long executionTime,

        Boolean success,

        String errorMessage,

        LocalDateTime createdAt
) {
}
