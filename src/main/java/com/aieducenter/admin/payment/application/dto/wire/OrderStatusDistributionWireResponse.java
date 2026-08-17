package com.aieducenter.admin.payment.application.dto.wire;

import java.util.List;

/**
 * payment {@code GET /api/v1/stats/orders/status-distribution} 的 wire 镜像——订单状态在途分布。
 *
 * <p>逐字镜像 payment 的 {@code StatusDistributionResponse}（ADR-0011 / issue #60）：全局快照（无时间窗），
 * 所有状态枚举值都出现（补零），退款待审核积压为<strong>嵌套</strong> {@code refundBacklog{pendingCount,
 * pendingAmount}}</strong>（非扁平字段——#60 前字段名+嵌套双不符恒 null，积压金额整个丢失）。分桶
 * {@code count}/{@code amount} 均为 {@code Long}（金额为分）。聚合归 payment（issue #37 一档统计）；
 * admin 作为 BFF 纯透传——不做 admin 侧聚合/重算。</p>
 *
 * @param paymentStatuses 支付各状态分布（PaymentStatus 全列）
 * @param refundStatuses  退款各状态分布（RefundStatus 全列）
 * @param refundBacklog   退款待审核积压（PENDING）——笔数 + 金额（分）
 * @since 0.1.0
 */
public record OrderStatusDistributionWireResponse(

        /** 支付单各状态分布 */
        List<PaymentStatusBucketWireResponse> paymentStatuses,

        /** 退款单各状态分布 */
        List<RefundStatusBucketWireResponse> refundStatuses,

        /** 退款待审核积压（status=PENDING 的退款单 roll-up；运营关注积压，单独投出） */
        BacklogWireResponse refundBacklog
) {

    /**
     * 支付状态分桶——某状态下的在途笔数·金额。
     *
     * <p>枚举出口规则（ADR-0009）：{@code status} 为 payment PaymentStatus 的 Integer code、配 {@code statusName}
     * 中文名。{@code amount} 语义为支付金额（分）。</p>
     *
     * @param status     状态（PaymentStatus code）
     * @param statusName 状态中文名（payment 出口提供）
     * @param count      该状态笔数
     * @param amount     该状态金额（分）
     */
    public record PaymentStatusBucketWireResponse(

            Integer status,

            String statusName,

            Long count,

            Long amount
    ) {
    }

    /**
     * 退款状态分桶——某状态下的在途笔数·金额（形状与支付分桶同构，payment 源码即两个独立 record）。
     *
     * @param status     状态（RefundStatus code）
     * @param statusName 状态中文名（payment 出口提供）
     * @param count      该状态笔数
     * @param amount     该状态金额（分）
     */
    public record RefundStatusBucketWireResponse(

            Integer status,

            String statusName,

            Long count,

            Long amount
    ) {
    }

    /**
     * 退款待审核积压——PENDING 退款单的笔数与金额（分）。
     *
     * @param pendingCount  待审核笔数
     * @param pendingAmount 待审核积压金额（分）——#60 起首次透出
     */
    public record BacklogWireResponse(

            Long pendingCount,

            Long pendingAmount
    ) {
    }
}
