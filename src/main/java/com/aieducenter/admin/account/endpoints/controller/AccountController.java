package com.aieducenter.admin.account.endpoints.controller;

import com.aieducenter.admin.account.application.AccountManagementAppService;
import com.aieducenter.admin.account.application.dto.query.AccountQuery;
import com.aieducenter.admin.account.application.dto.response.AccountSummaryResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号管理控制器——BFF 读路径，聚合 identity 终端用户账号管理能力域。
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/admin/accounts")
@Validated
@Tag(name = "Admin Account", description = "账号管理")
public class AccountController {

    private final AccountManagementAppService accountAppService;

    public AccountController(AccountManagementAppService accountAppService) {
        this.accountAppService = accountAppService;
    }

    @GetMapping
    @RequireAuth
    @RequirePermission(
            value = "admin:account:read",
            name = "账号管理 / 查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "分页搜索平台账号（email/phone/userId/status/locked/注册时间区间）")
    public ApiResponse<PageResponse<AccountSummaryResponse>> list(
            AccountQuery query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(accountAppService.list(query, pageable));
    }
}
