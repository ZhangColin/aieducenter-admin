package com.aieducenter.admin.endpoints.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.aieducenter.admin.application.PermissionScanAppService;
import com.aieducenter.admin.application.dto.response.PermissionResponse;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * PermissionController API 测试。
 */
@ExtendWith(MockitoExtension.class)
class PermissionControllerTest {

    @Mock
    private PermissionScanAppService permissionScanAppService;

    private PermissionController controller;
    private org.springframework.test.web.servlet.MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new PermissionController(permissionScanAppService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void given_default_scope_when_scanPermissions_then_returnPermissions() throws Exception {
        // Given
        when(permissionScanAppService.scanByScope("admin"))
                .thenReturn(List.of(
                        new PermissionResponse("admin:user:read", "用户管理-查看"),
                        new PermissionResponse("admin:user:write", "用户管理-编辑")
                ));

        // When & Then
        mvc.perform(get("/api/admin/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("admin:user:read"))
                .andExpect(jsonPath("$.data[0].name").value("用户管理-查看"))
                .andExpect(jsonPath("$.data[1].code").value("admin:user:write"));

        verify(permissionScanAppService).scanByScope("admin");
    }

    @Test
    void given_custom_scope_when_scanPermissions_then_returnPermissionsForScope() throws Exception {
        // Given
        when(permissionScanAppService.scanByScope("user"))
                .thenReturn(List.of(
                        new PermissionResponse("user:profile:read", "个人资料-查看")
                ));

        // When & Then
        mvc.perform(get("/api/admin/permissions").param("scope", "user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("user:profile:read"))
                .andExpect(jsonPath("$.data[0].name").value("个人资料-查看"));

        verify(permissionScanAppService).scanByScope("user");
    }

    @Test
    void given_empty_permissions_when_scanPermissions_then_returnEmptyList() throws Exception {
        // Given
        when(permissionScanAppService.scanByScope("admin"))
                .thenReturn(List.of());

        // When & Then
        mvc.perform(get("/api/admin/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}