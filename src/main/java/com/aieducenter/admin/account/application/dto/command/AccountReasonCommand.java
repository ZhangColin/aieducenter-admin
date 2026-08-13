package com.aieducenter.admin.account.application.dto.command;

import jakarta.validation.constraints.Size;

/**
 * 解封 / 解锁北向命令——运营人员附带的<strong>可选</strong>操作原因。
 *
 * <p>解封（activate）/ 解锁（unlock）是低危可逆动作，{@code reason} 可空（落审计为 null），调用方按需
 * 附「申诉成功」「风控误判」等。校验对齐 identity {@code ManagementReasonCommand}（identity #69：
 * 仅 {@code @Size(max=500)}、可空、命令可整体缺省）。controller 以 {@code @RequestBody(required = false)}
 * 接收，未带 body 时 command 为 null。</p>
 *
 * <p>与 {@link DisableAccountCommand} 一样<strong>不含</strong> operator 身份——操作者经框架
 * {@code RequestContext} → {@code X-User-Id/X-User-Name} 透传，identity 据此审计（issue #49 / #53）。</p>
 *
 * @since 0.1.0
 */
public record AccountReasonCommand(

        @Size(max = 500, message = "操作原因长度不能超过500")
        String reason
) {
}
