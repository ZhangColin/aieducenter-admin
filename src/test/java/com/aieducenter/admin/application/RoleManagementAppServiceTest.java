package com.aieducenter.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aieducenter.admin.application.dto.command.AssignMenusCommand;
import com.aieducenter.admin.application.dto.command.AssignPermissionsCommand;
import com.aieducenter.admin.application.dto.command.CreateRoleCommand;
import com.aieducenter.admin.application.dto.command.UpdateRoleCommand;
import com.aieducenter.admin.application.dto.query.AdminRoleQuery;
import com.aieducenter.admin.application.dto.response.RoleResponse;
import com.aieducenter.admin.application.mapper.AdminRoleMapper;
import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.aggregate.AdminMenu;
import com.aieducenter.admin.domain.error.AdminMessage;
import com.aieducenter.admin.domain.repository.AdminRoleRepository;
import com.aieducenter.admin.domain.repository.AdminMenuRepository;
import com.aieducenter.admin.constants.AdminScopes;
import com.cartisan.core.exception.DomainException;
import com.cartisan.data.jpa.specification.ConditionSpecifications;
import com.cartisan.security.permission.Permission;
import com.cartisan.security.permission.PermissionScanner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * RoleManagementAppService 测试。
 */
@ExtendWith(MockitoExtension.class)
class RoleManagementAppServiceTest {

    @Mock
    private AdminRoleRepository roleRepository;

    @Mock
    private AdminMenuRepository menuRepository;

    @Mock
    private AdminRoleMapper adminRoleMapper;

    @Mock
    private PermissionScanner permissionScanner;

    private RoleManagementAppService roleManagementAppService;

    @BeforeEach
    void setUp() {
        roleManagementAppService = new RoleManagementAppService(
            roleRepository,
            menuRepository,
            adminRoleMapper,
            permissionScanner
        );
    }

    // ========== findAll tests ==========

    @Test
    void given_no_filter_when_findAll_then_return_all_roles() {
        // Given
        AdminRoleQuery query = new AdminRoleQuery(null, null, null);
        Pageable pageable = PageRequest.of(0, 20);

        AdminRole role = new AdminRole("管理员", "ADMIN", "系统管理员", 1);
        Page<AdminRole> rolePage = new PageImpl<>(List.of(role));

        when(roleRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
            .thenReturn(rolePage);
        when(adminRoleMapper.convertList(List.of(role)))
            .thenReturn(List.of(new RoleResponse(1L, "管理员", "ADMIN", "系统管理员", 1, null, null)));

        // When
        var response = roleManagementAppService.findAll(query, pageable);

        // Then
        assertThat(response.items()).hasSize(1);
        assertThat(response.total()).isEqualTo(1);
    }

    // ========== create tests ==========

    @Test
    void given_valid_input_when_create_role_then_success() {
        // Given
        CreateRoleCommand command = new CreateRoleCommand("测试角色", "TEST", "测试角色描述", 1);

        when(roleRepository.findByCode("TEST")).thenReturn(Optional.empty());
        when(roleRepository.save(any(AdminRole.class))).thenAnswer(invocation -> {
            AdminRole saved = invocation.getArgument(0);
            try {
                java.lang.reflect.Field idField = AdminRole.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(saved, 1L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return saved;
        });

        // When
        Long roleId = roleManagementAppService.create(command);

        // Then
        assertThat(roleId).isEqualTo(1L);
        verify(roleRepository).save(any(AdminRole.class));
    }

    @Test
    void given_duplicate_code_when_create_role_then_throw_exception() {
        // Given
        CreateRoleCommand command = new CreateRoleCommand("测试角色", "ADMIN", "测试角色描述", 1);

        when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(new AdminRole("管理员", "ADMIN", "系统管理员", 1)));

        // When & Then
        assertThatThrownBy(() -> roleManagementAppService.create(command))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(AdminMessage.ROLE_CODE_ALREADY_EXISTS.message());
    }

    // ========== update tests ==========

    @Test
    void given_valid_input_when_update_role_then_success() {
        // Given
        Long roleId = 1L;
        UpdateRoleCommand command = new UpdateRoleCommand("新角色名", "NEW_CODE", "新描述", 2);
        AdminRole existingRole = new AdminRole("旧角色名", "OLD_CODE", "旧描述", 1);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(existingRole));
        when(roleRepository.findByCode("NEW_CODE")).thenReturn(Optional.empty());

        // When
        roleManagementAppService.update(roleId, command);

        // Then
        assertThat(existingRole.getName()).isEqualTo("新角色名");
        assertThat(existingRole.getCode()).isEqualTo("NEW_CODE");
        assertThat(existingRole.getDescription()).isEqualTo("新描述");
        assertThat(existingRole.getSortOrder()).isEqualTo(2);
        verify(roleRepository).save(existingRole);
    }

    @Test
    void given_nonexistent_role_when_update_then_throw_exception() {
        // Given
        Long roleId = 999L;
        UpdateRoleCommand command = new UpdateRoleCommand("新角色名", "NEW_CODE", "新描述", 1);

        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> roleManagementAppService.update(roleId, command))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(AdminMessage.ROLE_NOT_FOUND.message());
    }

