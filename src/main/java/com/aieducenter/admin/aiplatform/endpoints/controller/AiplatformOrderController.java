package com.aieducenter.admin.aiplatform.endpoints.controller;

import java.time.LocalDateTime;
import java.util.List;

import com.aieducenter.admin.aiplatform.application.AiplatformOrderAppService;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformOrderQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformOrderDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformOrderSummaryResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformSourcePackageResponse;
import com.aieducenter.admin.constants.AdminScopes;
import com.cartisan.security.annotation.RequireAuth;
import com.cartisan.security.annotation.RequirePermission;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 平台订单控制器——BFF 读路径（清单/详情/源码包），issue #64。
 *
 * <p>北向 {@code /api/admin/aiplatform/orders/**} 逐字镜像 aiplatform
 * {@code /api/backoffice/orders/**}（#155/#156 已冻结）——查询参数名、缺省值、分页基准
 * （page 1-based，缺省 1/20）与 provider controller 同形。错误经
 * {@code AiplatformUpstreamErrorAdvice} 原样透传（如 404 ORD_001→5001、400 ORD_010→5010）。</p>
 *
 * <p>分页（spec #62 平台分页统一决议目标态，区别于 payment 的 0-based Pageable ±1 仪式）：
 * 北向请求 page 1-based、回显 provider 1-based 原值，全链零换算；provider clamp
 * （page≥1、size∈[1,100]）行为透传，BFF 不重复夹取。</p>
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/admin/aiplatform/orders")
@Validated
@Tag(name = "Admin Aiplatform Order", description = "AI 平台 · 订单")
public class AiplatformOrderController {

    private final AiplatformOrderAppService orderAppService;

    public AiplatformOrderController(AiplatformOrderAppService orderAppService) {
        this.orderAppService = orderAppService;
    }

    // 不挂 @ErrorCodes：框架 ErrorCodesValidator 要求码在应用自身 CodeMessageRegistry 注册，admin 不镜像
    // provider 错误码目录（忠实透传、零加戏——spec #62）；错误码以 description 文档化（ORD_010 等）。
    @GetMapping
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:order:read",
            name = "AI 平台 / 订单查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "订单清单（四维检索，分页 1-based）——透传 aiplatform",
            description = "运营工作清单：新单在前（TSID 倒序，provider 定死）。四维可组合、均可缺省"
                    + "（缺省＝全量）：① status 状态多选，Integer code 逗号分隔单值（如 status=1,5；"
                    + "1=待报价 2=已报价 3=已支付 4=已归档 5=已取消）；② createdFrom/createdTo 创建时间区间"
                    + "（ISO-8601，含两端，如 2026-09-01T00:00:00）；③ externalId 下单账号（provider 换算，"
                    + "换算不到＝空清单 200）；④ orderId 订单号精确（TSID 十进制，查无/非数值→空清单 200）。"
                    + "page 1 基（缺省 1）、size 缺省 20（provider 上界 100），clamp 由 provider 执行、"
                    + "回显 provider 回报值。绑定裁决两段：非整数 code/非法时间在本服务绑定层 400（框架信封）；"
                    + "数值但未知的 code 由 provider 裁决 400 ORD_010（数字业务码 5010）透传。"
                    + "行带 ownerDisplayName（下单账号缺档为 null）。")
    public ApiResponse<PageResponse<AiplatformOrderSummaryResponse>> orders(
            @RequestParam(required = false) List<Integer> status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(required = false) String externalId,
            @RequestParam(required = false) String orderId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(orderAppService.list(
                new AiplatformOrderQuery(status, createdFrom, createdTo, externalId, orderId), page, size));
    }

    @GetMapping("/{id}")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:order:read",
            name = "AI 平台 / 订单查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "订单详情（含 append-only 价目历史）——透传 aiplatform",
            description = "报价依据全量：状态、金额+最新备注、价目历史（append-only 全量，新→旧，"
                    + "每条带操作者——存量行操作者为空）、PRD 快照正文（下单冻结）、项目名、下单用户昵称、"
                    + "状态时点组（含取消/归档操作者留痕两肢）。金额 Long（分）。订单不存在 404 ORD_001"
                    + "（数字业务码 5001）。")
    public ApiResponse<AiplatformOrderDetailResponse> detail(@PathVariable String id) {
        return ApiResponse.ok(orderAppService.getDetail(id));
    }

    @GetMapping("/{id}/source-package")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:order:read",
            name = "AI 平台 / 订单查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "订单源码包（tar.gz 二进制流，无 ApiResponse 信封）——透传 aiplatform",
            description = "交付取件：经项目工作区实时打包（排除 node_modules/.env/data/.platform），"
                    + "Content-Type=application/gzip + Content-Disposition 文件名均取 provider 原值透传。"
                    + "订单不存在 404 ORD_001（数字业务码 5001）；打包失败 500 WSP_002。")
    public ResponseEntity<byte[]> sourcePackage(@PathVariable String id) {
        AiplatformSourcePackageResponse pkg = orderAppService.getSourcePackage(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, pkg.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, pkg.contentDisposition())
                .body(pkg.content());
    }
}
