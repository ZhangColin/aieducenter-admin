package com.aieducenter.admin.aiplatform.application;

import java.util.List;

import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformCostQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformCostOverviewResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectCostDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectCostResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformTokenUsageResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformUnpricedUsageResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformCostOverviewWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformCostWindowWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectCostDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectCostWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformTokenUsageWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformUnpricedUsageWireResponse;
import com.aieducenter.admin.aiplatform.infrastructure.AiplatformClient;
import com.cartisan.web.response.PageResponse;
import org.springframework.stereotype.Service;

/**
 * aiplatform 成本域 BFF 应用服务——纯平台成本观测四读口（全局总览 / unpriced 全局警示 /
 * 项目成本清单 / 单项目下钻），issue #67。
 *
 * <p>admin 作为 BFF：调接口 + DTO 转换，不持业务逻辑、不持成本数据（聚合归 aiplatform 计量上下文
 * 现算）。下游错误已由 {@link AiplatformClient} 统一翻译为 {@link AiplatformUpstreamException}
 * （provider 信封原样透传，spec #62 定稿），本层不再 try/catch。四端点均为读口——无操作者透传。</p>
 *
 * <p>时间窗（issue #67）：北向 {@code from}/{@code to} 必填（半开 [from, to)、ISO-8601 Instant UTC），
 * BFF 不设默认窗口——出站恒带双参逐字透传。分页（平台分页统一决议目标态）：北向请求 page
 * <strong>1-based</strong>，出站直传零换算；回显取 provider 回报的 page/size 原值（含 clamp 后的值），
 * BFF 不重复夹取。</p>
 *
 * @since 0.1.0
 */
@Service
public class AiplatformCostAppService {

    private final AiplatformClient aiplatformClient;

    public AiplatformCostAppService(AiplatformClient aiplatformClient) {
        this.aiplatformClient = aiplatformClient;
    }

    /**
     * 平台成本全局总览（透传 aiplatform）——全平台跨项目观测模型开销构成：五档总量 + 平台成本
     * （币种分桶直读不折算）+ 分模型 + 分智能体（agentKind 裸维度串 + agentKindName 随行）。
     * 空窗/无数据返回全零 total 与空分桶，不是错误。
     */
    public AiplatformCostOverviewResponse overview(AiplatformCostQuery query) {
        return toOverview(aiplatformClient.getCostOverview(toWindow(query)));
    }

    /**
     * unpriced 全局警示（透传 aiplatform，用量驱动）——窗口内有用量且时点无生效单价的
     * (provider, model, 档位) 按档位汇总 token（只计无价分量）；据此发现漏配价及时补价
     * （历史成本不漂移）。
     */
    public AiplatformUnpricedUsageResponse unpriced(AiplatformCostQuery query) {
        return toUnpriced(aiplatformClient.getUnpricedUsage(toWindow(query)));
    }

    /**
     * 项目成本清单（透传 aiplatform，成本降序分页）——运营扫一眼谁费钱：窗口内有 token 用量的
     * 各项目成本汇总；全未配价项目排后且 allUnpriced=true 标注；已删项目的历史花费照列。
     */
    public PageResponse<AiplatformProjectCostResponse> listProjectCosts(AiplatformCostQuery query,
                                                                        int page, int size) {
        PageResponse<AiplatformProjectCostWireResponse> wirePage = aiplatformClient.listProjectCosts(
                toWindow(query), page, size);
        List<AiplatformProjectCostResponse> items = wirePage.items().stream()
                .map(AiplatformCostAppService::toProjectCost)
                .toList();
        // 回显 provider 的 page/size 原值（1-based，含 clamp 后值）——不是北向入参回声
        return new PageResponse<>(items, wirePage.total(), wirePage.page(), wirePage.size());
    }

    /**
     * 单项目成本下钻（透传 aiplatform）——byModel/byAgentKind 分解 + 未配价档位（与 cost 互补
     * 不重叠）；projectId 为计量 subject 原值（无用量/查无此号＝全零空态，非 404）。
     */
    public AiplatformProjectCostDetailResponse getProjectCostDetail(String projectId,
                                                                    AiplatformCostQuery query) {
        return toProjectCostDetail(aiplatformClient.getProjectCostDetail(projectId, toWindow(query)));
    }

