package com.aieducenter.admin.payment.application.dto.wire;

import java.time.LocalDateTime;

/**
 * payment {@code PaymentLog} 列表项的 wire 镜像——仅包含 BFF 需要的诊断摘要字段。
 *
 * <p>用于 Jackson 反序列化 {@link com.cartisan.openapi.client.OpenApiClient} 响应，
 * 不与 payment 内部 DTO 耦合。字段最终以 payment 实现契约为准（issue #37）。</p>
 *
 * <p>payment 的 {@code PaymentLog} 全字段均为基础类型（logType / bankInterface / returnCode 在 payment
 * 领域里以 String 记、非 {@code BaseEnum}），故本镜像无枚举 code/name 拆分——与 payment
 * {@code PaymentLogResponse} 同形。</p>
 *
 * @since 0.1.0
 */
public record PaymentLogWireResponse(

        Long id,

        String paymentOrderNo,

        String refundOrderNo,

        /** 日志类型（PAYMENT_REQUEST / PAYMENT_QUERY / PAYMENT_CANCEL / REFUND_REQUEST / REFUND_QUERY / PAYMENT_CALLBACK） */
        String logType,

        /** 银行编码（如 ICBC） */
        String bankCode,

        /** 银行接口（如 ICBC_PAY） */
        String bankInterface,

        /** HTTP 状态码 */
        Integer httpStatus,

        /** 业务返回码 */
        String returnCode,

        /** 业务返回消息 */
        String returnMsg,

        /** 执行耗时（毫秒） */
        Long executionTime,

        /** 是否成功 */
        Boolean success,

        /** 错误信息 */
        String errorMessage,

        LocalDateTime createdAt
) {
}
