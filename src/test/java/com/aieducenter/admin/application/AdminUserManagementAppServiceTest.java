package com.aieducenter.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aieducenter.admin.application.dto.command.AssignRolesCommand;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.application.dto.command.UpdateAdminUserCommand;
import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.aieducenter.admin.domain.enums.AdminUserStatus;
import com.aieducenter.admin.domain.error.AdminMessage;
import com.aieducenter.admin.domain.repository.AdminRoleRepository;
import com.aieducenter.admin.domain.repository.AdminUserRepository;
import com.aieducenter.admin.application.mapper.AdminUserMapper;
import com.aieducenter.admin.domain.service.PasswordEncoderService;
import com.cartisan.core.exception.ApplicationException;
import com.cartisan.core.exception.DomainException;

@ExtendWith(MockitoExtension.class)
class AdminUserManagementAppServiceTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private AdminRoleRepository adminRoleRepository;

    @Mock
    private AdminUserAuthAppService adminUserAuthAppService;

    @Mock
    private AdminUserMapper adminUserMapper;

    @Mock
    private PasswordEncoderService passwordEncoderService;

    private AdminUserManagementAppService adminUserManagementAppService;

    @BeforeEach
    void setUp() {
        adminUserManagementAppService = new AdminUserManagementAppService(
            adminUserRepository,
            adminRoleRepository,
            adminUserAuthAppService,
            adminUserMapper,
            passwordEncoderService
        );
    }

    @Test
    void given_valid_input_when_createAdminUser_then_success() {
        // Given
        CreateAdminUserCommand command = new CreateAdminUserCommand(
            "testuser", "Test1234", "测试用户", "test@example.com", null
        );
        when(adminUserRepository.existsByUsername("testuser")).thenReturn(false);
        when(passwordEncoderService.encodePassword("Test1234")).thenReturn("$2a$10$encodedPassword");
        when(adminUserRepository.save(any(AdminUser.class)))
            .thenAnswer(invocation -> {
                AdminUser user = invocation.getArgument(0);
                // Simulate JPA @PrePersist behavior by setting ID via reflection on the actual saved entity
                java.lang.reflect.Field idField = AdminUser.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(user, 1L);
                return user;
            });

        // When
        Long id = adminUserManagementAppService.create(command);

        // Then
        assertThat(id).isNotNull();
        assertThat(id).isEqualTo(1L);
    }

    @Test
    void given_duplicate_username_when_createAdminUser_then_throw_exception() {
        // Given
        CreateAdminUserCommand command = new CreateAdminUserCommand(
            "testuser", "Test1234", "测试用户", null, null
        );
        when(adminUserRepository.existsByUsername("testuser")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> adminUserManagementAppService.create(command))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(AdminMessage.USERNAME_ALREADY_EXISTS.message());
    }

    @Test
    void given_existingAdmin_when_delete_then_delegatesToRepository() {
        // Given — 破窗号不可删守卫由聚合 markAsDeleted() 承担，应用服务只负责加载并委托仓储
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        when(adminUserRepository.findById(1L)).thenReturn(Optional.of(adminUser));

        // When
        adminUserManagementAppService.delete(1L);

        // Then
        verify(adminUserRepository).delete(adminUser);
    }

    // ========== update tests ==========

    @Test
    void given_valid_input_when_update_then_success() {
        // Given
        Long userId = 1L;
        UpdateAdminUserCommand command = new UpdateAdminUserCommand("新昵称", "new@example.com", "13900139000", "http://example.com/avatar.jpg");
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");

        when(adminUserRepository.findById(userId)).thenReturn(Optional.of(adminUser));

        // When
        adminUserManagementAppService.update(userId, command);

        // Then
        assertThat(adminUser.getNickname()).isEqualTo("新昵称");
        assertThat(adminUser.getEmail()).isEqualTo("new@example.com");
        assertThat(adminUser.getPhone()).isEqualTo("13900139000");
        assertThat(adminUser.getAvatar()).isEqualTo("http://example.com/avatar.jpg");
        verify(adminUserRepository).save(adminUser);
    }

    @Test
    void given_nonexistent_user_when_update_then_throw_exception() {
        // Given
        Long userId = 999L;
        UpdateAdminUserCommand command = new UpdateAdminUserCommand("新昵称", null, null, null);

        when(adminUserRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> adminUserManagementAppService.update(userId, command))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(AdminMessage.ADMIN_NOT_FOUND.message());
    }

    @Test
    void given_partial_update_when_update_then_only_update_non_null_fields() {
        // Given
        Long userId = 1L;
        UpdateAdminUserCommand command = new UpdateAdminUserCommand("新昵称", null, null, null);
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        adminUser.setEmail("old@example.com");

        when(adminUserRepository.findById(userId)).thenReturn(Optional.of(adminUser));

        // When
        adminUserManagementAppService.update(userId, command);

        // Then
        assertThat(adminUser.getNickname()).isEqualTo("新昵称");
        assertThat(adminUser.getEmail()).isEqualTo("old@example.com"); // unchanged
        verify(adminUserRepository).save(adminUser);
    }

    // ========== updateStatus tests ==========

    @Test
    void given_active_status_when_updateStatus_to_disabled_then_success() {
        // Given
        Long userId = 1L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");

        when(adminUserRepository.findById(userId)).thenReturn(Optional.of(adminUser));

        // When
        adminUserManagementAppService.updateStatus(userId, AdminUserStatus.DISABLED);

        // Then
        assertThat(adminUser.getStatus()).isEqualTo(AdminUserStatus.DISABLED);
        verify(adminUserRepository).save(adminUser);
    }

    @Test
    void given_disabled_status_when_updateStatus_to_active_then_success() {
        // Given
        Long userId = 1L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        adminUser.disable();

        when(adminUserRepository.findById(userId)).thenReturn(Optional.of(adminUser));

        // When
        adminUserManagementAppService.updateStatus(userId, AdminUserStatus.ACTIVE);

        // Then
        assertThat(adminUser.getStatus()).isEqualTo(AdminUserStatus.ACTIVE);
        verify(adminUserRepository).save(adminUser);
    }

    @Test
    void given_nonexistent_user_when_updateStatus_then_throw_exception() {
        // Given
        Long userId = 999L;

        when(adminUserRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> adminUserManagementAppService.updateStatus(userId, AdminUserStatus.ACTIVE))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(AdminMessage.ADMIN_NOT_FOUND.message());
    }

    // ========== assignRoles tests ==========

    @Test
    void given_valid_roles_when_assignRoles_then_success() throws Exception {
        // Given
        Long userId = 1L;
        AssignRolesCommand command = new AssignRolesCommand(List.of(1L, 2L));
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        AdminRole role1 = new AdminRole("管理员", "ADMIN", "管理员", 1);
        AdminRole role2 = new AdminRole("操作员", "OPERATOR", "操作员", 2);

        // Set IDs using reflection
        java.lang.reflect.Field idField = AdminRole.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(role1, 1L);
        idField.set(role2, 2L);

        when(adminUserRepository.findById(userId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findAllById(command.roleIds())).thenReturn(List.of(role1, role2));

        // When
        adminUserManagementAppService.assignRoles(userId, command);

        // Then
        assertThat(adminUser.getRoleIds()).containsExactlyInAnyOrder(1L, 2L);
        verify(adminUserRepository).save(adminUser);
    }

    @Test
    void given_nonexistent_user_when_assignRoles_then_throw_exception() {
        // Given
        Long userId = 999L;
        AssignRolesCommand command = new AssignRolesCommand(List.of(1L));

        when(adminUserRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> adminUserManagementAppService.assignRoles(userId, command))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(AdminMessage.ADMIN_NOT_FOUND.message());
    }

    @Test
    void given_nonexistent_role_when_assignRoles_then_throw_exception() throws Exception {
        // Given
        Long userId = 1L;
        AssignRolesCommand command = new AssignRolesCommand(List.of(1L, 999L));
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        AdminRole role1 = new AdminRole("管理员", "ADMIN", "管理员", 1);

        // Set ID using reflection
        java.lang.reflect.Field idField = AdminRole.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(role1, 1L);

        when(adminUserRepository.findById(userId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findAllById(command.roleIds())).thenReturn(List.of(role1)); // Only role1 exists, 999L doesn't

        // When & Then
        assertThatThrownBy(() -> adminUserManagementAppService.assignRoles(userId, command))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining(AdminMessage.ROLE_NOT_FOUND.message());
    }

    @Test
    void given_empty_roles_when_assignRoles_then_clear_existing_roles() {
        // Given
        Long userId = 1L;
        AssignRolesCommand command = new AssignRolesCommand(List.of());
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        adminUser.addRole(1L);

        when(adminUserRepository.findById(userId)).thenReturn(Optional.of(adminUser));

        // When
        adminUserManagementAppService.assignRoles(userId, command);

        // Then
        assertThat(adminUser.getRoleIds()).isEmpty();
        verify(adminUserRepository).save(adminUser);
    }

    @Test
    void given_breakGlassAdmin_when_assignRolesWithoutSuperAdmin_then_throwException() throws Exception {
        // Given — 破窗号必须保留 SUPER_ADMIN（守住全权救援能力）
        Long userId = AdminUser.BREAK_GLASS_ADMIN_ID;
        AdminUser breakGlass = new AdminUser("admin", "Test1234", "破窗号");
        java.lang.reflect.Field idField = AdminUser.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(breakGlass, userId);

        AdminRole superRole = new AdminRole("超管", AdminRole.SUPER_ADMIN_CODE, "超级管理员", 0);
        AdminRole otherRole = new AdminRole("运营", "OPERATOR", "运营", 1);
        java.lang.reflect.Field roleIdField = AdminRole.class.getDeclaredField("id");
        roleIdField.setAccessible(true);
        roleIdField.set(superRole, 100L);
        roleIdField.set(otherRole, 200L);

        AssignRolesCommand command = new AssignRolesCommand(List.of(200L)); // 不含 SUPER_ADMIN

        when(adminUserRepository.findById(userId)).thenReturn(Optional.of(breakGlass));
        when(adminRoleRepository.findAllById(command.roleIds())).thenReturn(List.of(otherRole));
        when(adminRoleRepository.findByCode(AdminRole.SUPER_ADMIN_CODE)).thenReturn(Optional.of(superRole));

        // When & Then
        assertThatThrownBy(() -> adminUserManagementAppService.assignRoles(userId, command))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(AdminMessage.BREAK_GLASS_SUPER_ADMIN_REQUIRED.message());
        // 被拒 → 原角色关联不应被改动
        assertThat(breakGlass.getRoleIds()).isEmpty();
        verify(adminUserRepository, never()).save(breakGlass);
    }

    @Test
    void given_breakGlassAdmin_when_assignRolesKeepsSuperAdmin_then_success() throws Exception {
        // Given — 破窗号保留 SUPER_ADMIN 时放行
        Long userId = AdminUser.BREAK_GLASS_ADMIN_ID;
        AdminUser breakGlass = new AdminUser("admin", "Test1234", "破窗号");
        java.lang.reflect.Field idField = AdminUser.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(breakGlass, userId);

        AdminRole superRole = new AdminRole("超管", AdminRole.SUPER_ADMIN_CODE, "超级管理员", 0);
        java.lang.reflect.Field roleIdField = AdminRole.class.getDeclaredField("id");
        roleIdField.setAccessible(true);
        roleIdField.set(superRole, 100L);

        AssignRolesCommand command = new AssignRolesCommand(List.of(100L)); // 含 SUPER_ADMIN

        when(adminUserRepository.findById(userId)).thenReturn(Optional.of(breakGlass));
        when(adminRoleRepository.findAllById(command.roleIds())).thenReturn(List.of(superRole));
        when(adminRoleRepository.findByCode(AdminRole.SUPER_ADMIN_CODE)).thenReturn(Optional.of(superRole));

        // When
        adminUserManagementAppService.assignRoles(userId, command);

        // Then
        assertThat(breakGlass.getRoleIds()).containsExactly(100L);
        verify(adminUserRepository).save(breakGlass);
    }

    @Test
    void given_weak_password_when_create_then_throw_application_exception() {
        // Given
        CreateAdminUserCommand command = new CreateAdminUserCommand("testuser", "weak", "测试用户", null, null);

        // When & Then
        assertThatThrownBy(() -> adminUserManagementAppService.create(command))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining(AdminMessage.PASSWORD_WEAK.message());
    }
}