    @Test
    void given_duplicate_code_when_update_role_then_throw_exception() {
        // Given
        Long roleId = 1L;
        UpdateRoleCommand command = new UpdateRoleCommand("新角色名", "EXISTING_CODE", "新描述", 1);
        AdminRole existingRole = new AdminRole("旧角色名", "OLD_CODE", "旧描述", 1);
        AdminRole otherRole = new AdminRole("其他角色", "EXISTING_CODE", "其他", 2);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(existingRole));
        when(roleRepository.findByCode("EXISTING_CODE")).thenReturn(Optional.of(otherRole));

        // When & Then
        assertThatThrownBy(() -> roleManagementAppService.update(roleId, command))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(AdminMessage.ROLE_CODE_ALREADY_EXISTS.message());
    }

    @Test
    void given_same_code_when_update_role_then_success() {
        // Given
        Long roleId = 1L;
        UpdateRoleCommand command = new UpdateRoleCommand("新角色名", "OLD_CODE", "新描述", 1);
        AdminRole existingRole = new AdminRole("旧角色名", "OLD_CODE", "旧描述", 1);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(existingRole));

        // When
        roleManagementAppService.update(roleId, command);

        // Then
        assertThat(existingRole.getName()).isEqualTo("新角色名");
        verify(roleRepository).save(existingRole);
    }

    // ========== delete tests ==========

    @Test
    void given_existing_role_when_delete_then_success() {
        // Given
        Long roleId = 1L;
        AdminRole role = new AdminRole("测试角色", "TEST", "测试", 1);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepository.isUsedByAnyAdmin(roleId)).thenReturn(false);

        // When
        roleManagementAppService.delete(roleId);

        // Then
        verify(roleRepository).delete(role);
    }

    @Test
    void given_nonexistent_role_when_delete_then_throw_exception() {
        // Given
        Long roleId = 999L;

        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> roleManagementAppService.delete(roleId))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(AdminMessage.ROLE_NOT_FOUND.message());
    }

    @Test
    void given_super_admin_role_when_delete_then_throw_exception() {
        // Given
        Long roleId = 1L;
        AdminRole superAdminRole = new AdminRole("超级管理员", "SUPER_ADMIN", "超级管理员", 0);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(superAdminRole));

        // When & Then
        assertThatThrownBy(() -> roleManagementAppService.delete(roleId))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(AdminMessage.SUPER_ADMIN_CANNOT_DELETE.message());
    }

    @Test
    void given_role_in_use_when_delete_then_throw_exception() {
        // Given
        Long roleId = 1L;
        AdminRole role = new AdminRole("测试角色", "TEST", "测试", 1);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepository.isUsedByAnyAdmin(roleId)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> roleManagementAppService.delete(roleId))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(AdminMessage.ROLE_IN_USE.message());
    }

    // ========== assignMenus tests ==========

    @Test
    void given_valid_menus_when_assignMenus_then_success() throws Exception {
        // Given
        Long roleId = 1L;
        AssignMenusCommand command = new AssignMenusCommand(List.of(1L, 2L, 3L));
        AdminRole role = new AdminRole("测试角色", "TEST", "测试", 1);

        AdminMenu menu1 = new AdminMenu("菜单1", "/menu1", "icon1", null, 1);
        AdminMenu menu2 = new AdminMenu("菜单2", "/menu2", "icon2", null, 1);
        AdminMenu menu3 = new AdminMenu("菜单3", "/menu3", "icon3", null, 1);

        // Set IDs using reflection
        java.lang.reflect.Field idField = AdminMenu.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(menu1, 1L);
        idField.set(menu2, 2L);
        idField.set(menu3, 3L);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(menuRepository.findAllById(command.menuIds())).thenReturn(List.of(menu1, menu2, menu3));

        // When
        roleManagementAppService.assignMenus(roleId, command);

        // Then
        assertThat(role.getMenuIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
        verify(roleRepository).save(role);
    }

    @Test
    void given_nonexistent_role_when_assignMenus_then_throw_exception() {
        // Given
        Long roleId = 999L;
        AssignMenusCommand command = new AssignMenusCommand(List.of(1L));

        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> roleManagementAppService.assignMenus(roleId, command))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(AdminMessage.ROLE_NOT_FOUND.message());
    }

    @Test
    void given_nonexistent_menu_when_assignMenus_then_throw_exception() throws Exception {
        // Given
        Long roleId = 1L;
        AssignMenusCommand command = new AssignMenusCommand(List.of(1L, 999L));
        AdminRole role = new AdminRole("测试角色", "TEST", "测试", 1);

        AdminMenu menu1 = new AdminMenu("菜单1", "/menu1", "icon1", null, 1);

        // Set ID using reflection
        java.lang.reflect.Field idField = AdminMenu.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(menu1, 1L);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(menuRepository.findAllById(command.menuIds())).thenReturn(List.of(menu1)); // Only menu1 exists

        // When & Then
        assertThatThrownBy(() -> roleManagementAppService.assignMenus(roleId, command))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(AdminMessage.MENU_NOT_FOUND.message());
    }

    // ========== assignPermissions tests ==========

    @Test
    void given_valid_permissions_when_assignPermissions_then_success() {
        // Given
        Long roleId = 1L;
        AssignPermissionsCommand command = new AssignPermissionsCommand(
            List.of("admin:user:read", "admin:user:write")
        );
        AdminRole role = new AdminRole("测试角色", "TEST", "测试", 1);

        // Create mock Permission objects
        var perm1 = org.mockito.Mockito.mock(com.cartisan.security.permission.Permission.class);
        org.mockito.Mockito.lenient().when(perm1.code()).thenReturn("admin:user:read");
        org.mockito.Mockito.lenient().when(perm1.scope()).thenReturn(AdminScopes.ADMIN);

        var perm2 = org.mockito.Mockito.mock(com.cartisan.security.permission.Permission.class);
        org.mockito.Mockito.lenient().when(perm2.code()).thenReturn("admin:user:write");
        org.mockito.Mockito.lenient().when(perm2.scope()).thenReturn(AdminScopes.ADMIN);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(permissionScanner.scanByScope(AdminScopes.ADMIN))
            .thenReturn(List.of(perm1, perm2));

        // When
        roleManagementAppService.assignPermissions(roleId, command);

        // Then
        assertThat(role.getPermissionCodes()).containsExactlyInAnyOrder("admin:user:read", "admin:user:write");
        verify(roleRepository).save(role);
    }

    @Test
    void given_nonexistent_role_when_assignPermissions_then_throw_exception() {
        // Given
        Long roleId = 999L;
        AssignPermissionsCommand command = new AssignPermissionsCommand(List.of("admin:user:read"));

        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> roleManagementAppService.assignPermissions(roleId, command))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(AdminMessage.ROLE_NOT_FOUND.message());
    }

    @Test
    void given_invalid_permission_code_when_assignPermissions_then_throw_exception() {
        // Given
        Long roleId = 1L;
        AssignPermissionsCommand command = new AssignPermissionsCommand(
            List.of("invalid:permission:code")
        );
        AdminRole role = new AdminRole("测试角色", "TEST", "测试", 1);

        // Create mock Permission object
        var perm = org.mockito.Mockito.mock(com.cartisan.security.permission.Permission.class);
        org.mockito.Mockito.lenient().when(perm.code()).thenReturn("admin:user:read");
        org.mockito.Mockito.lenient().when(perm.scope()).thenReturn(AdminScopes.ADMIN);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(permissionScanner.scanByScope(AdminScopes.ADMIN))
            .thenReturn(List.of(perm));

        // When & Then
        assertThatThrownBy(() -> roleManagementAppService.assignPermissions(roleId, command))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(AdminMessage.PERMISSION_NOT_FOUND.message());
    }

    // ========== findById tests ==========

    @Test
    void given_existing_role_when_findById_then_return_role() {
        // Given
        Long roleId = 1L;
        AdminRole role = new AdminRole("管理员", "ADMIN", "系统管理员", 1);
        RoleResponse response = new RoleResponse(1L, "管理员", "ADMIN", "系统管理员", 1, null, null);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(adminRoleMapper.convert(role)).thenReturn(response);

        // When
        RoleResponse result = roleManagementAppService.findById(roleId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("管理员");
    }

    @Test
    void given_nonexistent_role_when_findById_then_throw_exception() {
        // Given
        Long roleId = 999L;

        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> roleManagementAppService.findById(roleId))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(AdminMessage.ROLE_NOT_FOUND.message());
    }
}
