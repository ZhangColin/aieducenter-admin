package com.aieducenter.admin.endpoints.controller;

import com.aieducenter.admin.application.AppManagementAppService;
import com.aieducenter.admin.application.dto.query.AppManagementQuery;
import com.aieducenter.admin.application.dto.response.AppDetailResponse;
import com.aieducenter.admin.application.dto.response.AppSummaryResponse;
import com.aieducenter.admin.constants.AdminScopes;
import com.cartisan.security.annotation.RequireAuth;
import com.cartisan.security.annotation.RequirePermission;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用管理控制器——BFF 读路径，聚合 app-registry 的 app + apiKey + ssoClient。
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/admin/apps")
@Validated
@Tag(name = "Admin Apps", description = "应用管理")
public class AppController {

    private final AppManagementAppService appManagementAppService;

    public AppController(AppManagementAppService appManagementAppService) {
        this.appManagementAppService = appManagementAppService;
    }

    @GetMapping
    @RequireAuth
    @RequirePermission(
            value = "admin:app:read",
            name = "平台管理 / 应用管理 / 查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "分页查询应用列表")
    public ApiResponse<PageResponse<AppSummaryResponse>> list(
            AppManagementQuery query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(appManagementAppService.list(query, pageable));
    }

    @GetMapping("/{id}")
    @RequireAuth
    @RequirePermission(
            value = "admin:app:read",
            name = "平台管理 / 应用管理 / 查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "查询应用详情（聚合 app + apiKey + ssoClient）")
    public ApiResponse<AppDetailResponse> getDetail(@PathVariable Long id) {
        return ApiResponse.ok(appManagementAppService.getDetail(id));
    }
}
