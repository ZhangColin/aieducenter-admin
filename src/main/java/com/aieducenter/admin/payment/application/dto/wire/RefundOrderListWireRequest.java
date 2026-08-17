package com.aieducenter.admin.payment.application.dto.wire;

import java.time.LocalDateTime;
import java.util.List;

/**
 * payment {@code GET /api/v1/refunds} 列表查询的 wire 请求——BFF 出站过滤参数的载荷形状。
 *
 * <p>与 {@link com.aieducenter.admin.payment.application.dto.query.RefundOrderQuery}（北向 controller 绑定）
 * 字段同构，由 {@code PaymentManagementAppService} 映射。独立成 wire 类型，是为了让 infrastructure
 * {@link com.aieducenter.admin.payment.infrastructure.PaymentClient} 只依赖 wire 层（而非 query 层），
 * 与 {@code AppRegistryClient} 依赖 {@code *WireRequest}、{@code PaymentOrderListWireRequest} 的约定一致——
 * query DTO 是应用层内部概念，不应被基础设施 import。</p>
 *
 * <p>枚举筛选项以 payment BaseEnum 的 Integer code 承载；{@code statuses} 为多选，经 {@code PaymentClient}
 * 展开为重复的 {@code status} 查询参数。金额筛选为 <strong>Long（分）</strong>，与 payment
 * {@code RefundOrderQuery} 同型透传、零换算（ADR-0011）。</p>
 *
 * @since 0.1.0
 */
public record RefundOrderListWireRequest(

        String refundOrderNo,

        String paymentOrderNo,

        String businessOrderNo,

        String businessSystemName,

        /** 退款状态多选（payment BaseEnum code）；空 = 不限 */
        List<Integer> statuses,

        Integer auditType,

        Long auditorId,

        /** 退款金额下限（分，含） */
        Long refundAmountMin,

        /** 退款金额上限（分，含） */
        Long refundAmountMax,

        /** 创建时间起（含） */
        LocalDateTime createdAtFrom,

        /** 创建时间止（含） */
        LocalDateTime createdAtTo
) {
}
