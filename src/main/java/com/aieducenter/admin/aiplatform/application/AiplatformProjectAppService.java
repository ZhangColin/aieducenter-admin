package com.aieducenter.admin.aiplatform.application;

import java.util.List;

import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformProjectQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformConversationEntryResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformOrderBriefResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformPrdResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectFileContentResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectFilesPackageResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectFilesResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectSummaryResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformVersionDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformVersionResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformConversationEntryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderBriefWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformPrdWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectFileContentWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectFilesWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectSummaryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformVersionDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformVersionWireResponse;
import com.aieducenter.admin.aiplatform.infrastructure.AiplatformClient;
import com.cartisan.openapi.client.BinaryResponse;
import com.cartisan.web.response.PageResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

/**
 * aiplatform 项目域 BFF 应用服务——项目读路径聚合（清单四维检索 / 详情带订单引用与成本指针 /
 * 对话史 / PRD / 版本列表与详情 / 文件区三端点），issue #65 + #71。
 *
 * <p>admin 作为 BFF：调接口 + DTO 转换，不持业务逻辑、不持项目数据、不记业务审计（审计归
 * aiplatform）。下游错误已由 {@link AiplatformClient} 统一翻译为 {@link AiplatformUpstreamException}
 * （provider 信封原样透传，spec #62 定稿：aiplatform 不做映射），本层不再 try/catch。载荷类字段
 * （对话史 question/closing/attachments、版本 closing、成本 cost map）JSON 原样透传——BFF 不解读
 * 载荷内部结构（provider 契约的开放对象）。</p>
 *
 * <p>分页（spec #62 平台分页统一决议目标态）：北向请求 page <strong>1-based</strong>，出站直传
 * 零换算；回显取 provider 回报的 page/size 原值（含 provider clamp 后的值），BFF 不重复夹取。</p>
 *
 * @since 0.1.0
 */
@Service
public class AiplatformProjectAppService {

    private final AiplatformClient aiplatformClient;

    public AiplatformProjectAppService(AiplatformClient aiplatformClient) {
        this.aiplatformClient = aiplatformClient;
    }

    /**
     * 项目清单（四维检索，透传 aiplatform）——状态三档单选（1=进行中 3=已归档，缺省＝全部、
     * 归档项目缺省含）/创建时间区间/externalId/projectId 精确，新项目在前（provider 定死 TSID 倒序）。
     */
    public PageResponse<AiplatformProjectSummaryResponse> list(AiplatformProjectQuery query, int page, int size) {
        PageResponse<AiplatformProjectSummaryWireResponse> wirePage = aiplatformClient.listProjects(
                new AiplatformProjectListWireRequest(query.status(), query.createdFrom(), query.createdTo(),
                        query.externalId(), query.projectId()),
                page, size);
        List<AiplatformProjectSummaryResponse> items = wirePage.items().stream()
                .map(AiplatformProjectAppService::toSummary)
                .toList();
        // 回显 provider 的 page/size 原值（1-based，含 clamp 后值）——不是北向入参回声
        return new PageResponse<>(items, wirePage.total(), wirePage.page(), wirePage.size());
    }

    /**
     * 项目详情（透传 aiplatform）——清单字段全量 + 订单引用（activeOrder 未终结/latestOrder 最近）
     * + 成本指针（costSummary：按币种 BigDecimal + unpriced 标记）。
     */
    public AiplatformProjectDetailResponse getDetail(String id) {
        return toDetail(aiplatformClient.getProject(id));
    }

    /**
     * 项目对话史（透传 aiplatform）——对话面全量同序（id 升序＝对话序），kind Integer code +
     * kindName 中文名（provider 出口提供，aiplatform#186）随行。
     */
    public List<AiplatformConversationEntryResponse> getConversation(String id) {
        return aiplatformClient.getConversation(id).stream()
                .map(AiplatformProjectAppService::toConversationEntry)
                .toList();
    }

    /**
     * 项目 PRD（透传 aiplatform）——工作区 docs/PRD.md 直读（v1 无版本链只最新版），
     * markdown 正文 + updatedAt（文件 mtime，秒精度 Instant）。
     */
    public AiplatformPrdResponse getPrd(String id) {
        AiplatformPrdWireResponse wire = aiplatformClient.getPrd(id);
        return new AiplatformPrdResponse(wire.projectId(), wire.content(), wire.updatedAt());
    }

    /**
     * 项目版本列表（透传 aiplatform）——git log 即版本序列，新→旧定死；零版本＝空列表非错误。
     */
    public List<AiplatformVersionResponse> listVersions(String id) {
        return aiplatformClient.listVersions(id).stream()
                .map(AiplatformProjectAppService::toVersion)
                .toList();
    }

    /**
     * 项目版本详情（透传 aiplatform）——版本元数据 + 锚定收尾卡载荷（Run-Id 联接对话史 closing
     * 条目，#88 同载荷）；ref＝commit hash（hex 40 位）。
     */
    public AiplatformVersionDetailResponse getVersion(String id, String ref) {
        return toVersionDetail(aiplatformClient.getVersion(id, ref));
    }

