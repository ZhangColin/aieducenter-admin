package com.aieducenter.admin.endpoints.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.aieducenter.admin.application.RoleManagementAppService;
import com.aieducenter.admin.application.dto.command.AssignMenusCommand;
import com.aieducenter.admin.application.dto.command.AssignPermissionsCommand;
import com.aieducenter.admin.application.dto.command.CreateRoleCommand;
import com.aieducenter.admin.application.dto.command.UpdateRoleCommand;
import com.aieducenter.admin.application.dto.query.AdminRoleQuery;
import com.aieducenter.admin.application.dto.response.RoleResponse;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * AdminRoleController API 测试。
 */
@ExtendWith(MockitoExtension.class)
class AdminRoleControllerTest {

    @Mock
    private RoleManagementAppService roleManagementAppService;

    private AdminRoleController controller;
    private org.springframework.test.web.servlet.MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new AdminRoleController(roleManagementAppService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void given_authenticatedUser_when_findAll_then_returnRoles() throws Exception {
        // Given
        when(roleManagementAppService.findAll(any(AdminRoleQuery.class), any()))
                .thenReturn(new com.cartisan.web.response.PageResponse<>(
                        List.of(new RoleResponse(1L, "管理员", "ADMIN", "系统管理员", 1, null, null)),
                        1L, 1, 20
                ));

        // When & Then
        mvc.perform(get("/api/admin/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("管理员"))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(roleManagementAppService).findAll(any(AdminRoleQuery.class), any());
    }

    @Test
    void given_authenticatedUser_when_findById_then_returnRole() throws Exception {
        // Given
        when(roleManagementAppService.findById(1L))
                .thenReturn(new RoleResponse(1L, "管理员", "ADMIN", "系统管理员", 1, null, null));

        // When & Then
        mvc.perform(get("/api/admin/roles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("管理员"));

        verify(roleManagementAppService).findById(1L);
    }

    @Test
    void given_validCommand_when_createRole_then_returnRoleId() throws Exception {
        // Given
        String json = """
                {
                    "name": "测试角色",
                    "code": "TEST",
                    "description": "测试角色描述",
                    "sortOrder": 1
                }
                """;

        when(roleManagementAppService.create(any(CreateRoleCommand.class))).thenReturn(1L);

        // When & Then
        mvc.perform(post("/api/admin/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        verify(roleManagementAppService).create(any(CreateRoleCommand.class));
    }

    @Test
    void given_validCommand_when_updateRole_then_success() throws Exception {
        // Given
        String json = """
                {
                    "name": "新角色名",
                    "code": "NEW_CODE",
                    "description": "新描述",
                    "sortOrder": 2
                }
                """;

        // When & Then
        mvc.perform(put("/api/admin/roles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(roleManagementAppService).update(eq(1L), any(UpdateRoleCommand.class));
    }

    @Test
    void given_existingRole_when_deleteRole_then_success() throws Exception {
        // When & Then
        mvc.perform(delete("/api/admin/roles/1"))
                .andExpect(status().isOk());

        verify(roleManagementAppService).delete(1L);
    }

    @Test
    void given_validMenus_when_assignMenus_then_success() throws Exception {
        // Given
        String json = """
                {
                    "menuIds": [1, 2, 3]
                }
                """;

        // When & Then
        mvc.perform(put("/api/admin/roles/1/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(roleManagementAppService).assignMenus(eq(1L), any(AssignMenusCommand.class));
    }

    @Test
    void given_validPermissions_when_assignPermissions_then_success() throws Exception {
        // Given
        String json = """
                {
                    "permissionCodes": ["admin:user:read", "admin:user:write"]
                }
                """;

        // When & Then
        mvc.perform(put("/api/admin/roles/1/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(roleManagementAppService).assignPermissions(eq(1L), any(AssignPermissionsCommand.class));
    }
}
