package com.aieducenter.admin.aiplatform.endpoints.controller;

import java.time.Instant;

import com.aieducenter.admin.aiplatform.application.AiplatformMaterialAppService;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformMaterialQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformMaterialDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformMaterialSummaryResponse;
import com.aieducenter.admin.constants.AdminScopes;
import com.cartisan.security.annotation.RequireAuth;
import com.cartisan.security.annotation.RequirePermission;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * AI 平台知识素材控制器——BFF 治理四件套（清单/详情/停用⇄启用/删除），issue #69。
 *
 * <p>北向 {@code /api/admin/aiplatform/materials/**} 逐字镜像 aiplatform
 * {@code /api/backoffice/materials/**}（#166 知识库管理）——查询参数名、缺省值、分页基准
 * （page 1-based，缺省 1/20）与 provider controller 同形。管理单元＝素材＝项目 × 素材类型
 * （非块）；内容面零写，治理手段＝停用/删除。错误经 {@code AiplatformUpstreamErrorAdvice}
 * 原样透传（如 404 KNW_005→2005、400 KNW_006→2006、400 KNW_007→2007）。</p>
 *
 * <p>权限码（spec #62）：读 {@code material:read} 一码；写操作各自独立成码
 * （{@code material:disable|enable|delete}）——最小授权（如只给内容审核员配停用/启用而不给
 * 不可逆删除）。操作者身份由框架 OpenApiClient 自动透传 {@code X-User-Id/X-User-Name}
 * 出站头（identity 同款），provider 落痕素材级（最近管理动作操作者）——知识治理动作必留痕，
 * 缺头会被 provider 以 400 KNW_006 拦截（与单价表缺头落空有意不同）；北向无操作者字段，
 * 前端无法伪造留痕。删除经框架 {@code OpenApiClient.delete}（cartisan-boot#33）出站。</p>
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/admin/aiplatform/materials")
@Validated
@Tag(name = "Admin Aiplatform Material", description = "AI 平台 · 知识素材")
public class AiplatformMaterialController {

    private final AiplatformMaterialAppService materialAppService;

    public AiplatformMaterialController(AiplatformMaterialAppService materialAppService) {
        this.materialAppService = materialAppService;
    }

    // 不挂 @ErrorCodes：框架 ErrorCodesValidator 要求码在应用自身 CodeMessageRegistry 注册，admin 不镜像
    // provider 错误码目录（忠实透传、零加戏——spec #62）；错误码以 description 文档化（KNW_007 等）。
    @GetMapping
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:material:read",
            name = "AI 平台 / 知识素材查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "知识素材清单（三维过滤，分页 1-based）——透传 aiplatform",
            description = "治理工作清单：新沉淀在前（沉淀时间倒序，provider 定死，id 倒序稳定）。"
                    + "三维度可组合、均可缺省（缺省＝全量）：① status 状态<strong>单选</strong>，"
                    + "Integer code（1=启用 2=停用；缺省＝全部——与订单清单状态多选有意不同）；"
                    + "② sunkFrom/sunkTo 沉淀时间区间（<strong>首沉淀时间</strong>，闭区间含两端，"
                    + "ISO-8601 Instant UTC 带 Z，如 2026-09-01T00:00:00Z）；③ projectId 来源项目"
                    + " id 精确（登记面字符串，查无＝空清单 200）。不做内容模糊与账号维度。行带"
                    + "最近管理动作操作者（未治理过为 null）。page 1 基（缺省 1）、size 缺省 20"
                    + "（provider 上界 100），clamp 由 provider 执行、回显 provider 回报值。绑定"
                    + "裁决两段：非整数 status/非 Instant 时间/非整数分页值在本服务绑定层 404"
                    + "（框架类型不匹配口径，不到 provider）；数值但未知的 status code 由 provider"
                    + " 裁决 400 KNW_007（数字业务码 2007）透传。")
    public ApiResponse<PageResponse<AiplatformMaterialSummaryResponse>> materials(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Instant sunkFrom,
            @RequestParam(required = false) Instant sunkTo,
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(materialAppService.list(
                new AiplatformMaterialQuery(status, sunkFrom, sunkTo, projectId), page, size));
    }

    @GetMapping("/{id}")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:material:read",
            name = "AI 平台 / 知识素材查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "知识素材详情（元数据＋PRD 全文）——透传 aiplatform",
            description = "读内容判治理：元数据（素材类型/来源项目引用/沉淀时间（首沉淀，重沉淀与"
                    + "治理动作不改）/状态/最近管理动作操作者）＋素材全文 content＝块按 seq 以空行"
                    + "拼接（段落级重组：超长单段硬切的切点呈现为段落断，内容无损）。来源项目引用"
                    + "容缺直读登记面（不校验项目存在，缺档不炸）。素材不存在（含畸形 id、重复"
                    + "删除后的素材）404 KNW_005（数字业务码 2005）。")
    public ApiResponse<AiplatformMaterialDetailResponse> detail(@PathVariable String id) {
        return ApiResponse.ok(materialAppService.getDetail(id));
    }

    @PostMapping("/{id}/disable")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:material:disable",
            name = "AI 平台 / 知识素材停用",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "停用素材（可逆开关）——透传 aiplatform",
            description = "素材全部块退出生成命中（检索状态过滤机制面恒开），重沉淀不复活（登记"
                    + "状态跨幂等替换存活）；拿不准的内容先摘除、误伤可经 enable 恢复。无请求体。"
                    + "回执＝summary（provider 重读登记行的最新状态与操作者）。重复停用幂等"
                    + "（操作者留最近一次）。X-User-Id/X-User-Name 透传头自动落痕素材级——"
                    + "<strong>缺头 400 KNW_006</strong>（数字业务码 2006，知识治理动作必留痕，"
                    + "与单价表缺头落空有意不同）；素材不存在（含畸形 id）404 KNW_005（2005）。")
    public ApiResponse<AiplatformMaterialSummaryResponse> disable(@PathVariable String id) {
        return ApiResponse.ok(materialAppService.disable(id));
    }

    @PostMapping("/{id}/enable")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:material:enable",
            name = "AI 平台 / 知识素材启用",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "启用素材（恢复命中）——透传 aiplatform",
            description = "停用的可逆侧：素材全部块恢复参与生成命中。无请求体。回执＝summary"
                    + "（provider 重读登记行的最新状态与操作者）。重复启用幂等。X-User-Id/"
                    + "X-User-Name 透传头自动落痕素材级（缺头 400 KNW_006＝2006，口径同停用）；"
                    + "素材不存在（含畸形 id）404 KNW_005（2005）。")
    public ApiResponse<AiplatformMaterialSummaryResponse> enable(@PathVariable String id) {
        return ApiResponse.ok(materialAppService.enable(id));
    }

    @DeleteMapping("/{id}")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:material:delete",
            name = "AI 平台 / 知识素材删除",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "删除素材（治理移除，不可逆）——透传 aiplatform",
            description = "治理移除素材登记行与全部块、<strong>不动来源项目</strong>（管理删除与"
                    + "项目删除级联正交）；此后清单/详情/检索均不可见。无请求体、无行可留、不留痕。"
                    + "回执＝<strong>删除前终态</strong> summary（provider 契约如此，逐字镜像——"
                    + "确认移除了什么）。素材不存在（含畸形 id、重复删除）404 KNW_005"
                    + "（数字业务码 2005）。出站经框架 OpenApiClient.delete（cartisan-boot#33）。")
    public ApiResponse<AiplatformMaterialSummaryResponse> delete(@PathVariable String id) {
        return ApiResponse.ok(materialAppService.delete(id));
    }
}
