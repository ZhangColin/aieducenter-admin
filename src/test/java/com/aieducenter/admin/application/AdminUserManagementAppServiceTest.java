package com.aieducenter.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.aieducenter.admin.application.dto.command.AssignRolesCommand;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.application.dto.command.UpdateAdminUserCommand;
import com.aieducenter.admin.application.dto.query.AdminUserQuery;
import com.aieducenter.admin.application.dto.response.AdminUserResponse;
import com.aieducenter.admin.application.dto.response.AssignedRoleResponse;
import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.aieducenter.admin.domain.enums.AdminUserGender;
import com.aieducenter.admin.domain.enums.AdminUserStatus;
import com.aieducenter.admin.domain.error.AdminMessage;
import com.aieducenter.admin.domain.repository.AdminRoleRepository;
import com.aieducenter.admin.domain.repository.AdminUserRepository;
import com.aieducenter.admin.application.mapper.AdminUserMapper;
import com.aieducenter.admin.application.mapper.AdminUserMapperImpl;
import com.aieducenter.admin.domain.service.PasswordEncoderService;
import com.cartisan.core.exception.ApplicationException;
import com.cartisan.core.exception.DomainException;
import com.cartisan.web.response.PageResponse;

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
            "testuser", "Test1234", "测试用户", "test@example.com", null, null
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
            "testuser", "Test1234", "测试用户", null, null, null
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
        UpdateAdminUserCommand command = new UpdateAdminUserCommand("新昵称", "new@example.com", "13900139000", "http://example.com/avatar.jpg", null);
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
        UpdateAdminUserCommand command = new UpdateAdminUserCommand("新昵称", null, null, null, null);

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
        UpdateAdminUserCommand command = new UpdateAdminUserCommand("新昵称", null, null, null, null);
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

    // ========== findById roles 回显（REQ-4）==========

    /**
     * 使用真实 MapStruct mapper 的服务实例——roles 断言落在响应对象本身，
     * 而非对 mapper 协作的 mock 验证。
     */
    private AdminUserManagementAppService serviceWithRealMapper() {
        return new AdminUserManagementAppService(
            adminUserRepository,
            adminRoleRepository,
            adminUserAuthAppService,
            new AdminUserMapperImpl(),
            passwordEncoderService
        );
    }

    private static void setRoleId(AdminRole role, Long id) throws Exception {
        java.lang.reflect.Field idField = AdminRole.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(role, id);
    }

    @Test
    void given_assignedRoles_when_findById_then_responseContainsRoleSummaries() throws Exception {
        // Given
        Long userId = 7L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        adminUser.addRole(1L);
        adminUser.addRole(2L);

        AdminRole role1 = new AdminRole("管理员", "ADMIN", "管理员", 1);
        AdminRole role2 = new AdminRole("操作员", "OPERATOR", "操作员", 2);
        setRoleId(role1, 1L);
        setRoleId(role2, 2L);

        when(adminUserRepository.findById(userId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findByIdInAndDeletedFalse(adminUser.getRoleIds()))
            .thenReturn(List.of(role1, role2));

        // When
        AdminUserResponse response = serviceWithRealMapper().findById(userId);

        // Then —— 裁剪投影 {id, name, code}，批量查询一次完成（无 N+1）
        assertThat(response.roles())
            .extracting(AssignedRoleResponse::id, AssignedRoleResponse::name, AssignedRoleResponse::code)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(1L, "管理员", "ADMIN"),
                org.assertj.core.groups.Tuple.tuple(2L, "操作员", "OPERATOR")
            );
    }

    @Test
    void given_staleLinkToSoftDeletedRole_when_findById_then_rolesEmpty() {
        // Given —— 关联行无软删标志：角色已软删但 admin_user_role 关联仍在（残留关联）
        Long userId = 7L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        adminUser.addRole(9L);

        when(adminUserRepository.findById(userId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findByIdInAndDeletedFalse(Set.of(9L))).thenReturn(List.of());

        // When
        AdminUserResponse response = serviceWithRealMapper().findById(userId);

        // Then —— 已删角色不回显；且必须走显式过滤查询（findAllById 会把软删角色泄出来）
        assertThat(response.roles()).isEmpty();
        verify(adminRoleRepository).findByIdInAndDeletedFalse(Set.of(9L));
    }

    @Test
    void given_noRoles_when_findById_then_rolesEmptyArrayNotNull() {
        // Given
        Long userId = 7L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");

        when(adminUserRepository.findById(userId)).thenReturn(Optional.of(adminUser));

        // When
        AdminUserResponse response = serviceWithRealMapper().findById(userId);

        // Then —— 返回空数组而非 null（契约：未分配 → []）；无角色时不发起无谓查询
        assertThat(response.roles()).isNotNull().isEmpty();
        verify(adminRoleRepository, never()).findByIdInAndDeletedFalse(any());
    }

    @Test
    void given_weak_password_when_create_then_throw_application_exception() {
        // Given
        CreateAdminUserCommand command = new CreateAdminUserCommand("testuser", "weak", "测试用户", null, null, null);

        // When & Then
        assertThatThrownBy(() -> adminUserManagementAppService.create(command))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining(AdminMessage.PASSWORD_WEAK.message());
    }

    // ========== findAll：列表内联角色摘要（issue #17，批量取角色、无 N+1）==========

    @Test
    void given_usersWithRoles_when_findAll_then_rolesInlinedBatchSingleQuery() throws Exception {
        // Given —— 两用户共挂 3 个角色；gender 顺带验证透传
        AdminUser user1 = new AdminUser("listu1", "Test1234", "用户一");
        user1.setGender(AdminUserGender.MALE);
        AdminUser user2 = new AdminUser("listu2", "Test1234", "用户二");
        user1.addRole(1L);
        user1.addRole(2L);
        user2.addRole(3L);

        AdminRole role1 = new AdminRole("管理员", "ADMIN", "管理员", 1);
        AdminRole role2 = new AdminRole("操作员", "OPERATOR", "操作员", 2);
        AdminRole role3 = new AdminRole("客服", "SUPPORT", "客服", 3);
        setRoleId(role1, 1L);
        setRoleId(role2, 2L);
        setRoleId(role3, 3L);

        Pageable pageable = PageRequest.of(0, 20);
        when(adminUserRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(user1, user2), pageable, 2));
        when(adminRoleRepository.findByIdInAndDeletedFalse(Set.of(1L, 2L, 3L)))
            .thenReturn(List.of(role1, role2, role3));

        // When
        PageResponse<AdminUserResponse> page =
            serviceWithRealMapper().findAll(new AdminUserQuery(null, null, null, null, null), pageable);

        // Then —— 每行内联角色摘要裁剪投影 {id,name,code}；gender 透传；批量只查一次（无 N+1）
        AdminUserResponse r1 = page.items().stream()
            .filter(r -> "listu1".equals(r.username())).findFirst().orElseThrow();
        AdminUserResponse r2 = page.items().stream()
            .filter(r -> "listu2".equals(r.username())).findFirst().orElseThrow();
        assertThat(r1.roles()).extracting(AssignedRoleResponse::code)
            .containsExactlyInAnyOrder("ADMIN", "OPERATOR");
        assertThat(r2.roles()).extracting(AssignedRoleResponse::code).containsExactly("SUPPORT");
        assertThat(r1.gender()).isEqualTo(AdminUserGender.MALE);
        assertThat(r1.genderName()).isEqualTo("男");
        assertThat(r2.gender()).isNull();
        assertThat(r2.genderName()).isNull();
        verify(adminRoleRepository, times(1)).findByIdInAndDeletedFalse(any());
    }

    @Test
    void given_softDeletedRoleStaleLink_when_findAll_then_roleExcludedFromList() throws Exception {
        // Given —— 角色 2 已软删但关联残留（findByIdInAndDeletedFalse 不返回它）
        AdminUser user = new AdminUser("listu1", "Test1234", "用户一");
        user.addRole(1L);
        user.addRole(2L);
        AdminRole alive = new AdminRole("管理员", "ADMIN", "管理员", 1);
        setRoleId(alive, 1L);

        Pageable pageable = PageRequest.of(0, 20);
        when(adminUserRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(user), pageable, 1));
        when(adminRoleRepository.findByIdInAndDeletedFalse(Set.of(1L, 2L)))
            .thenReturn(List.of(alive)); // 仅存活角色

        // When
        PageResponse<AdminUserResponse> page =
            serviceWithRealMapper().findAll(new AdminUserQuery(null, null, null, null, null), pageable);

        // Then —— 软删角色不回显（与 findById 同语义）
        assertThat(page.items().get(0).roles()).extracting(AssignedRoleResponse::code)
            .containsExactly("ADMIN");
    }

    @Test
    void given_noRolesAcrossPage_when_findAll_then_rolesEmptyAndNoRoleQuery() {
        // Given —— 本页用户均无角色
        AdminUser user = new AdminUser("listu1", "Test1234", "用户一");
        Pageable pageable = PageRequest.of(0, 20);
        when(adminUserRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(user), pageable, 1));

        // When
        PageResponse<AdminUserResponse> page =
            serviceWithRealMapper().findAll(new AdminUserQuery(null, null, null, null, null), pageable);

        // Then —— 无角色时返回 []，且不发起无谓的角色批量查询
        assertThat(page.items().get(0).roles()).isNotNull().isEmpty();
        verify(adminRoleRepository, never()).findByIdInAndDeletedFalse(any());
    }

    // ========== gender CRUD 透传（issue #17）==========

    @Test
    void given_genderInCommand_when_create_then_genderSetOnSavedEntity() {
        // Given
        CreateAdminUserCommand command = new CreateAdminUserCommand(
            "gendertest", "Test1234", "性别用户", null, null, AdminUserGender.FEMALE);
        when(adminUserRepository.existsByUsername("gendertest")).thenReturn(false);
        when(passwordEncoderService.encodePassword("Test1234")).thenReturn("$2a$10$encoded");
        ArgumentCaptor<AdminUser> captor = ArgumentCaptor.forClass(AdminUser.class);
        when(adminUserRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        // When
        adminUserManagementAppService.create(command);

        // Then —— gender 透传到落库实体
        assertThat(captor.getValue().getGender()).isEqualTo(AdminUserGender.FEMALE);
    }

    @Test
    void given_genderInUpdateCommand_when_update_then_genderSet() {
        // Given
        Long userId = 1L;
        UpdateAdminUserCommand command = new UpdateAdminUserCommand(null, null, null, null, AdminUserGender.MALE);
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        when(adminUserRepository.findById(userId)).thenReturn(Optional.of(adminUser));

        // When
        adminUserManagementAppService.update(userId, command);

        // Then
        assertThat(adminUser.getGender()).isEqualTo(AdminUserGender.MALE);
        verify(adminUserRepository).save(adminUser);
    }
}
