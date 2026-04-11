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

import com.aieducenter.admin.application.AdminUserManagementAppService;
import com.aieducenter.admin.application.dto.command.AssignRolesCommand;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.application.dto.command.ResetPasswordCommand;
import com.aieducenter.admin.application.dto.command.UpdateAdminUserCommand;
import com.aieducenter.admin.application.dto.query.AdminUserQuery;
import com.aieducenter.admin.application.dto.response.AdminUserResponse;
import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.aieducenter.admin.domain.enums.AdminUserStatus;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * AdminUserController API 测试。
 */
@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock
    private AdminUserManagementAppService adminManagementAppService;

    private AdminUserController controller;
    private org.springframework.test.web.servlet.MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new AdminUserController(adminManagementAppService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void given_authenticatedUser_when_findAll_then_returnUsers() throws Exception {
        // Given
        List<AdminUserResponse> users = List.of(
                new AdminUserResponse(1L, "admin", "管理员", null, null, null, AdminUserStatus.ACTIVE, null, null, null),
                new AdminUserResponse(2L, "user", "普通用户", null, null, null, AdminUserStatus.ACTIVE, null, null, null)
        );

        when(adminManagementAppService.findAll(any(AdminUserQuery.class), any()))
                .thenReturn(new com.cartisan.web.response.PageResponse<>(users, 2, 1, 20));

        // When & Then
        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].username").value("admin"))
                .andExpect(jsonPath("$.data.total").value(2));

        verify(adminManagementAppService).findAll(any(AdminUserQuery.class), any());
    }

    @Test
    void given_authenticatedUser_when_findById_then_returnUser() throws Exception {
        // Given
        when(adminManagementAppService.findById(1L))
                .thenReturn(new AdminUserResponse(1L, "admin", "管理员", null, null, null, AdminUserStatus.ACTIVE, null, null, null));

        // When & Then
        mvc.perform(get("/api/admin/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("admin"));

        verify(adminManagementAppService).findById(1L);
    }

    @Test
    void given_validCommand_when_createUser_then_returnUserId() throws Exception {
        // Given
        String json = """
                {
                    "username": "testuser",
                    "password": "Test1234",
                    "nickname": "测试用户",
                    "email": "test@example.com",
                    "phone": "13800138000"
                }
                """;

        when(adminManagementAppService.create(any(CreateAdminUserCommand.class))).thenReturn(1L);

        // When & Then
        mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        verify(adminManagementAppService).create(any(CreateAdminUserCommand.class));
    }

    @Test
    void given_validCommand_when_updateUser_then_success() throws Exception {
        // Given
        String json = """
                {
                    "nickname": "新昵称",
                    "email": "new@example.com",
                    "phone": "13900139000",
                    "avatar": "http://example.com/avatar.jpg"
                }
                """;

        // When & Then
        mvc.perform(put("/api/admin/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(adminManagementAppService).update(eq(1L), any(UpdateAdminUserCommand.class));
    }

    @Test
    void given_existingUser_when_deleteUser_then_success() throws Exception {
        // When & Then
        mvc.perform(delete("/api/admin/users/1"))
                .andExpect(status().isOk());

        verify(adminManagementAppService).delete(1L);
    }

    @Test
    void given_validStatus_when_updateStatus_then_success() throws Exception {
        // Given
        // 注意：MockMvc standalone 模式不会加载 cartisan-boot 的自动配置
        // 因此 BaseEnum 参数绑定需要集成测试环境才能验证
        // 这里只验证 Controller 调用了 AppService

        // When & Then
        // 直接触用 Controller 方法进行测试
        controller.updateStatus(1L, AdminUserStatus.DISABLED);

        verify(adminManagementAppService).updateStatus(eq(1L), eq(AdminUserStatus.DISABLED));
    }

    // TODO: BaseEnum 参数绑定需要在集成测试中验证（@WebMvcTest 或完整 Spring 上下文）
    // 单元测试只验证业务逻辑调用

    @Test
    void given_validRoles_when_assignRoles_then_success() throws Exception {
        // Given
        String json = """
                {
                    "roleIds": [1, 2]
                }
                """;

        // When & Then
        mvc.perform(put("/api/admin/users/1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(adminManagementAppService).assignRoles(eq(1L), any(AssignRolesCommand.class));
    }

    @Test
    void given_validPassword_when_resetPassword_then_success() throws Exception {
        // Given
        String json = """
                {
                    "newPassword": "NewPass123"
                }
                """;

        // When & Then
        mvc.perform(put("/api/admin/users/1/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(adminManagementAppService).resetPassword(eq(1L), any(ResetPasswordCommand.class));
    }
}
