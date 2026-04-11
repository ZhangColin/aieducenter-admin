package com.aieducenter.admin.domain.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * AdminRole 测试。
 */
class AdminRoleTest {

    @Test
    void given_valid_input_when_create_role_then_success() {
        // When
        AdminRole role = new AdminRole("管理员", "ADMIN", "系统管理员", 1);

        // Then
        assertThat(role.getName()).isEqualTo("管理员");
        assertThat(role.getCode()).isEqualTo("ADMIN");
        assertThat(role.getRoleMenus()).isEmpty();
        assertThat(role.getRolePermissions()).isEmpty();
    }

    @Test
    void given_role_when_add_menu_then_menuId_in_set() {
        // Given
        AdminRole role = new AdminRole("管理员", "ADMIN", "系统管理员", 1);
        role.addMenu(1L);
        role.addMenu(2L);

        // When & Then
        assertThat(role.getMenuIds()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void given_role_with_menus_when_clear_menus_then_empty() {
        // Given
        AdminRole role = new AdminRole("管理员", "ADMIN", "系统管理员", 1);
        role.addMenu(1L);
        role.addMenu(2L);

        // When
        role.clearMenus();

        // Then
        assertThat(role.getMenuIds()).isEmpty();
    }

    @Test
    void given_role_when_add_permission_then_permissionCode_in_set() {
        // Given
        AdminRole role = new AdminRole("管理员", "ADMIN", "系统管理员", 1);
        role.addPermission("admin:user:read", "用户管理-查看");
        role.addPermission("admin:user:write", "用户管理-编辑");

        // When & Then
        assertThat(role.getPermissionCodes()).containsExactlyInAnyOrder("admin:user:read", "admin:user:write");
    }

    @Test
    void given_role_with_permissions_when_clear_permissions_then_empty() {
        // Given
        AdminRole role = new AdminRole("管理员", "ADMIN", "系统管理员", 1);
        role.addPermission("admin:user:read", "用户管理-查看");

        // When
        role.clearPermissions();

        // Then
        assertThat(role.getPermissionCodes()).isEmpty();
    }

    @Test
    void given_super_admin_code_when_isSuperAdmin_then_true() {
        // Given
        AdminRole role = new AdminRole("超级管理员", "SUPER_ADMIN", "超级管理员", 0);

        // When & Then
        assertThat(role.isSuperAdmin()).isTrue();
    }

    @Test
    void given_normal_role_code_when_isSuperAdmin_then_false() {
        // Given
        AdminRole role = new AdminRole("管理员", "ADMIN", "系统管理员", 1);

        // When & Then
        assertThat(role.isSuperAdmin()).isFalse();
    }
}
