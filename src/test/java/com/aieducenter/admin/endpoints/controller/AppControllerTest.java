package com.aieducenter.admin.endpoints.controller;

import com.aieducenter.admin.application.AppManagementAppService;
import com.aieducenter.admin.application.dto.response.ApiKeyCreatedResponse;
import com.aieducenter.admin.application.dto.response.AppDetailResponse;
import com.aieducenter.admin.application.dto.response.AppSummaryResponse;
import com.aieducenter.admin.application.dto.response.SsoClientCreatedResponse;
import com.aieducenter.admin.application.dto.response.SsoClientResponse;
import com.cartisan.web.response.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AppController 接线测试（URL → 方法 → 服务、状态码）。
 */
@ExtendWith(MockitoExtension.class)
class AppControllerTest {

    @Mock
    private AppManagementAppService appManagementAppService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        AppController controller = new AppController(appManagementAppService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void given_query_when_list_then_returnPage() throws Exception {
        when(appManagementAppService.list(any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0L, 1, 20));

        mvc.perform(get("/api/admin/apps")
                        .param("page", "0").param("size", "20"))
                .andExpect(status().isOk());

        verify(appManagementAppService).list(any(), any());
    }

    @Test
    void given_id_when_getDetail_then_returnApp() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(appManagementAppService.getDetail(1L)).thenReturn(
                new AppDetailResponse(1L, "my-app", "My App", "desc",
                        1, "启用",
                        new AppDetailResponse.ApiKeyInfo(10L, "my-app", 1, "启用", now, now),
                        null, now, now));

        mvc.perform(get("/api/admin/apps/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.appCode").value("my-app"))
                .andExpect(jsonPath("$.data.name").value("My App"))
                .andExpect(jsonPath("$.data.apiKey.apiKey").value("my-app"));

        verify(appManagementAppService).getDetail(1L);
    }

    // ========== create ==========

    @Test
    void given_validBody_when_create_then_returnCreatedApp() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(appManagementAppService.create(any()))
                .thenReturn(new AppDetailResponse(1L, "new-app", "New App", "desc",
                        1, "启用", null, null, now, now));

        String body = new ObjectMapper().writeValueAsString(
                java.util.Map.of("appCode", "new-app", "name", "New App", "description", "desc"));

        mvc.perform(post("/api/admin/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.appCode").value("new-app"))
                .andExpect(jsonPath("$.data.name").value("New App"));

        verify(appManagementAppService).create(any());
    }

    @Test
    void given_missingName_when_create_then_return400() throws Exception {
        String body = new ObjectMapper().writeValueAsString(
                java.util.Map.of("appCode", "new-app", "description", "desc"));

        mvc.perform(post("/api/admin/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ========== update ==========

    @Test
    void given_validBody_when_update_then_returnUpdatedApp() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(appManagementAppService.update(eq(1L), any()))
                .thenReturn(new AppDetailResponse(1L, "my-app", "Updated", "new-desc",
                        1, "启用", null, null, now, now));

        String body = new ObjectMapper().writeValueAsString(
                java.util.Map.of("name", "Updated", "description", "new-desc"));

        mvc.perform(put("/api/admin/apps/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated"))
                .andExpect(jsonPath("$.data.description").value("new-desc"));

        verify(appManagementAppService).update(eq(1L), any());
    }

    // ========== disable ==========

    @Test
    void given_id_when_disable_then_return200() throws Exception {
        mvc.perform(put("/api/admin/apps/1/disable"))
                .andExpect(status().isOk());

        verify(appManagementAppService).disable(1L);
    }

    // ========== enable ==========

    @Test
    void given_id_when_enable_then_return200() throws Exception {
        mvc.perform(put("/api/admin/apps/1/enable"))
                .andExpect(status().isOk());

        verify(appManagementAppService).enable(1L);
    }

    // ========== manageApiKey ==========

    @Test
    void given_id_when_manageApiKey_then_returnCreatedWithSecret() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(appManagementAppService.manageApiKey(1L))
                .thenReturn(new ApiKeyCreatedResponse(10L, 1L, "my-app", "sk-abc123",
                        1, "启用", now, now));

        mvc.perform(post("/api/admin/apps/1/api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.apiKey").value("my-app"))
                .andExpect(jsonPath("$.data.apiSecret").value("sk-abc123"))
                .andExpect(jsonPath("$.data.status").value(1));

        verify(appManagementAppService).manageApiKey(1L);
    }

    // ========== manageSsoClientCredentials ==========

    @Test
    void given_id_when_manageSsoClientCredentials_then_returnCreatedWithSecret() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(appManagementAppService.manageSsoClientCredentials(1L))
                .thenReturn(new SsoClientCreatedResponse(20L, 1L, "oidc-client", "cs-xyz789",
                        List.of(), List.of(), Set.of(), Set.of(),
                        1, "启用", now, now));

        mvc.perform(post("/api/admin/apps/1/sso-client/credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(20))
                .andExpect(jsonPath("$.data.clientId").value("oidc-client"))
                .andExpect(jsonPath("$.data.clientSecret").value("cs-xyz789"))
                .andExpect(jsonPath("$.data.status").value(1));

        verify(appManagementAppService).manageSsoClientCredentials(1L);
    }

    // ========== updateSsoClientConfig ==========

    @Test
    void given_validBody_when_updateSsoClientConfig_then_returnViewWithoutSecret() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(appManagementAppService.updateSsoClientConfig(eq(1L), any()))
                .thenReturn(new SsoClientResponse(20L, 1L, "oidc-stable",
                        List.of("https://example.com/cb"),
                        List.of("https://example.com/logout"),
                        Set.of("openid"), Set.of("authorization_code"),
                        1, "启用", now, now));

        String body = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "redirectUris", List.of("https://example.com/cb"),
                "postLogoutRedirectUris", List.of("https://example.com/logout"),
                "scopes", Set.of("openid"),
                "grants", Set.of("authorization_code")));

        mvc.perform(put("/api/admin/apps/1/sso-client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(20))
                .andExpect(jsonPath("$.data.clientId").value("oidc-stable"))
                .andExpect(jsonPath("$.data.redirectUris[0]").value("https://example.com/cb"))
                .andExpect(jsonPath("$.data.postLogoutRedirectUris[0]").value("https://example.com/logout"))
                // 一次性 clientSecret 仅凭证接口返——配置 PUT 响应绝不带
                .andExpect(jsonPath("$.data.clientSecret").doesNotExist());

        verify(appManagementAppService).updateSsoClientConfig(eq(1L), any());
    }
}