    private static AiplatformCostWindowWireRequest toWindow(AiplatformCostQuery query) {
        return new AiplatformCostWindowWireRequest(query.from(), query.to());
    }

    private static AiplatformCostOverviewResponse toOverview(AiplatformCostOverviewWireResponse wire) {
        return new AiplatformCostOverviewResponse(
                wire.from(), wire.to(), toTokens(wire.total()), wire.cost(),
                wire.byModel() == null ? null : wire.byModel().stream()
                        .map(AiplatformCostAppService::toModelUsage).toList(),
                wire.byAgentKind() == null ? null : wire.byAgentKind().stream()
                        .map(AiplatformCostAppService::toAgentKindUsage).toList());
    }

    private static AiplatformUnpricedUsageResponse toUnpriced(AiplatformUnpricedUsageWireResponse wire) {
        return new AiplatformUnpricedUsageResponse(
                wire.from(), wire.to(),
                wire.items() == null ? null : wire.items().stream()
                        .map(AiplatformCostAppService::toUnpricedTier).toList());
    }

    private static AiplatformProjectCostResponse toProjectCost(AiplatformProjectCostWireResponse wire) {
        return new AiplatformProjectCostResponse(
                wire.projectId(), toTokens(wire.total()), wire.cost(), wire.allUnpriced());
    }

    private static AiplatformProjectCostDetailResponse toProjectCostDetail(
            AiplatformProjectCostDetailWireResponse wire) {
        return new AiplatformProjectCostDetailResponse(
                wire.projectId(), wire.from(), wire.to(), toTokens(wire.total()), wire.cost(),
                wire.unpriced() == null ? null : wire.unpriced().stream()
                        .map(AiplatformCostAppService::toDetailTier).toList(),
                wire.byModel() == null ? null : wire.byModel().stream()
                        .map(AiplatformCostAppService::toDetailModelUsage).toList(),
                wire.byAgentKind() == null ? null : wire.byAgentKind().stream()
                        .map(AiplatformCostAppService::toDetailAgentKindUsage).toList());
    }

    private static AiplatformTokenUsageResponse toTokens(AiplatformTokenUsageWireResponse wire) {
        return new AiplatformTokenUsageResponse(
                wire.input(), wire.output(), wire.cacheRead(), wire.cacheWrite(), wire.reasoning());
    }

    private static AiplatformCostOverviewResponse.ModelUsage toModelUsage(
            AiplatformCostOverviewWireResponse.ModelUsage wire) {
        return new AiplatformCostOverviewResponse.ModelUsage(
                wire.provider(), wire.model(), toTokens(wire.tokens()));
    }

    private static AiplatformCostOverviewResponse.AgentKindUsage toAgentKindUsage(
            AiplatformCostOverviewWireResponse.AgentKindUsage wire) {
        return new AiplatformCostOverviewResponse.AgentKindUsage(
                wire.agentKind(), wire.agentKindName(), toTokens(wire.tokens()));
    }

    private static AiplatformUnpricedUsageResponse.UnpricedTier toUnpricedTier(
            AiplatformUnpricedUsageWireResponse.UnpricedTier wire) {
        return new AiplatformUnpricedUsageResponse.UnpricedTier(
                wire.provider(), wire.model(), wire.tokenKind(), wire.tokenKindName(), wire.tokens());
    }

    private static AiplatformProjectCostDetailResponse.UnpricedTier toDetailTier(
            AiplatformProjectCostDetailWireResponse.UnpricedTier wire) {
        return new AiplatformProjectCostDetailResponse.UnpricedTier(
                wire.provider(), wire.model(), wire.tokenKind(), wire.tokenKindName());
    }

    private static AiplatformProjectCostDetailResponse.ModelUsage toDetailModelUsage(
            AiplatformProjectCostDetailWireResponse.ModelUsage wire) {
        return new AiplatformProjectCostDetailResponse.ModelUsage(
                wire.provider(), wire.model(), toTokens(wire.tokens()));
    }

    private static AiplatformProjectCostDetailResponse.AgentKindUsage toDetailAgentKindUsage(
            AiplatformProjectCostDetailWireResponse.AgentKindUsage wire) {
        return new AiplatformProjectCostDetailResponse.AgentKindUsage(
                wire.agentKind(), wire.agentKindName(), toTokens(wire.tokens()));
    }
}
