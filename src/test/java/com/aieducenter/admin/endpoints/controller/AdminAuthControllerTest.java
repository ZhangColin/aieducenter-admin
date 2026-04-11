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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.aieducenter.admin.application.AdminUserAuthAppService;
import com.aieducenter.admin.application.dto.command.AdminUserLoginCommand;
import com.aieducenter.admin.application.dto.command.UpdatePasswordCommand;
import com.aieducenter.admin.application.dto.response.AdminUserResponse;
import com.aieducenter.admin.application.dto.response.CurrentUserResponse;
import com.aieducenter.admin.application.dto.response.MenuResponse;
import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.aieducenter.admin.domain.enums.AdminUserStatus;
import com.cartisan.security.annotation.CurrentUser;
import com.cartisan.security.authentication.TokenInfo;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * AdminAuthController API 测试。
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
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
    }

    /**
     * Mock argument resolver for @CurrentUser annotation.
     */
    private static class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class)
                    && parameter.getParameterType().equals(Long.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return TEST_USER_ID;
        }
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
    void given_authenticatedUser_when_getCurrentAdmin_then_returnUserInfo() throws Exception {
        // Given
        AdminUserResponse user = new AdminUserResponse(1L, "admin", "管理员", null, null, null,
                AdminUserStatus.ACTIVE, null, null, null);
        List<String> roleCodes = List.of("SUPER_ADMIN");
        List<String> permissions = List.of("admin:user:read", "admin:user:write");
        List<MenuResponse> menus = List.of(new MenuResponse(1L, "用户管理", "/users", "user", null, 1, null));

        when(adminAuthAppService.getCurrentAdmin(TEST_USER_ID))
                .thenReturn(new CurrentUserResponse(user, roleCodes, menus, permissions));

        // When & Then
        mvc.perform(get("/api/admin/auth/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value("admin"))
                .andExpect(jsonPath("$.data.roleCodes.length()").value(1))
                .andExpect(jsonPath("$.data.roleCodes[0]").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.data.permissions.length()").value(2))
                .andExpect(jsonPath("$.data.menus.length()").value(1));

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

        // When & Then
        mvc.perform(put("/api/admin/auth/current/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(adminAuthAppService).updatePassword(eq(TEST_USER_ID), any(UpdatePasswordCommand.class));
    }
}
