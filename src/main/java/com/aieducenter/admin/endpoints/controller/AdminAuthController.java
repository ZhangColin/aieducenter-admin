package com.aieducenter.admin.endpoints.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.aieducenter.admin.application.AdminUserAuthAppService;
import com.aieducenter.admin.application.dto.command.AdminUserLoginCommand;
import com.aieducenter.admin.application.dto.command.UpdatePasswordCommand;
import com.aieducenter.admin.application.dto.response.CurrentUserResponse;
import com.cartisan.security.annotation.CurrentUser;
import com.cartisan.security.annotation.RequireAuth;
import com.cartisan.security.authentication.TokenInfo;
import com.cartisan.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 管理员认证控制器。
 */
@RestController
@RequestMapping("/api/admin/auth")
@Validated
@Tag(name = "Admin Auth", description = "管理员认证")
public class AdminAuthController {

    private final AdminUserAuthAppService adminAuthAppService;

    public AdminAuthController(AdminUserAuthAppService adminAuthAppService) {
        this.adminAuthAppService = adminAuthAppService;
    }

    @PostMapping("/login")
    @Operation(summary = "管理员登录")
    public ApiResponse<TokenInfo> login(@Valid @RequestBody AdminUserLoginCommand command) {
        return ApiResponse.ok(adminAuthAppService.login(command));
    }

    @PostMapping("/logout")
    @Operation(summary = "管理员登出")
    public ApiResponse<Void> logout() {
        adminAuthAppService.logout();
        return ApiResponse.ok();
    }

    @GetMapping("/current")
    @RequireAuth
    @Operation(summary = "获取当前管理员信息")
    public ApiResponse<CurrentUserResponse> getCurrentAdmin(@CurrentUser Long userId) {
        return ApiResponse.ok(adminAuthAppService.getCurrentAdmin(userId));
    }

    @PutMapping("/current/password")
    @RequireAuth
    @Operation(summary = "修改当前管理员密码")
    public ApiResponse<Void> updatePassword(
            @CurrentUser Long userId,
            @Valid @RequestBody UpdatePasswordCommand command) {
        adminAuthAppService.updatePassword(userId, command);
        return ApiResponse.ok();
    }
}
