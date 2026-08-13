package com.aieducenter.admin.payment.application.dto.wire;

import java.time.LocalDateTime;

/**
 * payment {@code GET /api/v1/operation-logs} 列表查询的 wire 请求——BFF 出站过滤参数的载荷形状。
 *
 * <p>与 {@link com.aieducenter.admin.payment.application.dto.query.OperationLogQuery}（北向 controller 绑定）
 * 字段同构，由 {@code PaymentManagementAppService} 映射。独立成 wire 类型，是为了让 infrastructure
 * {@link com.aieducenter.admin.payment.infrastructure.PaymentClient} 只依赖 wire 层（而非 query 层），
 * 与 {@code AppRegistryClient} 依赖 {@code *WireRequest}、{@code RefundOrderListWireRequest} 的约定一致——
 * query DTO 是应用层内部概念，不应被基础设施 import。</p>
 *
 * <p>枚举筛选项（{@code targetType} / {@code operation}）以 payment BaseEnum 的 Integer code 承载；
 * {@code operation} 为单选，对齐 payment {@code OperationLogQuery.operation} 的单值 EQUAL 契约。</p>
 *
 * <p><b>时间区间字段名对齐 payment 特例</b>：payment 的 {@code OperationLogQuery} 用 {@code createdAtStart}/
 * {@code createdAtEnd}（其余三个查询用 {@code createdAtFrom}/{@code createdAtTo}）。Spring 按记录组件名绑定查询参数，
 * 故 wire 此处用 {@code createdAtStart}/{@code createdAtEnd} 以匹配 payment 的真实参数名——否则日期筛选会在
 * payment 边界静默失效。应用层把北向 query 的 {@code createdAtFrom}/{@code createdAtTo} 映射到这两个字段。</p>
 *
 * @since 0.1.0
 */
public record OperationLogListWireRequest(

        Integer targetType,

        String targetNo,

        /** 操作类型（payment BaseEnum code）——单选，对齐 payment 单值契约 */
        Integer operation,

        Long operatorId,

        String operatorSystem,

        String result,

        /** 创建时间起（含）——对齐 payment OperationLogQuery.createdAtStart */
        LocalDateTime createdAtStart,

        /** 创建时间止（含）——对齐 payment OperationLogQuery.createdAtEnd */
        LocalDateTime createdAtEnd
) {
}
