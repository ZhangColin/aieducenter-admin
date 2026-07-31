package com.aieducenter.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.aieducenter.admin.application.dto.response.MenuResponse;
import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.aieducenter.admin.domain.enums.MenuType;
import com.aieducenter.admin.domain.repository.AdminRoleRepository;
import com.aieducenter.admin.domain.repository.AdminUserRepository;

/**
 * AdminUserPermissionAppService 测试。
 */
@ExtendWith(MockitoExtension.class)
class AdminUserPermissionAppServiceTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private AdminRoleRepository adminRoleRepository;

    @Mock
    private MenuManagementAppService menuManagementAppService;

    private AdminUserPermissionAppService adminUserPermissionAppService;

    @BeforeEach
    void setUp() {
        adminUserPermissionAppService = new AdminUserPermissionAppService(
            adminUserRepository,
            adminRoleRepository,
            menuManagementAppService
        );
    }

    // ========== getPermissions tests ==========

    @Test
    void given_superAdminRole_when_getPermissions_then_aggregates_role_permissions() {
        // 超管不再短路返回空（Bug ② workaround 已移除）：与普通用户同路径，聚合其角色（含 SUPER_ADMIN）的权限
        // Given
        Long adminId = 1L;
        AdminUser adminUser = new AdminUser("superadmin", "Test1234", "超管");
        adminUser.addRole(1L);

        AdminRole superAdminRole = new AdminRole("超级管理员", AdminRole.SUPER_ADMIN_CODE, "超管", 0);
        superAdminRole.addPermission("admin:user:read", "用户管理-查看");

        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findAllById(Set.of(1L))).thenReturn(List.of(superAdminRole));

        // When
        List<String> permissions = adminUserPermissionAppService.getPermissions(adminId);

        // Then
        assertThat(permissions).containsExactly("admin:user:read");
    }

    @Test
    void given_user_with_roles_when_getPermissions_then_return_permission_codes() {
        // Given
        Long adminId = 1L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        adminUser.addRole(1L);
        adminUser.addRole(2L);

        AdminRole role1 = new AdminRole("管理员", "ADMIN", "管理员", 1);
        role1.addPermission("admin:user:read", "用户管理-查看");
        role1.addPermission("admin:user:write", "用户管理-编辑");

        AdminRole role2 = new AdminRole("操作员", "OPERATOR", "操作员", 2);
        role2.addPermission("admin:role:read", "角色管理-查看");

        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(role1, role2));

        // When
        List<String> permissions = adminUserPermissionAppService.getPermissions(adminId);

        // Then
        assertThat(permissions).containsExactlyInAnyOrder("admin:user:read", "admin:user:write", "admin:role:read");
    }

    @Test
    void given_user_with_no_roles_when_getPermissions_then_return_empty_list() {
        // Given
        Long adminId = 1L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");

        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));

        // When
        List<String> permissions = adminUserPermissionAppService.getPermissions(adminId);

        // Then
        assertThat(permissions).isEmpty();
    }

    // ========== getRoleCodes tests ==========

    @Test
    void given_superAdminRole_when_getRoleCodes_then_returns_super_admin_code() throws Exception {
        // 超管不再短路返回空（Bug ② workaround 已移除）：聚合角色编码，返回 ["SUPER_ADMIN"]
        // Given
        Long adminId = 1L;
        AdminUser adminUser = new AdminUser("superadmin", "Test1234", "超管");
        adminUser.addRole(1L);

        AdminRole superAdminRole = new AdminRole("超级管理员", AdminRole.SUPER_ADMIN_CODE, "超管", 0);
        java.lang.reflect.Field idField = AdminRole.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(superAdminRole, 1L);

        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findAllById(Set.of(1L))).thenReturn(List.of(superAdminRole));

        // When
        List<String> roleCodes = adminUserPermissionAppService.getRoleCodes(adminId);

        // Then
        assertThat(roleCodes).containsExactly(AdminRole.SUPER_ADMIN_CODE);
    }

    @Test
    void given_user_with_roles_when_getRoleCodes_then_return_role_codes() throws Exception {
        // Given
        Long adminId = 1L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        adminUser.addRole(1L);
        adminUser.addRole(2L);

        AdminRole role1 = new AdminRole("管理员", "ADMIN", "管理员", 1);
        AdminRole role2 = new AdminRole("操作员", "OPERATOR", "操作员", 2);

        // Set IDs using reflection
        java.lang.reflect.Field idField = AdminRole.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(role1, 1L);
        idField.set(role2, 2L);

        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(role1, role2));

        // When
        List<String> roleCodes = adminUserPermissionAppService.getRoleCodes(adminId);

        // Then
        assertThat(roleCodes).containsExactlyInAnyOrder("ADMIN", "OPERATOR");
    }

    @Test
    void given_user_with_no_roles_when_getRoleCodes_then_return_empty_list() {
        // Given
        Long adminId = 1L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");

        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));

        // When
        List<String> roleCodes = adminUserPermissionAppService.getRoleCodes(adminId);

        // Then
        assertThat(roleCodes).isEmpty();
    }

    // ========== getMenus tests ==========

    @Test
    void given_super_admin_when_getMenus_then_return_all_menus() {
        // Given
        Long adminId = 1L;
        List<MenuResponse> allMenus = List.of(
            menuResp(1L, "用户管理", "/users", 1),
            menuResp(2L, "角色管理", "/roles", 2)
        );

        when(adminUserRepository.hasRole(adminId, AdminRole.SUPER_ADMIN_CODE)).thenReturn(true);
        when(menuManagementAppService.findTree(null)).thenReturn(allMenus);

        // When
        List<MenuResponse> menus = adminUserPermissionAppService.getMenus(adminId);

        // Then
        assertThat(menus).hasSize(2);
        assertThat(menus).isEqualTo(allMenus);
        verify(menuManagementAppService).findTree(null);
    }

    @Test
    void given_user_with_roles_when_getMenus_then_return_filtered_menus() {
        // Given
        Long adminId = 1L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        adminUser.addRole(1L);

        AdminRole role1 = new AdminRole("管理员", "ADMIN", "管理员", 1);
        role1.addMenu(1L);
        role1.addMenu(2L);

        List<MenuResponse> filteredMenus = List.of(
            menuResp(1L, "用户管理", "/users", 1),
            menuResp(2L, "角色管理", "/roles", 2)
        );

        when(adminUserRepository.hasRole(adminId, AdminRole.SUPER_ADMIN_CODE)).thenReturn(false);
        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findAllById(Set.of(1L))).thenReturn(List.of(role1));
        when(menuManagementAppService.findTree(Set.of(1L, 2L))).thenReturn(filteredMenus);

        // When
        List<MenuResponse> menus = adminUserPermissionAppService.getMenus(adminId);

        // Then
        assertThat(menus).hasSize(2);
        verify(menuManagementAppService).findTree(Set.of(1L, 2L));
    }

    @Test
    void given_user_with_no_roles_when_getMenus_then_return_empty_list() {
        // Given
        Long adminId = 1L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");

        when(adminUserRepository.hasRole(adminId, AdminRole.SUPER_ADMIN_CODE)).thenReturn(false);
        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));

        // When
        List<MenuResponse> menus = adminUserPermissionAppService.getMenus(adminId);

        // Then
        assertThat(menus).isEmpty();
    }

    @Test
    void given_user_with_roles_but_no_menus_when_getMenus_then_return_empty_list() {
        // Given
        Long adminId = 1L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        adminUser.addRole(1L);

        AdminRole role1 = new AdminRole("管理员", "ADMIN", "管理员", 1);

        when(adminUserRepository.hasRole(adminId, AdminRole.SUPER_ADMIN_CODE)).thenReturn(false);
        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findAllById(Set.of(1L))).thenReturn(List.of(role1));

        // When
        List<MenuResponse> menus = adminUserPermissionAppService.getMenus(adminId);

        // Then
        assertThat(menus).isEmpty();
    }

    // ========== isSuperAdmin tests ==========

    @Test
    void given_super_admin_when_isSuperAdmin_then_return_true() {
        // Given
        Long adminId = 1L;
        when(adminUserRepository.hasRole(adminId, AdminRole.SUPER_ADMIN_CODE)).thenReturn(true);

        // When
        boolean result = adminUserPermissionAppService.isSuperAdmin(adminId);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void given_normal_user_when_isSuperAdmin_then_return_false() {
        // Given
        Long adminId = 1L;
        when(adminUserRepository.hasRole(adminId, AdminRole.SUPER_ADMIN_CODE)).thenReturn(false);

        // When
        boolean result = adminUserPermissionAppService.isSuperAdmin(adminId);

        // Then
        assertThat(result).isFalse();
    }

    /** 精简 MenuResponse（Soybean 必备字段，其余缺省）。 */
    private static MenuResponse menuResp(long id, String menuName, String routePath, int sortOrder) {
        return new MenuResponse(id, menuName, menuName, routePath, null, null, null, null, sortOrder,
                MenuType.MENU, null, false, false, false, false, null, null, null, null, null,
                null, null, null);
    }
}
