package com.aieducenter.admin.endpoints.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.aieducenter.admin.application.PermissionScanAppService;
import com.aieducenter.admin.application.dto.response.PermissionResponse;
import com.cartisan.security.annotation.RequireAuth;
import com.cartisan.security.annotation.RequirePermission;
import com.cartisan.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 权限查询控制器。
 */
@RestController
@RequestMapping("/api/admin/permissions")
@Tag(name = "Admin Permissions", description = "权限查询")
public class PermissionController {

    private final PermissionScanAppService permissionScanAppService;

    public PermissionController(PermissionScanAppService permissionScanAppService) {
        this.permissionScanAppService = permissionScanAppService;
    }

    @GetMapping
    @RequireAuth
    @RequirePermission(
        value = "admin:permission:read",
        name = "平台管理 / 权限管理 / 查看",
        scope = "admin"
    )
    @Operation(summary = "查询权限列表（扫描结果）")
    public ApiResponse<List<PermissionResponse>> scanPermissions(
            @RequestParam(defaultValue = "admin") String scope) {
        return ApiResponse.ok(permissionScanAppService.scanByScope(scope));
    }
}
