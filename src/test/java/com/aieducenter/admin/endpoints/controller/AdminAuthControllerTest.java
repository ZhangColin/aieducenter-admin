package com.aieducenter.admin.endpoints.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.aieducenter.admin.application.AdminUserAuthAppService;
import com.aieducenter.admin.application.dto.command.AdminUserLoginCommand;
import com.aieducenter.admin.application.dto.command.UpdatePasswordCommand;
import com.aieducenter.admin.application.dto.response.AdminUserResponse;
import com.aieducenter.admin.application.dto.response.CurrentUserResponse;
import com.aieducenter.admin.domain.enums.AdminUserStatus;
import com.cartisan.core.context.RequestContext;
import com.cartisan.security.authentication.TokenInfo;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * AdminAuthController API 测试。
 *
 * <p>注：{@code getCurrentAdmin} / {@code updatePassword} 经 {@link RequestContext#getUserId()} 取当前用户
 * （controller 重构后不再用 {@code @CurrentUser} 参数）。standalone MockMvc 不跑过滤器，故这两个用例需在
 * {@code RequestContext.runFor} 内执行以填充上下文。</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthControllerTest {

    private static final Long TEST_USER_ID = 1L;

    @Mock
    private AdminUserAuthAppService adminAuthAppService;

    private AdminAuthController controller;
    private org.springframework.test.web.servlet.MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new AdminAuthController(adminAuthAppService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void given_validCredentials_when_login_then_returnTokenInfo() throws Exception {
        // Given
        String json = """
                {
                    "username": "admin",
                    "password": "Admin123",
                    "rememberMe": false
                }
                """;

        TokenInfo tokenInfo = new TokenInfo("test-token", 1L, Instant.now().plusSeconds(86400));
        when(adminAuthAppService.login(any(AdminUserLoginCommand.class))).thenReturn(tokenInfo);

        // When & Then
        mvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("test-token"))
                .andExpect(jsonPath("$.data.loginId").value(1));

        verify(adminAuthAppService).login(any(AdminUserLoginCommand.class));
    }

    @Test
    void given_authenticatedUser_when_logout_then_success() throws Exception {
        // When & Then
        mvc.perform(post("/api/admin/auth/logout"))
                .andExpect(status().isOk());

        verify(adminAuthAppService).logout();
    }

    @Test
    void given_authenticatedUser_when_getCurrentAdmin_then_returnUserInfoWithoutMenus() throws Exception {
        // Given
        AdminUserResponse user = new AdminUserResponse(1L, "admin", "管理员", null, null, null, null, null,
                AdminUserStatus.ACTIVE, null, true, null, null, null);
        List<String> roleCodes = List.of("SUPER_ADMIN");
        List<String> permissions = List.of("admin:user:read", "admin:user:write");

        when(adminAuthAppService.getCurrentAdmin(TEST_USER_ID))
                .thenReturn(new CurrentUserResponse(user, roleCodes, permissions));

        // When & Then — controller 经 RequestContext 取 userId，需在上下文中执行
        runAsTestUser(() -> {
            mvc.perform(get("/api/admin/auth/current"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.user.username").value("admin"))
                    .andExpect(jsonPath("$.data.roleCodes.length()").value(1))
                    .andExpect(jsonPath("$.data.roleCodes[0]").value("SUPER_ADMIN"))
                    .andExpect(jsonPath("$.data.permissions.length()").value(2))
                    // REQ-13-T3：身份 claims 收敛为 {user, roleCodes, permissions}，导航归 /menus/my
                    .andExpect(jsonPath("$.data.menus").doesNotExist());
            return null;
        });

        verify(adminAuthAppService).getCurrentAdmin(TEST_USER_ID);
    }

    @Test
    void given_validPasswords_when_updatePassword_then_success() throws Exception {
        // Given
        String json = """
                {
                    "oldPassword": "OldPass123",
                    "newPassword": "NewPass123"
                }
                """;

        // When & Then — controller 经 RequestContext 取 userId，需在上下文中执行
        runAsTestUser(() -> {
            mvc.perform(put("/api/admin/auth/current/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk());
            return null;
        });

        verify(adminAuthAppService).updatePassword(eq(TEST_USER_ID), any(UpdatePasswordCommand.class));
    }

    /**
     * 在填充了 TEST_USER_ID 的 RequestContext 中执行（standalone MockMvc 不跑过滤器，需手动注入上下文）。
     */
    private void runAsTestUser(Callable<Void> body) throws Exception {
        RequestContext.runFor(
                new RequestContext(null, null, null, null, TEST_USER_ID, null, null, null),
                body);
    }
}
