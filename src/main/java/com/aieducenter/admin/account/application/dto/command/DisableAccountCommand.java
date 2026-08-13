package com.aieducenter.admin.account.application.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 封号北向命令——前端（运营人员）提交的封号决策。
 *
 * <p>只承载运营人员的<strong>封号原因</strong>。封号是高危治理动作，identity 侧 {@code reason} 必填
 * （落审计 {@code account_operation_log.reason}），故 {@code reason} {@code @NotBlank}。
 * 校验对齐 identity {@code DisableAccountCommand}（identity #68：{@code @NotBlank} + {@code @Size(max=500)}）。</p>
 *
 * <p><strong>刻意不含</strong> operator 身份——操作者由 admin 服务端经框架 {@code RequestContext}
 * → {@code X-User-Id/X-User-Name} 透传（cartisan-openapi 自动带 header，identity 据此审计），
 * 前端无法伪造操作归属。这与 payment 退款审核（身份进 wire body）不同：identity 管理端点从
 * {@code RequestContext} 读 operator，故 admin 不在 body 里塞身份（issue #49 / #53）。</p>
 *
 * @since 0.1.0
 */
public record DisableAccountCommand(

        @NotBlank(message = "封号原因不能为空")
        @Size(max = 500, message = "封号原因长度不能超过500")
        String reason
) {
}
