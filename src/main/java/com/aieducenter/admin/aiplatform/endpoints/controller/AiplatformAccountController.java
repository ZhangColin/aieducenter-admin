package com.aieducenter.admin.aiplatform.endpoints.controller;

import com.aieducenter.admin.aiplatform.application.AiplatformAccountAppService;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformAccountProfileResponse;
import com.aieducenter.admin.constants.AdminScopes;
import com.cartisan.security.annotation.RequireAuth;
import com.cartisan.security.annotation.RequirePermission;
import com.cartisan.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 平台账号档案控制器——BFF 读路径，聚合 aiplatform 账号极简档案读口（issue #63 T1）。
 *
 * <p>北向 {@code GET /api/admin/aiplatform/accounts/{externalId}} 逐字镜像 aiplatform
 * {@code GET /api/backoffice/accounts/{externalId}}（#154 已冻结）——无独立页面，订单/项目详情
 * 抽屉内嵌使用。错误经 {@code AiplatformUpstreamErrorAdvice} 原样透传（如 404 IDN_004）。</p>
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/admin/aiplatform/accounts")
@Validated
@Tag(name = "Admin Aiplatform Account", description = "AI 平台 · 账号档案")
public class AiplatformAccountController {

    private final AiplatformAccountAppService accountAppService;

    public AiplatformAccountController(AiplatformAccountAppService accountAppService) {
        this.accountAppService = accountAppService;
    }

    @GetMapping("/{externalId}")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:account:read",
            name = "AI 平台 / 账号档案查看",
            scope = AdminScopes.ADMIN
    )
    // 不挂 @ErrorCodes：框架 ErrorCodesValidator 要求码在应用自身 CodeMessageRegistry 注册，admin 不镜像
    // provider 错误码目录（忠实透传、零加戏——spec #62）；错误码以 description 文档化（IDN_004 等）。
    @Operation(summary = "账号极简档案（按 externalId）——透传 aiplatform，供订单/项目详情抽屉认交易对手",
            description = "externalId＝OIDC sub＝identity 账户 Id（对外正身）。返回 aiplatform 留存四字段"
                    + "原样（id/externalId/displayName/createdAt，id 为 TSID 十进制字符串）。"
                    + "externalId 未命中 404 IDN_004（数字业务码 6004）。")
    public ApiResponse<AiplatformAccountProfileResponse> profile(
            @PathVariable String externalId) {
        return ApiResponse.ok(accountAppService.getAccountProfile(externalId));
    }
}
