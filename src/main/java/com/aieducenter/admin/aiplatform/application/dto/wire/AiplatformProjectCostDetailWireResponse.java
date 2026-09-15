package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * aiplatform 单项目成本下钻的 wire 镜像——与 aiplatform {@code BackofficeProjectCostDetailResponse}
 * （#164 成本运营）字段同构：subject 回显 + 窗口回显 + 五档总量 + 成本分桶 + 未配价档位 +
 * 分模型/分智能体两分解。
 *
 * <p>成本换算同全局总览：token × 事件时点生效单价现算（历史成本不随改价漂移）；无生效单价的分量
 * 不进 {@code cost}（不伪装 0），其 (provider, model, 档位) 集合在 {@code unpriced} 如实呈现
 * （与 cost 互补不重叠）。{@code projectId} 不透明：无用量/查无此号返回全零 total 与空结构，
 * 不是错误（明确空态、不 404）。</p>
 *
 * @param projectId   项目标识（计量 subject 原值回显）
 * @param from        窗口起点（含；原样回显）
 * @param to          窗口终点（不含；原样回显）
 * @param total       总量（五档分列）
 * @param cost        平台成本（币种分桶；全未配价/无事件时为空 Map）
 * @param unpriced    未配价档位清单（provider/model/档位码序；与 cost 互补不重叠）
 * @param byModel     分模型聚合（provider + model 为单价表匹配键）
 * @param byAgentKind 分智能体聚合（dims.agentKind 原值 + agentKindName 随行）
 */
public record AiplatformProjectCostDetailWireResponse(
        String projectId,
        Instant from,
        Instant to,
        AiplatformTokenUsageWireResponse total,
        Map<String, BigDecimal> cost,
        List<UnpricedTier> unpriced,
        List<ModelUsage> byModel,
        List<AgentKindUsage> byAgentKind
) {

    /**
     * 未配价标注项（tokenKind 为 Integer code + tokenKindName 随附，1=输入 2=输出 3=缓存读
     * 4=缓存写 5=推理；bySubject 口径无 token 计数——档位用量汇总走全局 unpriced 端点）。
     */
    public record UnpricedTier(
            String provider,
            String model,
            Integer tokenKind,
            String tokenKindName
    ) {
    }

    /**
     * 分模型聚合项（provider + model 为单价表匹配键）。
     */
    public record ModelUsage(String provider, String model, AiplatformTokenUsageWireResponse tokens) {
    }

    /**
     * 分智能体聚合项（agentKind = dims 透传原值 + agentKindName 中文名随行——#186 回解口径，
     * 辅助标记为 null）。
     */
    public record AgentKindUsage(String agentKind, String agentKindName, AiplatformTokenUsageWireResponse tokens) {
    }
}
