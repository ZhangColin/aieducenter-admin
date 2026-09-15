package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.time.Instant;

/**
 * aiplatform 成本域四读口的 wire 时间窗——BFF 出站查询的载荷形状（{@code from}/{@code to} 两参数）。
 *
 * <p>与北向 {@link com.aieducenter.admin.aiplatform.application.dto.query.AiplatformCostQuery}
 * 字段同构，由 {@code AiplatformCostAppService} 映射。独立成 wire 类型，是为了让 infrastructure
 * {@link com.aieducenter.admin.aiplatform.infrastructure.AiplatformClient} 只依赖 wire 层
 * （query DTO 是应用层内部概念，不应被基础设施 import——订单/项目/沙箱同款约定）。</p>
 *
 * <p>口径（aiplatform {@code BackofficeCostController} 四端点共用）：半开区间 {@code [from, to)}、
 * ISO-8601 Instant（UTC 带 Z）；provider 侧可缺省（缺省＝该侧不限），北向<strong>必填</strong>
 * （issue #67：逐字镜像、不设默认窗口）——BFF 出站恒带双参。出站序列化取
 * {@code Instant.toString()}（ISO_INSTANT 确定形，秒恒在场）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformCostWindowWireRequest(

        /** 窗口起点（含；ISO-8601 Instant，UTC 带 Z） */
        Instant from,

        /** 窗口终点（不含；ISO-8601 Instant，UTC 带 Z） */
        Instant to
) {
}
