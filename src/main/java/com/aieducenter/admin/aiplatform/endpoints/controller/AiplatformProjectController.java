package com.aieducenter.admin.aiplatform.endpoints.controller;

import java.time.LocalDateTime;
import java.util.List;

import com.aieducenter.admin.aiplatform.application.AiplatformProjectAppService;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformProjectQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformConversationEntryResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformPrdResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectSummaryResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformVersionDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformVersionResponse;
import com.aieducenter.admin.constants.AdminScopes;
import com.cartisan.security.annotation.RequireAuth;
import com.cartisan.security.annotation.RequirePermission;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * AI 平台项目控制器——BFF 核心读路径（清单/详情/对话史/PRD/版本），issue #65。
 *
 * <p>北向 {@code /api/admin/aiplatform/projects/**} 逐字镜像 aiplatform
 * {@code /api/backoffice/projects/**}（#159 清单+详情 / #162 深读三组）——查询参数名、缺省值、
 * 分页基准（page 1-based，缺省 1/20）与 provider controller 同形。错误经
 * {@code AiplatformUpstreamErrorAdvice} 原样透传（如 404 PRJ_001→4001、400 PRJ_014→4014、
 * 404 PRJ_015→4015、404 PRJ_028→4028、500 WSP_002→1002）。</p>
 *
 * <p>归档项目全读面照读（provider 工作区保留）；已删项目不可见（provider 真删无墓碑）——语义
 * 透传，BFF 不二次过滤。对话史/PRD/版本归 {@code project:read} 同一权限码（spec #62：深读
 * 三组是项目全貌的组成面，不设独立码）。项目文件区（files 三端点）是 T5（issue #71），不在本控制器。</p>
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/admin/aiplatform/projects")
@Validated
@Tag(name = "Admin Aiplatform Project", description = "AI 平台 · 项目")
public class AiplatformProjectController {

    private final AiplatformProjectAppService projectAppService;

    public AiplatformProjectController(AiplatformProjectAppService projectAppService) {
        this.projectAppService = projectAppService;
    }

    // 不挂 @ErrorCodes：框架 ErrorCodesValidator 要求码在应用自身 CodeMessageRegistry 注册，admin 不镜像
    // provider 错误码目录（忠实透传、零加戏——spec #62）；错误码以 description 文档化（PRJ_014 等）。
    @GetMapping
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:project:read",
            name = "AI 平台 / 项目查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "项目清单（四维检索，分页 1-based）——透传 aiplatform",
            description = "监管工作清单：新项目在前（TSID 倒序，provider 定死）。四维可组合、均可缺省"
                    + "（缺省＝全量）：① status 状态三档<strong>单选</strong>，Integer code（1=进行中 "
                    + "3=已归档；缺省＝全部，归档项目缺省含——照用户面状态过滤先例，与订单清单状态"
                    + "多选有意不同）；② createdFrom/createdTo 创建时间区间（ISO-8601，含两端，如 "
                    + "2026-09-01T00:00:00）；③ externalId 归属账号（provider 换算，换算不到＝空清单"
                    + " 200）；④ projectId 项目 id 精确（TSID 十进制，用户报障贴链接场景；查无/非数值"
                    + "→空清单 200）。行带 ownerDisplayName（归属账号缺档/无主为 null）。不做项目名"
                    + "模糊。page 1 基（缺省 1）、size 缺省 20（provider 上界 100），clamp 由 provider "
                    + "执行、回显 provider 回报值。已删项目不可见（provider 真删无墓碑，BFF 不二次过滤）。"
                    + "绑定裁决两段：非整数 code/非法时间在本服务绑定层 400（框架信封）；数值但未知的 "
                    + "code（如 2——码位注销、或 99）由 provider 裁决 400 PRJ_014（数字业务码 4014）透传。")
    public ApiResponse<PageResponse<AiplatformProjectSummaryResponse>> projects(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(required = false) String externalId,
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(projectAppService.list(
                new AiplatformProjectQuery(status, createdFrom, createdTo, externalId, projectId), page, size));
    }

    @GetMapping("/{id}")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:project:read",
            name = "AI 平台 / 项目查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "项目详情（带订单引用＋成本指针）——透传 aiplatform",
            description = "项目全貌一屏：清单字段全量＋归属账号显示名＋workspaceId（工作区互查锚点）＋"
                    + "订单引用（activeOrder＝未终结订单摘要，1=待报价 2=已报价，有值即冻结迭代；"
                    + "latestOrder＝最近一张任意状态订单，支付归档后承接完整记录取单面；从未下单两者"
                    + "皆空）＋成本指针（costSummary：总成本按币种分桶直读不折算（键=ISO 4217 币种码、"
                    + "值 BigDecimal）+ unpriced 标记——true 时成本不完整；无用量＝空 cost + false "
                    + "明确空态；明细下钻走成本域端点）。type/status 配 *Name。归档项目照读；"
                    + "项目不存在（含已删）404 PRJ_001（数字业务码 4001）。")
    public ApiResponse<AiplatformProjectDetailResponse> detail(@PathVariable String id) {
        return ApiResponse.ok(projectAppService.getDetail(id));
    }

    @GetMapping("/{id}/conversation")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:project:read",
            name = "AI 平台 / 项目查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "项目对话史（全量同序）——透传 aiplatform",
            description = "还原用户与系统的交互过程：用户发言/智能体回复/问答卡/问答作答/收尾卡/"
                    + "平台轻引导，按写入序（id 升序＝对话序）全量返回；过程明细（解说段/动作卡流水）"
                    + "不在其中。kind Integer code（1=用户发言 2=智能体回复 3=问答卡 4=问答作答 "
                    + "5=收尾卡 6=平台轻引导）+ kindName 中文名随行（aiplatform#186 已落——provider "
                    + "出口提供，BFF 透传）。question＝question-raised 事件载荷原样（answered=false "
                    + "即挂起待答）；closing＝run-finish 收口扩载同载荷（版本详情锚定的权威事实）；"
                    + "attachments＝圈注附件数组——载荷 JSON 原样透传。归档项目照读（对话区只读终态）。"
                    + "项目不存在 404 PRJ_001（数字业务码 4001）。")
    public ApiResponse<List<AiplatformConversationEntryResponse>> conversation(@PathVariable String id) {
        return ApiResponse.ok(projectAppService.getConversation(id));
    }

    @GetMapping("/{id}/prd")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:project:read",
            name = "AI 平台 / 项目查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "项目 PRD（工作区直读，只最新版）——透传 aiplatform",
            description = "了解交付物内容：直读项目 dev 工作区 docs/PRD.md（事实源，v1 无版本链只"
                    + "最新版），返回 markdown 正文 + updatedAt（文件 mtime，ISO-8601 秒精度）。"
                    + "未产出（工作区无该文件）404 PRJ_015（数字业务码 4015，与项目不存在 PRJ_001 "
                    + "的 4001 区分）。归档项目照读（工作区保留）；环境故障 500 WSP_002（1002）。")
    public ApiResponse<AiplatformPrdResponse> prd(@PathVariable String id) {
        return ApiResponse.ok(projectAppService.getPrd(id));
    }

    @GetMapping("/{id}/versions")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:project:read",
            name = "AI 平台 / 项目查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "项目版本列表（新→旧）——透传 aiplatform",
            description = "「上周五还好好的」按版本锚点回看的入口：git log 即版本序列（正本＝容器内 "
                    + "git log 直读，无库表）——每轮编码 run 收口自动成版（commit 主题＝收口摘要、"
                    + "Run-Id trailer 锚定收尾卡）；回滚版本 runId 空、rollbackFrom 锚定源版本。排序"
                    + "新→旧定死；零版本（尚无收口）＝空列表非错误。归档项目照读。项目不存在 404 "
                    + "PRJ_001（数字业务码 4001）；环境故障 500 WSP_002（1002）。")
    public ApiResponse<List<AiplatformVersionResponse>> versions(@PathVariable String id) {
        return ApiResponse.ok(projectAppService.listVersions(id));
    }

    @GetMapping("/{id}/versions/{ref}")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:project:read",
            name = "AI 平台 / 项目查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "项目版本详情（锚定收尾卡）——透传 aiplatform",
            description = "版本元数据（hash/摘要/锚定 run/成版时刻）＋收尾卡载荷（Run-Id 联接对话史 "
                    + "closing 条目，同载荷复用；收尾卡缺位时 closing 为 null）＋rollbackFrom（回滚"
                    + "版本锚定源版本、runId 空；run 版本反之）。ref＝commit hash（hex 40 位）——"
                    + "非 hash 形态 404 PRJ_028（数字业务码 4028，provider 裁决且不触工作区）。"
                    + "项目不存在 404 PRJ_001（4001）；环境故障 500 WSP_002（1002）。")
    public ApiResponse<AiplatformVersionDetailResponse> versionDetail(
            @PathVariable String id, @PathVariable String ref) {
        return ApiResponse.ok(projectAppService.getVersion(id, ref));
    }
}
