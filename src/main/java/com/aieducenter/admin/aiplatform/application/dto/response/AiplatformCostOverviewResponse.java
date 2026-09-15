package com.aieducenter.admin.aiplatform.application.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 平台成本全局总览响应（BFF 北向出口）——逐字镜像 aiplatform {@code BackofficeCostOverviewResponse}
 * （issue #67，spec #62 忠实透传）：窗口回显 + 五档总量 + 平台成本（币种分桶）+ 分模型/分智能体两分解。
 *
 * <p>纯平台 token 成本观测（与报价脱钩）；改价不溯及（历史事件按当时价）。{@code byAgentKind} 的
 * {@code agentKind} 为裸维度串原值透传（dims 原值，不臆造映射）、{@code agentKindName} 中文名随行
 * （aiplatform#186 已落——辅助标记为 null，前端落「—」桶）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformCostOverviewResponse(

        /** 窗口起点（含；ISO-8601 Instant UTC 带 Z，原样回显） */
        Instant from,

        /** 窗口终点（不含；原样回显） */
        Instant to,

        /** 总量（五档分列，全平台跨项目） */
        AiplatformTokenUsageResponse total,

        /** 平台成本（币种分桶直读不折算，键 = ISO 4217 币种码；全未配价/无事件时空 Map） */
        Map<String, BigDecimal> cost,

        /** 分模型聚合（provider + model 为单价表匹配键） */
        List<ModelUsage> byModel,

        /** 分智能体聚合（agentKind 裸维度串 + agentKindName 随行） */
        List<AgentKindUsage> byAgentKind
) {

    /** 分模型聚合项。 */
    public record ModelUsage(String provider, String model, AiplatformTokenUsageResponse tokens) {
    }

    /** 分智能体聚合项（agentKind 原值 + agentKindName 中文名，辅助标记为 null）。 */
    public record AgentKindUsage(String agentKind, String agentKindName, AiplatformTokenUsageResponse tokens) {
    }
}
