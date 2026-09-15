package com.aieducenter.admin.aiplatform.endpoints.controller;

import java.time.Instant;

import com.aieducenter.admin.aiplatform.application.AiplatformCostAppService;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformCostQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformCostOverviewResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectCostDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectCostResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformUnpricedUsageResponse;
import com.aieducenter.admin.constants.AdminScopes;
import com.cartisan.security.annotation.RequireAuth;
import com.cartisan.security.annotation.RequirePermission;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * AI 平台成本控制器——BFF 纯观测四读口（全局总览 / unpriced 全局警示 / 项目成本清单 /
 * 单项目下钻），issue #67。
 *
 * <p>北向 {@code /api/admin/aiplatform/costs/**} 逐字镜像 aiplatform
 * {@code /api/backoffice/costs/**}（#161/#164 成本运营）——查询参数名、分页基准（page 1-based，
 * 缺省 1/20）与 provider controller 同形。<strong>时间窗差异（issue #67 拍板）</strong>：provider
 * 侧 {@code from}/{@code to} 可缺省（缺省＝该侧不限），北向<strong>必填</strong>——逐字镜像、
 * 不设默认窗口（窗口由运营显式给出，provider 的不限能力不经 BFF 暴露，防无界扫描）。错误经
 * {@code AiplatformUpstreamErrorAdvice} 原样透传（如 400 METER_011→3011）。</p>
 *
 * <p>权限码（spec #62）：读 {@code cost:read} 一码（四读口同码——观测面整体授权）；成本域无写
 * 操作（改价走单价表域）。操作者身份透传不涉及（纯读口）。</p>
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/admin/aiplatform/costs")
@Validated
@Tag(name = "Admin Aiplatform Cost", description = "AI 平台 · 平台成本")
public class AiplatformCostController {

    private final AiplatformCostAppService costAppService;

    public AiplatformCostController(AiplatformCostAppService costAppService) {
        this.costAppService = costAppService;
    }

    // 不挂 @ErrorCodes：框架 ErrorCodesValidator 要求码在应用自身 CodeMessageRegistry 注册，admin 不镜像
    // provider 错误码目录（忠实透传、零加戏——spec #62）；错误码以 description 文档化（METER_011 等）。
    @GetMapping("/overview")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:cost:read",
            name = "AI 平台 / 成本查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "平台成本全局总览（时间窗）——透传 aiplatform",
            description = "全平台跨项目观测模型开销构成：总量（五档 input/output/cacheRead/"
                    + "cacheWrite/reasoning，JSON 数字）+ 平台成本（token × 事件时点生效单价，"
                    + "按币种分桶直读不折算、键 = ISO 4217 币种码、值 BigDecimal）+ 分模型 + 分智能体"
                    + "（agentKind 裸维度串原值 + agentKindName 中文名随行——aiplatform#186 已落，"
                    + "naming/classify 等辅助标记为 null，前端落「—」桶）。与报价脱钩——纯平台付出"
                    + "金额，无建议售价推导；改价不溯及（历史事件按当时价，成本不漂移）。无生效单价"
                    + "的分量不进 cost（不伪装 0），未配价观测走 unpriced 端点。from/to 时间窗半开"
                    + "区间 [from, to)、ISO-8601 Instant（UTC 带 Z，如 2026-09-01T00:00:00Z），"
                    + "<strong>北向必填</strong>（不设默认窗口）；空窗/无数据返回全零 total 与空分桶，"
                    + "不是错误。绑定裁决在本服务绑定层（不到 provider）：缺参 400（框架信封）、"
                    + "非 ISO-8601 Instant 404（框架类型不匹配口径）；provider 侧绑定失败"
                    + " 400 METER_011（数字业务码 3011）透传。")
    public ApiResponse<AiplatformCostOverviewResponse> overview(
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return ApiResponse.ok(costAppService.overview(new AiplatformCostQuery(from, to)));
    }

    @GetMapping("/unpriced")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:cost:read",
            name = "AI 平台 / 成本查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "unpriced 全局警示（用量驱动）——透传 aiplatform",
            description = "窗口内有 token 用量且事件时点无生效单价的 (provider, model, 档位) 按档位"
                    + "汇总 token（只计无价分量——同档位部分有价部分无价时只计无价部分）。用量驱动："
                    + "已配价档位与无用量档位不出现，静态配价缺口清单不做（无用量＝无实际损失）；据此"
                    + "发现漏配价并及时补价（补价只影响此后事件，历史成本不漂移）。tokenKind 为 Integer"
                    + " code（1=输入 2=输出 3=缓存读 4=缓存写 5=推理）+ tokenKindName 中文名随行。"
                    + "from/to 时间窗半开 [from, to)、ISO-8601 Instant（UTC 带 Z），<strong>北向必填</strong>"
                    + "（不设默认窗口）；空窗/无未配价用量返回空 items，不是错误。绑定裁决同总览端点"
                    + "（400 METER_011→3011 透传）。")
    public ApiResponse<AiplatformUnpricedUsageResponse> unpriced(
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return ApiResponse.ok(costAppService.unpriced(new AiplatformCostQuery(from, to)));
    }

    @GetMapping("/projects")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:cost:read",
            name = "AI 平台 / 成本查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "项目成本清单（窗口聚合，成本降序分页）——透传 aiplatform",
            description = "运营扫一眼谁费钱：窗口内有 token 用量的各项目成本汇总——总量（五档）+"
                    + " 平台成本（按币种分桶直读不折算、键 = ISO 4217 币种码）。排序服务端定死：成本"
                    + "降序（排序标量＝币种桶金额直加，单价表单币种时＝精确），全未配价项目（有用量但"
                    + "无任何已配价分量，成本标量缺失）排后且 allUnpriced=true 标注，同序按 projectId"
                    + " 升序稳定。用量驱动：无用量项目不出现在清单（空窗＝空清单 200）；已删项目的历史"
                    + "花费照列（成本观测不抹历史，行 projectId 不解释存在性，项目名归本服务按 id 互查）。"
                    + "page 1 基（缺省 1）、size 缺省 20（provider 上界 100），clamp 由 provider 执行、"
                    + "回显 provider 回报值。from/to 时间窗半开 [from, to)、ISO-8601 Instant（UTC 带 Z），"
                    + "<strong>北向必填</strong>（不设默认窗口）。查询参数（含分页）绑定失败"
                    + " 400 METER_011（数字业务码 3011）透传。")
    public ApiResponse<PageResponse<AiplatformProjectCostResponse>> projectCosts(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(costAppService.listProjectCosts(
                new AiplatformCostQuery(from, to), page, size));
    }

    @GetMapping("/projects/{projectId}")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:cost:read",
            name = "AI 平台 / 成本查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "单项目成本下钻（byModel/byAgentKind 分解）——透传 aiplatform",
            description = "单项目成本构成分解，复用 bySubject 聚合口径（与全局总览/项目清单同一换算"
                    + "规则）：总量（五档）+ 平台成本（币种分桶直读不折算）+ 未配价标注清单（窗口内有"
                    + "用量且时点无生效价的 provider/model/档位，与 cost 互补不重叠；bySubject 口径无"
                    + " token 计数——档位用量汇总走全局 unpriced 端点）+ 分模型 + 分智能体"
                    + "（agentKind 裸维度串原值 + agentKindName 随行）。projectId = 计量 subject 原值"
                    + "（写侧口径 projectId 十进制串，provider 不解释存在性）：无用量/查无此号返回"
                    + "全零 total 与空结构（明确空态，非错误、不 404）。from/to 时间窗半开 [from, to)、"
                    + "ISO-8601 Instant（UTC 带 Z），<strong>北向必填</strong>（不设默认窗口）。"
                    + "查询参数绑定失败 400 METER_011（数字业务码 3011）透传。")
    public ApiResponse<AiplatformProjectCostDetailResponse> projectCostDetail(
            @PathVariable String projectId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return ApiResponse.ok(costAppService.getProjectCostDetail(
                projectId, new AiplatformCostQuery(from, to)));
    }
}
