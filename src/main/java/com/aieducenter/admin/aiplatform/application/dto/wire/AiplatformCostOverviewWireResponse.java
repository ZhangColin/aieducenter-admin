package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * aiplatform 平台成本全局总览的 wire 镜像——与 aiplatform
 * {@code BackofficeCostOverviewResponse}（#161 成本运营）字段同构：窗口回显 + 五档总量 +
 * 平台成本（币种分桶）+ 分模型/分智能体两分解。
 *
 * <p>纯平台 token 成本观测——与报价脱钩（无建议售价推导）、按币种分桶直读不折算（键 =
 * ISO 4217 币种码）。无生效单价的分量不进 {@code cost}（不伪装 0），未配价观测走 unpriced 端点；
 * 窗口内无事件时 total 全零、各分桶为空，不是错误。</p>
 *
 * @param from        窗口起点（含；原样回显）
 * @param to          窗口终点（不含；原样回显）
 * @param total       总量（五档分列，全平台跨项目）
 * @param cost        平台成本（币种分桶；全未配价/无事件时为空 Map）
 * @param byModel     分模型聚合（provider + model 为单价表匹配键）
 * @param byAgentKind 分智能体聚合（dims.agentKind 原值 + agentKindName 随行——aiplatform#186 已落）
 */
public record AiplatformCostOverviewWireResponse(
        Instant from,
        Instant to,
        AiplatformTokenUsageWireResponse total,
        Map<String, BigDecimal> cost,
        List<ModelUsage> byModel,
        List<AgentKindUsage> byAgentKind
) {

    /**
     * 分模型聚合项（provider + model 为单价表匹配键）。
     */
    public record ModelUsage(String provider, String model, AiplatformTokenUsageWireResponse tokens) {
    }

    /**
     * 分智能体聚合项（agentKind = dims 透传原值，写侧终态口径 main/executor，非主链用途标记照原样；
     * agentKindName 中文名随行——#186 经 {@code AgentKindNames} 端口回解正本 AgentProfile，
     * naming/classify 等辅助标记为 null，消费端落「—」桶）。
     */
    public record AgentKindUsage(String agentKind, String agentKindName, AiplatformTokenUsageWireResponse tokens) {
    }
}
