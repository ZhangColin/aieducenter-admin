package com.aieducenter.admin.payment.application.dto.wire;

/**
 * 退款审核出站请求体——payment {@code AuditRefundCommand} 的 wire 镜像。
 *
 * <p>承载<strong>完整</strong>的审核载荷：审核决策（{@code agreed}）+ 备注（{@code remark}）+ 操作者身份
 * （{@code auditorId} / {@code auditorName}）。操作者身份由 admin 应用层从 {@code RequestContext}
 * 注入（见 {@link com.aieducenter.admin.payment.application.PaymentManagementAppService#auditRefund}），
 * <strong>不</strong>来自前端请求体——前端只能表达「同意/拒绝」，审核人归属不可伪造（payment 落
 * {@code OperationLog}，auditType=MANUAL）。</p>
 *
 * <p>字段对齐 payment {@code AuditRefundCommand}（issue #37）：{@code agreed=true} 审核通过、
 * {@code false} 审核拒绝；{@code remark} 选填（≤512）。</p>
 *
 * @since 0.1.0
 */
public record AuditRefundWireRequest(

        /** 审核人 ID（admin Operator.id，来自 RequestContext.getUserId()） */
        Long auditorId,

        /** 审核人姓名（admin Operator 昵称，来自 RequestContext.getUserName()） */
        String auditorName,

        /** 审核决策：{@code true}=通过（approve）、{@code false}=拒绝（reject） */
        Boolean agreed,

        /** 审核备注（选填，payment 侧 ≤512） */
        String remark
) {
}
