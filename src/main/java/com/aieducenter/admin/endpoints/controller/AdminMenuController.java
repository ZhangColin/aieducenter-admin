package com.aieducenter.admin.endpoints.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.aieducenter.admin.application.MenuManagementAppService;
import com.aieducenter.admin.application.dto.command.CreateMenuCommand;
import com.aieducenter.admin.application.dto.command.UpdateMenuCommand;
import com.aieducenter.admin.application.dto.query.MenuQuery;
import com.aieducenter.admin.application.dto.response.MenuResponse;
import com.aieducenter.admin.constants.AdminScopes;
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

    public AdminMenuController(MenuManagementAppService menuManagementAppService) {
        this.menuManagementAppService = menuManagementAppService;
    }

    @GetMapping
    @RequireAuth
    @RequirePermission(
        value = "admin:menu:read",
        name = "平台管理 / 菜单管理 / 查看",
        scope = AdminScopes.ADMIN
    )
    @Operation(summary = "查询菜单列表（树形）")
    public ApiResponse<List<MenuResponse>> findTree() {
        return ApiResponse.ok(menuManagementAppService.findTree());
    }

    @GetMapping("/page")
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
