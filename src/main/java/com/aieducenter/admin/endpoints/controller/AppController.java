package com.aieducenter.admin.endpoints.controller;

import com.aieducenter.admin.application.AppManagementAppService;
import com.aieducenter.admin.application.dto.command.CreateAppCommand;
import com.aieducenter.admin.application.dto.command.UpdateAppCommand;
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
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping
    @RequireAuth
    @RequirePermission(
            value = "admin:app:write",
            name = "平台管理 / 应用管理 / 编辑",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "创建应用")
    public ApiResponse<AppDetailResponse> create(@Valid @RequestBody CreateAppCommand command) {
        return ApiResponse.ok(appManagementAppService.create(command));
    }

    @PutMapping("/{id}")
    @RequireAuth
    @RequirePermission(
            value = "admin:app:write",
            name = "平台管理 / 应用管理 / 编辑",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "更新应用基本信息（name/description）")
    public ApiResponse<AppDetailResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateAppCommand command) {
        return ApiResponse.ok(appManagementAppService.update(id, command));
    }

    @PutMapping("/{id}/disable")
    @RequireAuth
    @RequirePermission(
            value = "admin:app:write",
            name = "平台管理 / 应用管理 / 编辑",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "停用应用")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        appManagementAppService.disable(id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/enable")
    @RequireAuth
    @RequirePermission(
            value = "admin:app:write",
            name = "平台管理 / 应用管理 / 编辑",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "启用应用")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        appManagementAppService.enable(id);
        return ApiResponse.ok();
    }
}
