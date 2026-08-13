package com.aieducenter.admin.account.application.dto.wire;

/**
 * 账号状态操作出站请求体——identity {@code DisableAccountCommand} / {@code ManagementReasonCommand}
 * 的 wire 镜像，三个状态写端点（disable / activate / unlock）共用。
 *
 * <p>三个端点的 identity 请求体形状一致——都是 {@code {reason}}：封号 reason 必填（由北向
 * {@link com.aieducenter.admin.account.application.dto.command.DisableAccountCommand} 的 {@code @NotBlank}
 * 兜）、解封/解锁 reason 可空（透传 null）。故共用一个 wire 记录，必填/可空的差异在北向 command 层校验。</p>
 *
 * <p><strong>不含</strong> operator 身份——identity 管理端点从 {@code RequestContext}（经
 * {@code X-User-Id/X-User-Name} header）读 operator 审计，不在请求体里收（与 payment
 * {@code AuditRefundWireRequest} 把 auditorId/auditorName 塞 body 不同）。admin 侧 operator 身份由
 * 框架 cartisan-openapi 自动从 {@code RequestContext} 带入出站 header，零显式注入（issue #49 / #53）。</p>
 *
 * @param reason 操作原因（disable 必填、activate/unlock 可空）
 * @since 0.1.0
 */
public record AccountReasonWireRequest(

        String reason
) {
}
