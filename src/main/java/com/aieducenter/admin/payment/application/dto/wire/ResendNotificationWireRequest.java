package com.aieducenter.admin.payment.application.dto.wire;

/**
 * 通知重发出站请求体——payment {@code notifications/resend} 的 wire 镜像。
 *
 * <p>承载<strong>操作者身份</strong>（{@code operatorId} / {@code operatorName}），由 admin 应用层从
 * {@code RequestContext} 注入（见 {@link com.aieducenter.admin.payment.application.PaymentManagementAppService#resendPaymentNotification}
 * / {@link com.aieducenter.admin.payment.application.PaymentManagementAppService#resendRefundNotification}），
 * <strong>不</strong>来自前端请求体——重发端点无前端决策（不像退款审核带 {@code agreed}），前端只发空 POST，
 * 操作者归属由服务端注入、不可伪造。payment 据此落 {@code OperationLog}（{@code operation=NOTIFY_RESEND}）。</p>
 *
 * <p>字段名对齐 payment {@code OperationLog} 的操作者字段（{@code operatorId} / {@code operatorName}），
 * 而非退款审核的 {@code auditorId} / {@code auditorName}——重发的发起者是「操作者」而非「审核人」，
 * payment 在 {@code OperationLog} 中以 {@code operator*} 字段记录该动作的归属（issue #37、#47）。</p>
 *
 * <p>通知重发<strong>不改订单状态</strong>（仅补发投递，payment ADR-0001）；payment 返回当前订单聚合
 * （与详情同形），以便前端确认状态未变并刷新生命周期 tab 中新增的 {@code NOTIFY_RESEND} 记录。
 * 字段以 payment 实现时的契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record ResendNotificationWireRequest(

        /** 操作者 ID（admin Operator.id，来自 RequestContext.getUserId()） */
        Long operatorId,

        /** 操作者姓名（admin Operator 昵称，来自 RequestContext.getUserName()） */
        String operatorName
) {
}
