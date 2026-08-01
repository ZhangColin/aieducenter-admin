package com.aieducenter.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aieducenter.admin.application.dto.response.MenuResponse;
import com.aieducenter.admin.application.dto.response.MyMenusResponse;
import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.aieducenter.admin.domain.enums.AdminRoleStatus;
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
        when(adminRoleRepository.findByIdInAndStatusAndDeletedFalse(Set.of(1L), AdminRoleStatus.ENABLED)).thenReturn(List.of(superAdminRole));

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
        when(adminRoleRepository.findByIdInAndStatusAndDeletedFalse(Set.of(1L, 2L), AdminRoleStatus.ENABLED)).thenReturn(List.of(role1, role2));

        // When
        List<String> permissions = adminUserPermissionAppService.getPermissions(adminId);

        // Then
        assertThat(permissions).containsExactlyInAnyOrder("admin:user:read", "admin:user:write", "admin:role:read");
    }

    @Test
    void given_onlyDisabledRoles_when_getPermissions_then_return_empty_list() {
        // 禁用角色在汇总聚合中视为不存在（issue #21）：启用查询返回空 → 权限聚合为空
        // Given
        Long adminId = 1L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        adminUser.addRole(1L);

        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findByIdInAndStatusAndDeletedFalse(Set.of(1L), AdminRoleStatus.ENABLED)).thenReturn(List.of());

        // When
        List<String> permissions = adminUserPermissionAppService.getPermissions(adminId);

        // Then
        assertThat(permissions).isEmpty();
        verify(adminRoleRepository).findByIdInAndStatusAndDeletedFalse(Set.of(1L), AdminRoleStatus.ENABLED);
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
        when(adminRoleRepository.findByIdInAndStatusAndDeletedFalse(Set.of(1L), AdminRoleStatus.ENABLED)).thenReturn(List.of(superAdminRole));

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
        when(adminRoleRepository.findByIdInAndStatusAndDeletedFalse(Set.of(1L, 2L), AdminRoleStatus.ENABLED)).thenReturn(List.of(role1, role2));

        // When
        List<String> roleCodes = adminUserPermissionAppService.getRoleCodes(adminId);

        // Then
        assertThat(roleCodes).containsExactlyInAnyOrder("ADMIN", "OPERATOR");
    }

    @Test
    void given_onlyDisabledRoles_when_getRoleCodes_then_return_empty_list() {
        // 禁用角色在汇总聚合中视为不存在（issue #21）：启用查询返回空 → 角色码聚合为空
        // Given
        Long adminId = 1L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        adminUser.addRole(1L);

        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findByIdInAndStatusAndDeletedFalse(Set.of(1L), AdminRoleStatus.ENABLED)).thenReturn(List.of());

        // When
        List<String> roleCodes = adminUserPermissionAppService.getRoleCodes(adminId);

        // Then
        assertThat(roleCodes).isEmpty();
        verify(adminRoleRepository).findByIdInAndStatusAndDeletedFalse(Set.of(1L), AdminRoleStatus.ENABLED);
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
        when(adminRoleRepository.findByIdInAndStatusAndDeletedFalse(Set.of(1L), AdminRoleStatus.ENABLED)).thenReturn(List.of(role1));
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
        when(adminRoleRepository.findByIdInAndStatusAndDeletedFalse(Set.of(1L), AdminRoleStatus.ENABLED)).thenReturn(List.of(role1));

        // When
        List<MenuResponse> menus = adminUserPermissionAppService.getMenus(adminId);

        // Then
        assertThat(menus).isEmpty();
    }

    @Test
    void given_onlyDisabledRoles_when_getMenus_then_return_empty_list() {
        // 禁用角色在汇总聚合中视为不存在（issue #21）：启用查询返回空 → 菜单聚合为空（不走 findTree）
        // Given
        Long adminId = 1L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        adminUser.addRole(1L);

        when(adminUserRepository.hasRole(adminId, AdminRole.SUPER_ADMIN_CODE)).thenReturn(false);
        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findByIdInAndStatusAndDeletedFalse(Set.of(1L), AdminRoleStatus.ENABLED)).thenReturn(List.of());

        // When
        List<MenuResponse> menus = adminUserPermissionAppService.getMenus(adminId);

        // Then
        assertThat(menus).isEmpty();
        verify(adminRoleRepository).findByIdInAndStatusAndDeletedFalse(Set.of(1L), AdminRoleStatus.ENABLED);
        verifyNoInteractions(menuManagementAppService);
    }

    // ========== getMyMenus tests（REQ-13-T2 /menus/my） ==========

    @Test
    void given_superAdmin_when_getMyMenus_then_fullVisibleTree_and_homeFromRoles() throws Exception {
        // 超管不受角色裁剪（findVisibleTree(null)），status 过滤下沉到组装层；home 同规则取自启用角色
        // Given
        Long adminId = 1L;
        AdminUser adminUser = new AdminUser("superadmin", "Test1234", "超管");
        adminUser.addRole(1L);

        AdminRole superAdminRole = new AdminRole("超级管理员", AdminRole.SUPER_ADMIN_CODE, "超管", 0);
        setRoleId(superAdminRole, 1L);
        superAdminRole.setHome("super_home");

        List<MenuResponse> allVisible = List.of(menuResp(1L, "用户管理", "/users", 1));

        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findByIdInAndStatusAndDeletedFalse(Set.of(1L), AdminRoleStatus.ENABLED))
                .thenReturn(List.of(superAdminRole));
        when(adminUserRepository.hasRole(adminId, AdminRole.SUPER_ADMIN_CODE)).thenReturn(true);
        when(menuManagementAppService.findVisibleTree(null)).thenReturn(allVisible);

        // When
        MyMenusResponse response = adminUserPermissionAppService.getMyMenus(adminId);

        // Then
        assertThat(response.home()).isEqualTo("super_home");
        assertThat(response.menus()).isEqualTo(allVisible);
        verify(menuManagementAppService).findVisibleTree(null);
    }

    @Test
    void given_user_when_getMyMenus_then_homeIsFirstNonBlank_bySortOrderThenId() throws Exception {
        // home：全部启用角色按 (sortOrder 升, id 升) 取第一个非空白——仓储返回无序，排序语义由应用层保证
        // Given
        Long adminId = 1L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        adminUser.addRole(1L);
        adminUser.addRole(2L);
        adminUser.addRole(3L);

        AdminRole roleA = new AdminRole("角色A", "ROLEA", null, 1); // home null
        setRoleId(roleA, 1L);
        AdminRole roleB = new AdminRole("角色B", "ROLEB", null, 2); // home 空白
        setRoleId(roleB, 2L);
        roleB.setHome("   ");
        AdminRole roleC = new AdminRole("角色C", "ROLEC", null, 3);
        setRoleId(roleC, 3L);
        roleC.setHome("home_c");
        roleC.addMenu(9L);

        List<MenuResponse> menus = List.of(menuResp(9L, "菜单", "/m", 1));

        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findByIdInAndStatusAndDeletedFalse(Set.of(1L, 2L, 3L), AdminRoleStatus.ENABLED))
                .thenReturn(List.of(roleC, roleA, roleB)); // 无序返回
        when(adminUserRepository.hasRole(adminId, AdminRole.SUPER_ADMIN_CODE)).thenReturn(false);
        when(menuManagementAppService.findVisibleTree(Set.of(9L))).thenReturn(menus);

        // When
        MyMenusResponse response = adminUserPermissionAppService.getMyMenus(adminId);

        // Then
        assertThat(response.home()).isEqualTo("home_c");
        assertThat(response.menus()).isEqualTo(menus);
    }

    @Test
    void given_sameSortOrder_when_getMyMenus_then_homeIdTiebreak() throws Exception {
        // Given
        Long adminId = 1L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        adminUser.addRole(9L);
        adminUser.addRole(3L);

        AdminRole roleX = new AdminRole("角色X", "ROLEX", null, 1);
        setRoleId(roleX, 9L);
        roleX.setHome("home_x");
        AdminRole roleY = new AdminRole("角色Y", "ROLEY", null, 1);
        setRoleId(roleY, 3L);
        roleY.setHome("home_y");

        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findByIdInAndStatusAndDeletedFalse(Set.of(9L, 3L), AdminRoleStatus.ENABLED))
                .thenReturn(List.of(roleX, roleY));
        when(adminUserRepository.hasRole(adminId, AdminRole.SUPER_ADMIN_CODE)).thenReturn(false);

        // When
        MyMenusResponse response = adminUserPermissionAppService.getMyMenus(adminId);

        // Then
        assertThat(response.home()).isEqualTo("home_y"); // 同 sortOrder，id 小者先
        assertThat(response.menus()).isEmpty();
    }

    @Test
    void given_allBlankHomes_when_getMyMenus_then_homeNull() throws Exception {
        // Given
        Long adminId = 1L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        adminUser.addRole(1L);
        adminUser.addRole(2L);

        AdminRole roleA = new AdminRole("角色A", "ROLEA", null, 1); // home null
        setRoleId(roleA, 1L);
        AdminRole roleB = new AdminRole("角色B", "ROLEB", null, 2);
        setRoleId(roleB, 2L);
        roleB.setHome("  ");

        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findByIdInAndStatusAndDeletedFalse(Set.of(1L, 2L), AdminRoleStatus.ENABLED))
                .thenReturn(List.of(roleA, roleB));
        when(adminUserRepository.hasRole(adminId, AdminRole.SUPER_ADMIN_CODE)).thenReturn(false);

        // When
        MyMenusResponse response = adminUserPermissionAppService.getMyMenus(adminId);

        // Then
        assertThat(response.home()).isNull();
        assertThat(response.menus()).isEmpty();
    }

    @Test
    void given_onlyDisabledRoles_when_getMyMenus_then_empty_and_noMenuQuery() {
        // 禁用角色在汇总聚合中视为不存在（issue #21）：menus/home 均剔除其贡献
        // Given
        Long adminId = 1L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        adminUser.addRole(1L);

        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findByIdInAndStatusAndDeletedFalse(Set.of(1L), AdminRoleStatus.ENABLED))
                .thenReturn(List.of());
        when(adminUserRepository.hasRole(adminId, AdminRole.SUPER_ADMIN_CODE)).thenReturn(false);

        // When
        MyMenusResponse response = adminUserPermissionAppService.getMyMenus(adminId);

        // Then
        assertThat(response.home()).isNull();
        assertThat(response.menus()).isEmpty();
        verifyNoInteractions(menuManagementAppService);
    }

    @Test
    void given_noRoles_when_getMyMenus_then_empty() {
        // Given
        Long adminId = 1L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");

        when(adminUserRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(adminUserRepository.hasRole(adminId, AdminRole.SUPER_ADMIN_CODE)).thenReturn(false);

        // When
        MyMenusResponse response = adminUserPermissionAppService.getMyMenus(adminId);

        // Then
        assertThat(response.home()).isNull();
        assertThat(response.menus()).isEmpty();
        verifyNoInteractions(menuManagementAppService);
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

    /** 反射回填角色 id（聚合 id 仅 JPA 生成，单测手动注入）。 */
    private static void setRoleId(AdminRole role, Long id) throws Exception {
        Field idField = AdminRole.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(role, id);
    }
}
