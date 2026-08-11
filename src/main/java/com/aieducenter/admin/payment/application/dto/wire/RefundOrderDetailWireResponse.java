package com.aieducenter.admin.payment.application.dto.wire;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * payment {@code RefundOrder} 详情的 wire 镜像——完整聚合投影。
 *
 * <p>与列表项 {@link RefundOrderWireResponse} 同源（payment {@code RefundOrder} 聚合），
 * 但承载<strong>详情全貌</strong>投影：列表项为扫描精简、详情为完整聚合，二者独立演进
 * （payment 契约定型后，详情端可能新增列表不需要的字段）。当前字段为 payment 数据模型速查所列
 * （issue #37），最终字段以 payment 实现契约为准。</p>
 *
 * @since 0.1.0
 */
public record RefundOrderDetailWireResponse(

        String refundOrderNo,

        String paymentOrderNo,

        String businessOrderNo,

        String businessSystemName,

        /** 退款状态（PENDING / REJECTED / APPROVED / REFUNDING / SUCCESS / FAILED） */
        String status,

        BigDecimal refundAmount,

        /** 审核类型（AUTO 免审 / MANUAL 人工） */
        String auditType,

        Long auditorId,

        String auditorName,

        LocalDateTime auditedAt,

        LocalDateTime createdAt
) {
}
