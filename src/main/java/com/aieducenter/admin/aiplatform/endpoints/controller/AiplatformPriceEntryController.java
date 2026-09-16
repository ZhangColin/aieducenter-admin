package com.aieducenter.admin.aiplatform.endpoints.controller;

import com.aieducenter.admin.aiplatform.application.AiplatformPriceEntryAppService;
import com.aieducenter.admin.aiplatform.application.dto.command.AiplatformPriceEntryRepriceCommand;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformPriceEntryQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformUnitPriceEntryRepriceResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformUnitPriceEntryResponse;
import com.aieducenter.admin.constants.AdminScopes;
import com.cartisan.security.annotation.RequireAuth;
import com.cartisan.security.annotation.RequirePermission;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 平台单价表控制器——BFF 核价（含历史行清单）＋原子改价＋停用三端点，issue #68。
 *
 * <p>北向 {@code /api/admin/aiplatform/price-entries/**} 逐字镜像 aiplatform
 * {@code /api/backoffice/price-entries/**}（#160 成本运营＋#165 写口唯一化）——查询参数名、
 * 缺省值、分页基准（page 1-based，缺省 1/20）与 provider controller 同形。<strong>provider 的
 * 「开行」端点（POST /price-entries，种子脚本通道）不建北向</strong>（spec #62）——web 改价只走
 * 原子 reprice。错误经 {@code AiplatformUpstreamErrorAdvice} 原样透传（如 400 METER_009→3009、
 * 404 METER_006→3006、409 METER_007→3007、409 METER_008→3008）。</p>
 *
 * <p>权限码（spec #62）：读 {@code price-entry:read} 一码；写操作各自独立成码
 * （{@code price-entry:reprice|deactivate}）——最小授权（如只给调价审核员配 reprice 而不给停用）。
 * 操作者身份由框架 OpenApiClient 自动透传 {@code X-User-Id/X-User-Name} 出站头（identity 同款），
 * provider 落痕改价新行/停用被关行——北向命令体不含操作者字段，前端无法伪造留痕。</p>
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/admin/aiplatform/price-entries")
@Validated
@Tag(name = "Admin Aiplatform Price Entry", description = "AI 平台 · 单价表")
public class AiplatformPriceEntryController {

    private final AiplatformPriceEntryAppService priceEntryAppService;

    public AiplatformPriceEntryController(AiplatformPriceEntryAppService priceEntryAppService) {
        this.priceEntryAppService = priceEntryAppService;
    }

    // 不挂 @ErrorCodes：框架 ErrorCodesValidator 要求码在应用自身 CodeMessageRegistry 注册，admin 不镜像
    // provider 错误码目录（忠实透传、零加戏——spec #62）；错误码以 description 文档化（METER_009 等）。
    @GetMapping
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:price-entry:read",
            name = "AI 平台 / 单价表查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "单价行清单（含历史行，provider/model 过滤，分页 1-based）——透传 aiplatform",
            description = "运营核价工作面：现行与历史行全量（价史全貌），排序服务端定死＝生效起点倒序"
                    + "（新段在前，同起点 id 倒序稳定）。provider/model 均为匹配键成分＝精确等值过滤、"
                    + "均可缺省（缺省＝全量行）；effectiveTo 为 null 即当前行。行字段：tokenKind Integer"
                    + " code（1=输入 2=输出 3=缓存读 4=缓存写 5=推理）+ tokenKindName 中文名随行；unitPrice"
                    + " String 明文小数（BigDecimal 语义，精确十进制串）；operatorId/operatorName 留痕"
                    + "（该行最近管理动作——开行或停用；存量行/种子脚本种入行为 null）。page 1 基"
                    + "（缺省 1）、size 缺省 20（provider 上界 100），clamp 由 provider 执行、回显"
                    + " provider 回报值。绑定裁决两段：非整数 page/size 在本服务绑定层 404（框架类型"
                    + "不匹配口径，不到 provider）；provider 侧绑定失败（非法分页值）400 METER_009"
                    + "（数字业务码 3009）仅在 provider 契约演进时可能出现，错误信封忠实透传。")
    public ApiResponse<PageResponse<AiplatformUnitPriceEntryResponse>> entries(
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(priceEntryAppService.list(
                new AiplatformPriceEntryQuery(provider, model), page, size));
    }

    @PostMapping("/{id}/reprice")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:price-entry:reprice",
            name = "AI 平台 / 单价表改价",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "原子改价（单调用关当前行＋开新行，可预发布）——透传 aiplatform",
            description = "同事务两步：被关行落 effectiveTo＝新起点（保留其原开行操作者，不被改写），"
                    + "新行沿用匹配键、单价/币种取命令、敞口生效。命令体逐字镜像 {unitPrice, currency,"
                    + " effectiveFrom}：unitPrice JSON 明文小数（BigDecimal 语义）；effectiveFrom"
                    + " ISO-8601 Instant（UTC 带 Z）可空＝缺省即时，含未来时点＝预发布（对齐供应商凌晨"
                    + "调价，窗口前旧价仍生效）——预发布与同键区间重叠校验语义由 provider 承担，BFF 不"
                    + "代填时点不预判。回执 {closed, opened} 双行（库内事实）。X-User-Id/X-User-Name"
                    + " 透传头自动落痕新行（缺头落空）。行不存在（含畸形 id）404 METER_006（3006）；"
                    + "字段不完整/单价负数 400 METER_004（3004）；币种非 ISO 4217 400 METER_010（3010）；"
                    + "起点早于被关行起点 400 METER_005（3005）；目标非当前行 409 METER_007（3007）；"
                    + "区间重叠（跨区间或同起点）409 METER_008（3008）。")
    public ApiResponse<AiplatformUnitPriceEntryRepriceResponse> reprice(
            @PathVariable String id, @RequestBody AiplatformPriceEntryRepriceCommand command) {
        return ApiResponse.ok(priceEntryAppService.reprice(id, command));
    }

    @PostMapping("/{id}/deactivate")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:price-entry:deactivate",
            name = "AI 平台 / 单价表停用",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "停用（即时生效，关行不接新行）——透传 aiplatform",
            description = "关当前行（effectiveTo＝现在），不开新行——此后该匹配键用量进 unpriced"
                    + "（缺价不伪装 0、不阻断聚合），成本警示面可据此发现缺口。对未生效的预发布行停用＝"
                    + "钳到自身起点成空区间（从未生效）。无请求体。X-User-Id/X-User-Name 透传头自动落痕"
                    + "被关行（停用不接新行，被关行是唯一落点；缺头落空）。行不存在（含畸形 id）"
                    + " 404 METER_006（3006）；目标非当前行 409 METER_007（3007）。")
    public ApiResponse<AiplatformUnitPriceEntryResponse> deactivate(@PathVariable String id) {
        return ApiResponse.ok(priceEntryAppService.deactivate(id));
    }
}
