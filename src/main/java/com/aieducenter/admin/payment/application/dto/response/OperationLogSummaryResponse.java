package com.aieducenter.admin.payment.application.dto.response;

import java.time.LocalDateTime;

/**
 * 订单操作记录列表项响应——由 {@link com.aieducenter.admin.payment.application.dto.wire.OperationLogWireResponse}
 * 映射而来，屏蔽 wire 层细节。
 *
 * <p>记录行为者对订单的操作（审核通过/拒绝、通知重发等），用于合规追溯与运营审计
 * （CONTEXT.md · OperationLog）。admin 原值透传、不做翻译。</p>
 *
 * @since 0.1.0
 */
public record OperationLogSummaryResponse(

        Long id,

        String targetType,

        String targetNo,

        String operation,

        Long operatorId,

        String operatorName,

        String operatorSystem,

        String result,

        String remark,

        LocalDateTime createdAt
) {
}
