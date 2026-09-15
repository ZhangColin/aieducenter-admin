package com.aieducenter.admin.aiplatform.application;

import java.util.List;

import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformWorkspaceQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformWorkspaceDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformWorkspaceSummaryResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformWorkspaceDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformWorkspaceListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformWorkspaceSummaryWireResponse;
import com.aieducenter.admin.aiplatform.infrastructure.AiplatformClient;
import com.cartisan.web.response.PageResponse;
import org.springframework.stereotype.Service;

/**
 * aiplatform 沙箱域 BFF 应用服务——观测面（清单两维过滤/详情全量）＋四干预动作（唤醒/强制休眠/
 * 强制重建/封存），issue #66。
 *
 * <p>admin 作为 BFF：调接口 + DTO 转换，不持业务逻辑、不持沙箱数据、不记业务审计（操作留痕归
 * aiplatform 动作行）。下游错误已由 {@link AiplatformClient} 统一翻译为 {@link AiplatformUpstreamException}
 * （provider 信封原样透传，spec #62 定稿：aiplatform 不做映射），本层不再 try/catch。</p>
 *
 * <p>四干预动作的回执＝详情 DTO（动作后的观测详情、新事实），与详情读同一映射；操作者身份经框架
 * {@code RequestContext}→{@code X-User-Id/X-User-Name} 自动透传（identity 同款，AppService 不经手）。</p>
 *
 * <p>分页（spec #62 平台分页统一决议目标态）：北向请求 page <strong>1-based</strong>，出站直传
 * 零换算；回显取 provider 回报的 page/size 原值（含 provider clamp 后的值），BFF 不重复夹取。</p>
 *
 * @since 0.1.0
 */
@Service
public class AiplatformWorkspaceAppService {

    private final AiplatformClient aiplatformClient;

    public AiplatformWorkspaceAppService(AiplatformClient aiplatformClient) {
        this.aiplatformClient = aiplatformClient;
    }

    /**
     * 沙箱清单（两维过滤，透传 aiplatform）——期望态/实态分示发现漂移：desired（1=运行 2=休眠
     * 3=封存）/actual（1=运行中 2=已停止 3=无容器 4=未知）均单选、可组合、可缺省；新沙箱在前
     * （provider 定死 TSID 倒序）。实态与卷大小逐行现场探查（docker 子进程）——观测页不必贪大。
     */
    public PageResponse<AiplatformWorkspaceSummaryResponse> list(AiplatformWorkspaceQuery query, int page, int size) {
        PageResponse<AiplatformWorkspaceSummaryWireResponse> wirePage = aiplatformClient.listWorkspaces(
                new AiplatformWorkspaceListWireRequest(query.desired(), query.actual()),
                page, size);
        List<AiplatformWorkspaceSummaryResponse> items = wirePage.items().stream()
                .map(AiplatformWorkspaceAppService::toSummary)
                .toList();
        // 回显 provider 的 page/size 原值（1-based，含 clamp 后值）——不是北向入参回声
        return new PageResponse<>(items, wirePage.total(), wirePage.page(), wirePage.size());
    }

    /**
     * 沙箱详情（透传 aiplatform）——清单行超集：全量字段＋所属项目引用＋中间件资源清单
     * （连接串原文，容器内回环形态）＋置备失败原因/封存包寻址键/审计列。
     */
    public AiplatformWorkspaceDetailResponse getDetail(String id) {
        return toDetail(aiplatformClient.getWorkspace(id));
    }

    /**
     * 唤醒（透传 aiplatform）——收敛到 READY＋（已生成项目）应用在服，同步等结果；容器缺失/被杀
     * 走幂等重建（卷保留）；封存态走深度唤醒（解包回卷＋依赖重装，分钟级）。回执＝动作后的观测详情。
     */
    public AiplatformWorkspaceDetailResponse wake(String id) {
        return toDetail(aiplatformClient.wakeWorkspace(id));
    }

    /**
     * 强制休眠（透传 aiplatform）——管理员的即时止损口：删容器保卷、期望态置休眠；已休眠＝幂等成功。
     * 封存态拒 400 WSP_009、run 在途拒 409 WSP_015、收敛任务在途 409 WSP_017——原样透传。
     */
    public AiplatformWorkspaceDetailResponse hibernate(String id) {
        return toDetail(aiplatformClient.hibernateWorkspace(id));
    }

    /**
     * 强制重建（透传 aiplatform）——「预览死了」型漂移的标准化处置：在跑但坏了的容器也杀（卷保留），
     * 走唤醒内核同一重建路径收敛回 READY。封存态拒 400 WSP_009（先唤醒）、重建重试上限落 FAILED
     * 时 500 WSP_010（可再触发）——原样透传。
     */
    public AiplatformWorkspaceDetailResponse rebuild(String id) {
        return toDetail(aiplatformClient.rebuildWorkspace(id));
    }

    /**
     * 封存（透传 aiplatform）——动作序同闲置满期的自动封存：删容器→整卷打包落平台存储→期望态置
     * 封存＋包元数据→删卷；RUNNING 起点可用（即时深回收）。重复封存拒 400 WSP_009——原样透传。
     */
    public AiplatformWorkspaceDetailResponse seal(String id) {
        return toDetail(aiplatformClient.sealWorkspace(id));
    }

    private static AiplatformWorkspaceSummaryResponse toSummary(AiplatformWorkspaceSummaryWireResponse wire) {
        return new AiplatformWorkspaceSummaryResponse(
                wire.workspaceId(), wire.containerName(),
                wire.kind(), wire.kindName(), wire.status(), wire.statusName(),
                wire.desiredState(), wire.desiredStateName(),
                wire.containerState(), wire.containerStateName(),
                wire.lastTouchAt(), wire.volumeSizeBytes(), wire.sealedAt(), wire.archiveSizeBytes(),
                wire.project() == null ? null : toProjectRef(wire.project()));
    }

    private static AiplatformWorkspaceDetailResponse toDetail(AiplatformWorkspaceDetailWireResponse wire) {
        return new AiplatformWorkspaceDetailResponse(
                wire.workspaceId(), wire.containerName(), wire.networkName(),
                wire.kind(), wire.kindName(), wire.status(), wire.statusName(), wire.provisionError(),
                wire.desiredState(), wire.desiredStateName(),
                wire.containerState(), wire.containerStateName(),
                wire.lastTouchAt(), wire.volumeSizeBytes(), wire.sealedAt(),
                wire.archivePath(), wire.archiveSizeBytes(), wire.createdAt(), wire.updatedAt(),
                wire.resources() == null ? null : wire.resources().stream()
                        .map(AiplatformWorkspaceAppService::toMiddlewareResource)
                        .toList(),
                wire.project() == null ? null : toProjectRef(wire.project()));
    }

    private static AiplatformWorkspaceSummaryResponse.ProjectRef toProjectRef(
            AiplatformWorkspaceSummaryWireResponse.ProjectRef wire) {
        return new AiplatformWorkspaceSummaryResponse.ProjectRef(
                wire.projectId(), wire.name(), wire.archived());
    }

    private static AiplatformWorkspaceDetailResponse.MiddlewareResourceObservation toMiddlewareResource(
            AiplatformWorkspaceDetailWireResponse.MiddlewareResourceObservation wire) {
        return new AiplatformWorkspaceDetailResponse.MiddlewareResourceObservation(
                wire.kind(), wire.containerName(), wire.internalUrl());
    }
}