    /**
     * 项目文件树（透传 aiplatform）——交付文件视图＝工作区剔除非交付物后的 [{path, size}] 清单，
     * 按路径稳定排序；只列文件（目录由前端按路径段合成）。未下单项目可浏览（排障不依赖成交）。
     */
    public AiplatformProjectFilesResponse getFiles(String id) {
        AiplatformProjectFilesWireResponse wire = aiplatformClient.getProjectFiles(id);
        return new AiplatformProjectFilesResponse(wire.projectId(), wire.files().stream()
                .map(AiplatformProjectAppService::toFileEntry)
                .toList());
    }

    /**
     * 项目文本文件内容（透传 aiplatform，「点看」）——path 工作区相对路径原样回传，只读策略
     * （机密/逃逸拒、1 MiB 上限、非文本拒 PRJ_020–023）全归 provider 裁决，BFF 不预检不解释。
     */
    public AiplatformProjectFileContentResponse getFileContent(String id, String path) {
        AiplatformProjectFileContentWireResponse wire = aiplatformClient.getProjectFileContent(id, path);
        return new AiplatformProjectFileContentResponse(wire.path(), wire.content());
    }

    /**
     * 下载项目文件包（透传 aiplatform）——tar.gz 二进制流 + provider 响应头 raw 值。
     * sealed/source 两态文件名由 provider 决定（封存包 {id}-archive.tar.gz 整卷口径 /
     * 即时源码包 {id}-source.tar.gz 交付口径），BFF 不判封存态、不重构文件名。
     */
    public AiplatformProjectFilesPackageResponse getFilesPackage(String id) {
        BinaryResponse binary = aiplatformClient.downloadProjectFilesPackage(id);
        // HttpHeaders 大小写不敏感取值；provider 契约恒带两头——缺头兜底为通用二进制附件（HTTP 合法性，
        // 非契约发明）——订单源码包同款
        String contentType = binary.headers().firstValue(HttpHeaders.CONTENT_TYPE)
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        String contentDisposition = binary.headers().firstValue(HttpHeaders.CONTENT_DISPOSITION)
                .orElse("attachment");
        return new AiplatformProjectFilesPackageResponse(binary.body(), contentType, contentDisposition);
    }

    private static AiplatformProjectSummaryResponse toSummary(AiplatformProjectSummaryWireResponse wire) {
        return new AiplatformProjectSummaryResponse(
                wire.id(), wire.name(), wire.ownerDisplayName(),
                wire.type(), wire.typeName(), wire.status(), wire.statusName(), wire.archived(),
                wire.createdAt(), wire.updatedAt());
    }

    private static AiplatformProjectDetailResponse toDetail(AiplatformProjectDetailWireResponse wire) {
        return new AiplatformProjectDetailResponse(
                wire.id(), wire.name(), wire.ownerDisplayName(), wire.workspaceId(),
                wire.type(), wire.typeName(), wire.status(), wire.statusName(), wire.archived(),
                wire.createdAt(), wire.updatedAt(), wire.prdProducedAt(), wire.generatedAt(),
                wire.activeOrder() == null ? null : toOrderBrief(wire.activeOrder()),
                wire.latestOrder() == null ? null : toOrderBrief(wire.latestOrder()),
                wire.costSummary() == null ? null : new AiplatformProjectDetailResponse.CostSummary(
                        wire.costSummary().cost(), wire.costSummary().unpriced()));
    }

    private static AiplatformOrderBriefResponse toOrderBrief(AiplatformOrderBriefWireResponse wire) {
        return new AiplatformOrderBriefResponse(wire.id(), wire.status(), wire.statusName());
    }

    private static AiplatformConversationEntryResponse toConversationEntry(
            AiplatformConversationEntryWireResponse wire) {
        return new AiplatformConversationEntryResponse(
                wire.id(), wire.kind(), wire.kindName(), wire.runId(), wire.text(),
                wire.question(), wire.closing(), wire.attachments(), wire.answered(), wire.at());
    }

    private static AiplatformVersionResponse toVersion(AiplatformVersionWireResponse wire) {
        return new AiplatformVersionResponse(
                wire.commitHash(), wire.subject(), wire.runId(), wire.rollbackFrom(), wire.committedAt());
    }

    private static AiplatformVersionDetailResponse toVersionDetail(AiplatformVersionDetailWireResponse wire) {
        return new AiplatformVersionDetailResponse(
                wire.commitHash(), wire.subject(), wire.runId(), wire.rollbackFrom(),
                wire.committedAt(), wire.closing());
    }

    private static AiplatformProjectFilesResponse.FileEntry toFileEntry(
            AiplatformProjectFilesWireResponse.FileEntry wire) {
        return new AiplatformProjectFilesResponse.FileEntry(wire.path(), wire.size());
    }
}
