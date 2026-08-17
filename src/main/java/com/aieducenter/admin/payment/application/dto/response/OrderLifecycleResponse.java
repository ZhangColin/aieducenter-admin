package com.aieducenter.admin.payment.application.dto.response;

import java.time.LocalDateTime;

/**
 * 订单生命周期响应——由 {@link com.aieducenter.admin.payment.application.dto.wire.OrderLifecycleWireResponse}
 * 逐字段映射而来，承载 payment 已合并（按时间排序）时间线的<strong>单个事件</strong>。
 *
 * <p>北向出口为 {@code ApiResponse<List<OrderLifecycleResponse>>} 扁平列表——逐字镜像 payment 契约、
 * <strong>无 orderNo 包装</strong>（orderNo 在北向路径参数上，issue #57 / ADR-0011）。合并在 payment 完成
 * （ADR-0002），admin 透传不改序、不本地合并；前端按 {@code source}（GATEWAY/OPERATION）渲染网关交互
 * 或行为者操作、直读 {@code actionName} 中文名。</p>
 *
 * @param id              源记录主键（同时间排序的稳定键）
 * @param source          来源标签：{@code "GATEWAY"} / {@code "OPERATION"}
 * @param createdAt       事件时间（payment 已排好序）
 * @param action          动作稳定 token（PAYMENT_REQUEST / AUDIT_APPROVE…）
 * @param actionName      动作显示名（中文）
 * @param outcome         结果 token（SUCCESS/FAILED）
 * @param performer       执行方（bankInterface / operatorName）
 * @param performerSystem 执行方系统（bankCode / operatorSystem）
 * @param detail          补充说明
 * @since 0.1.0
 */
public record OrderLifecycleResponse(

        Long id,

        String source,

        LocalDateTime createdAt,

        String action,

        String actionName,

        String outcome,

        String performer,

        String performerSystem,

        String detail
) {
}
