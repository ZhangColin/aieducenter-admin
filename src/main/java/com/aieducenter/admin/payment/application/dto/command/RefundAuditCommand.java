package com.aieducenter.admin.payment.application.dto.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 退款审核北向命令——前端（运营人员）提交的审核决策。
 *
 * <p>只承载运营人员的<strong>决策意图</strong>：同意（{@code agreed=true}）或拒绝（{@code agreed=false}），
 * 外加可选备注。<strong>刻意不含</strong> {@code auditorId} / {@code auditorName}——审核人身份由
 * admin 服务端从 {@code RequestContext} 注入（{@code RequestContext.getUserId()} /
 * {@code getUserName()}），前端无法伪造审核归属（见 payment-admin spec「操作者身份透传」、issue #42）。</p>
 *
 * <p>校验对齐 payment {@code AuditRefundCommand}（issue #37）：{@code agreed} 非空、{@code remark} ≤512。</p>
 *
 * @since 0.1.0
 */
public record RefundAuditCommand(

        @NotNull(message = "审核结果不能为空")
        Boolean agreed,

        @Size(max = 512, message = "审核备注长度不能超过512")
        String remark
) {
}
