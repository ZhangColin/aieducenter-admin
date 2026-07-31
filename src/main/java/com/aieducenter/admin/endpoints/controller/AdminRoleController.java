package com.aieducenter.admin.endpoints.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.aieducenter.admin.application.RoleManagementAppService;
import com.aieducenter.admin.application.dto.command.AssignMenusCommand;
import com.aieducenter.admin.application.dto.command.AssignPermissionsCommand;
import com.aieducenter.admin.application.dto.command.CreateRoleCommand;
import com.aieducenter.admin.application.dto.command.UpdateRoleCommand;
import com.aieducenter.admin.application.dto.query.AdminRoleQuery;
import com.aieducenter.admin.application.dto.response.RoleResponse;
import com.aieducenter.admin.application.dto.response.RoleOptionResponse;
import com.aieducenter.admin.constants.AdminScopes;
import com.aieducenter.admin.domain.enums.AdminRoleStatus;
import com.cartisan.security.annotation.RequireAuth;
import com.cartisan.security.annotation.RequirePermission;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.response.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 角色管理控制器。
 */
@RestController
@RequestMapping("/api/admin/roles")
@Validated
@Tag(name = "Admin Roles", description = "角色管理")
public class AdminRoleController {

    private final RoleManagementAppService roleManagementAppService;

    public AdminRoleController(RoleManagementAppService roleManagementAppService) {
        this.roleManagementAppService = roleManagementAppService;
    }

    @GetMapping
    @RequireAuth
    @RequirePermission(
        value = "admin:role:read",
        name = "平台管理 / 角色管理 / 查看",
        scope = AdminScopes.ADMIN
    )
    @Operation(summary = "查询角色列表（分页）")
    public ApiResponse<PageResponse<RoleResponse>> findAll(
            AdminRoleQuery query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(roleManagementAppService.findAll(query, pageable));
    }

    @GetMapping("/all")
    @RequireAuth
    @RequirePermission(
        value = "admin:role:read",
        name = "平台管理 / 角色管理 / 查看",
        scope = AdminScopes.ADMIN
    )
    @Operation(summary = "查询启用角色字典（不分页，精简 {id,name,code}）")
    public ApiResponse<List<RoleOptionResponse>> findAllEnabled() {
        return ApiResponse.ok(roleManagementAppService.listEnabledOptions());
    }

    @GetMapping("/{id}")
    @RequireAuth
    @RequirePermission(
        value = "admin:role:read",
        name = "平台管理 / 角色管理 / 查看",
        scope = AdminScopes.ADMIN
    )
    @Operation(summary = "查询角色详情")
    public ApiResponse<RoleResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(roleManagementAppService.findById(id));
    }

    @PostMapping
    @RequireAuth
    @RequirePermission(
        value = "admin:role:write",
        name = "平台管理 / 角色管理 / 编辑",
        scope = AdminScopes.ADMIN
    )
    @Operation(summary = "创建角色")
    public ApiResponse<Long> create(@Valid @RequestBody CreateRoleCommand command) {
        return ApiResponse.ok(roleManagementAppService.create(command));
    }

    @PutMapping("/{id}")
    @RequireAuth
    @RequirePermission(
        value = "admin:role:write",
        name = "平台管理 / 角色管理 / 编辑",
        scope = AdminScopes.ADMIN
    )
    @Operation(summary = "更新角色")
    public ApiResponse<Void> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleCommand command) {
        roleManagementAppService.update(id, command);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @RequireAuth
    @RequirePermission(
        value = "admin:role:write",
        name = "平台管理 / 角色管理 / 编辑",
        scope = AdminScopes.ADMIN
    )
    @Operation(summary = "删除角色")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleManagementAppService.delete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/status")
    @RequireAuth
    @RequirePermission(
        value = "admin:role:write",
        name = "平台管理 / 角色管理 / 编辑",
        scope = AdminScopes.ADMIN
    )
    @Operation(summary = "修改角色状态")
    public ApiResponse<Void> updateStatus(
            @PathVariable Long id,
            @RequestParam AdminRoleStatus status) {
        // cartisan-boot 自动转换：?status=1 → AdminRoleStatus.ENABLED / ?status=0 → DISABLED
        roleManagementAppService.updateStatus(id, status);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/menus")
    @RequireAuth
    @RequirePermission(
        value = "admin:role:write",
        name = "平台管理 / 角色管理 / 编辑",
        scope = AdminScopes.ADMIN
    )
    @Operation(summary = "分配菜单")
    public ApiResponse<Void> assignMenus(
            @PathVariable Long id,
            @Valid @RequestBody AssignMenusCommand command) {
        roleManagementAppService.assignMenus(id, command);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/permissions")
    @RequireAuth
    @RequirePermission(
        value = "admin:role:write",
        name = "平台管理 / 角色管理 / 编辑",
        scope = AdminScopes.ADMIN
    )
    @Operation(summary = "分配权限")
    public ApiResponse<Void> assignPermissions(
            @PathVariable Long id,
            @Valid @RequestBody AssignPermissionsCommand command) {
        roleManagementAppService.assignPermissions(id, command);
        return ApiResponse.ok();
    }
}
