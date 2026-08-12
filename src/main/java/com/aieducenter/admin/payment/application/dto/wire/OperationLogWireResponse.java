package com.aieducenter.admin.payment.application.dto.wire;

import java.time.LocalDateTime;

/**
 * payment {@code OperationLog} 列表项的 wire 镜像——仅包含 BFF 需要的合规追溯字段。
 *
 * <p>用于 Jackson 反序列化 {@link com.cartisan.openapi.client.OpenApiClient} 响应，
 * 不与 payment 内部 DTO 耦合。字段最终以 payment 实现契约为准（issue #37）。</p>
 *
 * <p>枚举字段（{@code targetType} / {@code operation}）以单值 String 透传枚举名——与
 * {@code OrderLifecycleWireResponse.LifecycleEventWireResponse}（操作字段组）同形、与
 * {@code RefundOrderWireResponse} 的「状态以 String 透传」约定一致；admin 作为 BFF 不拥有 payment 的
 * 状态语义，展示文案（i18n）由前端按枚举名映射。</p>
 *
 * @since 0.1.0
 */
public record OperationLogWireResponse(

        Long id,

        /** 操作目标类型（PAYMENT / REFUND） */
        String targetType,

        /** 操作目标单号 */
        String targetNo,

        /** 操作类型（AUDIT_APPROVE / AUDIT_REJECT / NOTIFY_RESEND / …） */
        String operation,

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
