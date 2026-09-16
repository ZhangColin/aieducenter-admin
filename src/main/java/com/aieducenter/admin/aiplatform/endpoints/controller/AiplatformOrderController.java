package com.aieducenter.admin.aiplatform.endpoints.controller;

import java.time.LocalDateTime;
import java.util.List;

import com.aieducenter.admin.aiplatform.application.AiplatformOrderAppService;
import com.aieducenter.admin.aiplatform.application.dto.command.AiplatformOrderCancelCommand;
import com.aieducenter.admin.aiplatform.application.dto.command.AiplatformOrderQuoteCommand;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformOrderQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformOrderDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformOrderResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 平台订单控制器——BFF 读路径（清单/详情/源码包，issue #64）＋写路径三操作
 * （报价/改价、运营取消、重试归档，issue #70）。
 *
 * <p>北向 {@code /api/admin/aiplatform/orders/**} 逐字镜像 aiplatform
 * {@code /api/backoffice/orders/**}（#155/#156/#29/#157/#158 已冻结）——查询参数名、缺省值、
 * 分页基准（page 1-based，缺省 1/20）与 provider controller 同形。错误经
 * {@code AiplatformUpstreamErrorAdvice} 原样透传（如 404 ORD_001→5001、400 ORD_010→5010、
 * 409 PRJ_013→4013 跨域码）。</p>
 *
 * <p>分页（spec #62 平台分页统一决议目标态；#73 起全平台收口同语义，见 ADR-0012）：
 * 北向请求 page 1-based、回显 provider 1-based 原值，全链零换算；provider clamp
 * （page≥1、size∈[1,100]）行为透传，BFF 不重复夹取。</p>
 *
 * <p>权限码（spec #62）：读 {@code order:read} 一码（#64）；三写操作各自独立成码
 * （{@code order:quote|cancel|retry-archive}，issue #70）——最小授权（如只给定价员配报价
 * 而不给取消/归档）。操作者身份由框架 OpenApiClient 自动透传 {@code X-User-Id/X-User-Name}
 * 出站头（identity 同款），provider 落痕价目行（报价）/订单行（取消/归档）——认知口径：
 * 操作者＝admin 侧管理员，非 aiplatform 平台用户；北向无操作者字段，前端无法伪造留痕。</p>
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

    @PostMapping("/{id}/quote")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:order:quote",
            name = "AI 平台 / 订单报价改价",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "提交报价（已报价态重复提交＝改价）——透传 aiplatform",
            description = "交易定价写口：待报价态首次提交＝报价（→已报价）；已报价态重复提交＝改价"
                    + "（状态不变，append-only 价目行留痕、订单现值取最新行，改价历史用户面可见）"
                    + "——<strong>语义由 provider 承担、BFF 不解释</strong>（同一端点同一命令体，前端"
                    + "无需区分模式）。命令体逐字镜像 {amount, note}：amount 总价（分，正整数，"
                    + "JSON string——Long 出口口径）；note 报价备注（可空，至多 1000 字，用户面展示）。"
                    + "回执＝OrderResponse（provider 用户面同构：金额/备注取最新价目行＋改价历史"
                    + "新→旧，价目行五字段无操作者——查全量留痕转后台详情读口）。X-User-Id/"
                    + "X-User-Name 透传头自动落痕价目行（缺头落空）。订单不存在（含畸形 id）"
                    + " 404 ORD_001（数字业务码 5001）；限未支付态：已支付/已终结 409 ORD_007（5007）；"
                    + "金额非正 400 ORD_008（5008）；备注超长 400 ORD_009（5009）。")
    public ApiResponse<AiplatformOrderResponse> quote(
            @PathVariable String id, @RequestBody AiplatformOrderQuoteCommand command) {
        return ApiResponse.ok(orderAppService.quote(id, command));
    }

    @PostMapping("/{id}/cancel")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:order:cancel",
            name = "AI 平台 / 订单取消",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "运营取消订单（限未支付态）——透传 aiplatform",
            description = "异常交易处置：语义与用户取消完全一致——订单落已取消、项目解冻回迭代、"
                    + "用户可继续对话与再次下单。取消原因必填（运营内部口径留档，不呈现任何用户面"
                    + "读面、后台订单详情可见）。命令体逐字镜像 {reason}（必填，至多 1000 字）。"
                    + "回执＝OrderResponse（已取消终态，cancelledAt 落定）。X-User-Id/X-User-Name"
                    + " 透传头自动落痕订单行（缺头落空）。订单不存在（含畸形 id）404 ORD_001"
                    + "（数字业务码 5001）；限未支付态：已支付/已归档/已取消 409 ORD_005（5005，"
                    + "退款/售后另议）；原因缺失 400 ORD_013（5013）；原因超长 400 ORD_014（5014）。")
    public ApiResponse<AiplatformOrderResponse> cancel(
            @PathVariable String id, @RequestBody AiplatformOrderCancelCommand command) {
        return ApiResponse.ok(orderAppService.cancel(id, command));
    }

    @PostMapping("/{id}/retry-archive")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:order:retry-archive",
            name = "AI 平台 / 订单重试归档",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "重试归档（已支付未归档的卡单补归档）——透传 aiplatform",
            description = "交付闭环补齐：对支付成功但归档失败的卡单手动补完结——一事务内订单落"
                    + "已归档＋项目归档，成功后补发「已归档」通知并触发知识沉淀（成交 PRD 入"
                    + "知识库，best-effort 不炸主流程）。幂等由 provider 既有守卫保证。<strong>无请求体</strong>。"
                    + "回执＝OrderResponse（已归档终态，paidAt/archivedAt 双时点）。X-User-Id/"
                    + "X-User-Name 透传头自动落痕订单行（缺头落空；支付链自动归档操作者为空）。"
                    + "订单不存在（含畸形 id）404 ORD_001（数字业务码 5001）；重复触发/非已支付态"
                    + " 409 ORD_012（5012）；项目已归档 409 <strong>PRJ_013</strong>（数字业务码"
                    + " 4013，跨域码原样透传——不产生重复素材）。")
    public ApiResponse<AiplatformOrderResponse> retryArchive(@PathVariable String id) {
        return ApiResponse.ok(orderAppService.retryArchive(id));
    }
}
