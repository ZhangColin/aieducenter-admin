package com.aieducenter.admin.account.endpoints.controller;

import com.aieducenter.admin.account.application.AccountManagementAppService;
import com.aieducenter.admin.account.application.dto.command.AccountReasonCommand;
import com.aieducenter.admin.account.application.dto.command.DisableAccountCommand;
import com.aieducenter.admin.account.application.dto.query.AccountQuery;
import com.aieducenter.admin.account.application.dto.response.AccountManagementDetailResponse;
import com.aieducenter.admin.account.application.dto.response.AccountSummaryResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/{userId}/management")
    @RequireAuth
    @RequirePermission(
            value = "admin:account:read",
            name = "账号管理 / 查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "查询账号管理详情（状态/锁定/是否有密码/资料）——透传 identity，供前端抽屉")
    public ApiResponse<AccountManagementDetailResponse> getManagementDetail(
            @PathVariable Long userId) {
        return ApiResponse.ok(accountAppService.getManagementDetail(userId));
    }

    @PostMapping("/{userId}/disable")
    @RequireAuth
    @RequirePermission(
            value = "admin:account:write",
            name = "账号管理 / 状态操作",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "封号（reason 必填）——identity 自动踢所有会话；操作者身份经 RequestContext 透传，审计归 identity")
    public ApiResponse<Void> disable(
            @PathVariable Long userId,
            @Valid @RequestBody DisableAccountCommand command) {
        // operator 身份不经 body：框架 cartisan-openapi 自动从 RequestContext 带 X-User-Id/X-User-Name 出站 header，
        // identity 据此审计（与 payment 退款审核把 auditor 塞 body 不同——identity 管理端点从 RequestContext 读 operator）。
        // 纯透传（spec「纯透传」）：identity 写端点返 204 无 body，admin 不回读——成功 ack（data=null），前端自行回读详情。
        accountAppService.disable(userId, command.reason());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{userId}/activate")
    @RequireAuth
    @RequirePermission(
            value = "admin:account:write",
            name = "账号管理 / 状态操作",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "解封——状态置 ACTIVE；操作者身份经 RequestContext 透传，审计归 identity")
    public ApiResponse<Void> activate(
            @PathVariable Long userId,
            @Valid @RequestBody(required = false) AccountReasonCommand command) {
        // reason 可选（低危可逆动作）；@Valid 兜 @Size(max=500)；operator 经 RequestContext 透传（同 disable）。纯透传。
        accountAppService.activate(userId, command == null ? null : command.reason());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{userId}/unlock")
    @RequireAuth
    @RequirePermission(
            value = "admin:account:write",
            name = "账号管理 / 状态操作",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "解除系统锁定（区别于封号状态）——locked 置 false；操作者身份经 RequestContext 透传，审计归 identity")
    public ApiResponse<Void> unlock(
            @PathVariable Long userId,
            @Valid @RequestBody(required = false) AccountReasonCommand command) {
        // reason 可选；@Valid 兜 @Size(max=500)；operator 经 RequestContext 透传（同 disable）。纯透传。
        accountAppService.unlock(userId, command == null ? null : command.reason());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{userId}/sessions/revoke")
    @RequireAuth
    @RequirePermission(
            value = "admin:account:write",
            name = "账号管理 / 状态操作",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "强制下线——一键 revoke 该账号全部会话、不改账号状态（区别于封号）；操作者身份经 RequestContext 透传，审计归 identity")
    public ApiResponse<Void> revokeSessions(
            @PathVariable Long userId,
            @Valid @RequestBody(required = false) AccountReasonCommand command) {
        // 强制下线 ≠ 封号：只清 SSO 会话、不动 status（identity revokeSessions 契约）；reason 可选；
        // @Valid 兜 @Size(max=500)；operator 经 RequestContext 透传（同 disable）。纯透传。
        accountAppService.revokeSessions(userId, command == null ? null : command.reason());
        return ApiResponse.ok(null);
    }
}
