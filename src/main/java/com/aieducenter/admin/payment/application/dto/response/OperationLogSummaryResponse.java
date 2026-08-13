package com.aieducenter.admin.payment.application.dto.response;

import java.time.LocalDateTime;

/**
 * 订单操作记录列表项响应——由 {@link com.aieducenter.admin.payment.application.dto.wire.OperationLogWireResponse}
 * 映射而来，屏蔽 wire 层细节。
 *
 * <p>记录行为者对订单的操作（审核通过/拒绝、通知重发等），用于合规追溯与运营审计
 * （CONTEXT.md · OperationLog）。admin 原值透传、不做翻译。</p>
 *
 * <p>枚举出口规则（ADR-0009）：{@code targetType}/{@code targetTypeName}、{@code operation}/{@code operationName}，
 * 枚举 code 为 Integer；前端直读 {@code *Name}，不在端侧做枚举→中文映射。中文名由 payment 出口提供、admin 透传。</p>
 *
 * @since 0.1.0
 */
public record OperationLogSummaryResponse(

        Long id,

        Integer targetType,

        String targetTypeName,

        String targetNo,

        Integer operation,

        String operationName,

        Long operatorId,

        String operatorName,

        String operatorSystem,

        String result,

        String remark,

        LocalDateTime createdAt
) {
}
