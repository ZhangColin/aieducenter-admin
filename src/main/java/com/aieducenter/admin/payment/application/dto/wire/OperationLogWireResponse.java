package com.aieducenter.admin.payment.application.dto.wire;

import java.time.LocalDateTime;

/**
 * payment {@code OperationLog} 列表项的 wire 镜像——仅包含 BFF 需要的合规追溯字段。
 *
 * <p>用于 Jackson 反序列化 {@link com.cartisan.openapi.client.OpenApiClient} 响应，
 * 不与 payment 内部 DTO 耦合。字段以 payment 实现契约为准。</p>
 *
 * <p>枚举出口规则（ADR-0009）：{@code targetType}/{@code targetTypeName}、{@code operation}/{@code operationName}，
 * 枚举 code 为 Integer。中文名由 payment 出口提供、admin 透传（与 {@code RefundOrderWireResponse} 的
 * 「枚举 code + *Name」约定一致）。</p>
 *
 * @since 0.1.0
 */
public record OperationLogWireResponse(

        Long id,

        /** 操作目标类型（payment BaseEnum code：PAYMENT / REFUND） */
        Integer targetType,

        /** 操作目标类型中文名（payment 出口提供） */
        String targetTypeName,

        /** 操作目标单号 */
        String targetNo,

        /** 操作类型（payment BaseEnum code：AUDIT_APPROVE / AUDIT_REJECT / NOTIFY_RESEND / …） */
        Integer operation,

        /** 操作类型中文名（payment 出口提供） */
        String operationName,

        /** 操作者 ID（系统发起的动作可空） */
        Long operatorId,

        /** 操作者名称（可空） */
        String operatorName,

        /** 来源系统（调用方 appName，可空） */
        String operatorSystem,

        /** 操作结果（SUCCESS / FAILED） */
        String result,

        /** 备注（决策依据等，可空） */
        String remark,

        LocalDateTime createdAt
) {
}
