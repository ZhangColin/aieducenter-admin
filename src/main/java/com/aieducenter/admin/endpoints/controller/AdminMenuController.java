package com.aieducenter.admin.endpoints.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.aieducenter.admin.application.AdminUserPermissionAppService;
import com.aieducenter.admin.application.MenuManagementAppService;
import com.aieducenter.admin.application.dto.command.CreateMenuCommand;
import com.aieducenter.admin.application.dto.command.UpdateMenuCommand;
import com.aieducenter.admin.application.dto.query.MenuQuery;
import com.aieducenter.admin.application.dto.response.MenuResponse;
import com.aieducenter.admin.application.dto.response.MyMenusResponse;
import com.aieducenter.admin.constants.AdminScopes;
import com.cartisan.core.context.RequestContext;
import com.cartisan.security.annotation.RequireAuth;
import com.cartisan.security.annotation.RequirePermission;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.response.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 菜单管理控制器。
 */
@RestController
@RequestMapping("/api/admin/menus")
@Validated
@Tag(name = "Admin Menus", description = "菜单管理")
public class AdminMenuController {

    private final MenuManagementAppService menuManagementAppService;
    private final AdminUserPermissionAppService adminUserPermissionAppService;

    public AdminMenuController(MenuManagementAppService menuManagementAppService,
                               AdminUserPermissionAppService adminUserPermissionAppService) {
        this.menuManagementAppService = menuManagementAppService;
        this.adminUserPermissionAppService = adminUserPermissionAppService;
    }

    /**
     * 「我的导航」——消费面端点（REQ-13-T2）：登录即可访问，<b>不挂管理权限</b>
     * （普通用户也要拉自己的导航；与管理面 {@code GET /menus}/{@code /menus/tree} 权限语义不同）。
     */
    @GetMapping("/my")
    @RequireAuth
    @Operation(summary = "查询当前用户可见导航（home + 启用菜单树，登录即可）")
    public ApiResponse<MyMenusResponse> my() {
        return ApiResponse.ok(adminUserPermissionAppService.getMyMenus(RequestContext.getUserId()));
    }

    @GetMapping
    @RequireAuth
    @RequirePermission(
        value = "admin:menu:read",
        name = "平台管理 / 菜单管理 / 查看",
        scope = AdminScopes.ADMIN
    )
    @Operation(summary = "查询菜单列表（扁平分页，Soybean 菜单表格用）")
    public ApiResponse<PageResponse<MenuResponse>> findAll(
            MenuQuery query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(menuManagementAppService.findAll(query, pageable));
    }

    @GetMapping("/tree")
    @RequireAuth
    @RequirePermission(
        value = "admin:menu:read",
        name = "平台管理 / 菜单管理 / 查看",
        scope = AdminScopes.ADMIN
    )
    @Operation(summary = "查询菜单树（父级选择器 / 角色分配用）")
    public ApiResponse<List<MenuResponse>> findTree() {
        return ApiResponse.ok(menuManagementAppService.findTree());
    }

    @GetMapping("/{id}")
    @RequireAuth
    @RequirePermission(
        value = "admin:menu:read",
        name = "平台管理 / 菜单管理 / 查看",
        scope = AdminScopes.ADMIN
    )
    @Operation(summary = "查询菜单详情")
    public ApiResponse<MenuResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(menuManagementAppService.findById(id));
    }

    @PostMapping
    @RequireAuth
    @RequirePermission(
        value = "admin:menu:write",
        name = "平台管理 / 菜单管理 / 编辑",
        scope = AdminScopes.ADMIN
    )
    @Operation(summary = "创建菜单")
    public ApiResponse<Long> create(@Valid @RequestBody CreateMenuCommand command) {
        return ApiResponse.ok(menuManagementAppService.create(command));
    }

    @PutMapping("/{id}")
    @RequireAuth
    @RequirePermission(
        value = "admin:menu:write",
        name = "平台管理 / 菜单管理 / 编辑",
        scope = AdminScopes.ADMIN
    )
    @Operation(summary = "更新菜单")
    public ApiResponse<Void> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMenuCommand command) {
        menuManagementAppService.update(id, command);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @RequireAuth
    @RequirePermission(
        value = "admin:menu:write",
        name = "平台管理 / 菜单管理 / 编辑",
        scope = AdminScopes.ADMIN
    )
    @Operation(summary = "删除菜单")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        menuManagementAppService.delete(id);
        return ApiResponse.ok();
    }
}
