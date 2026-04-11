package com.aieducenter.admin.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * AdminRolePermission 测试。
 */
class AdminRolePermissionTest {

    @Test
    void given_valid_input_when_create_then_success() {
        // When
        AdminRolePermission rp = new AdminRolePermission(1L, "admin:user:read", "用户管理-查看");

        // Then
        assertThat(rp.getRoleId()).isEqualTo(1L);
        assertThat(rp.getPermissionCode()).isEqualTo("admin:user:read");
        assertThat(rp.getPermissionName()).isEqualTo("用户管理-查看");
    }

    @Test
    void given_null_permissionCode_when_create_then_throw_exception() {
        // When & Then
        assertThatThrownBy(() -> new AdminRolePermission(1L, null, "用户管理-查看"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void given_null_permissionName_when_create_then_success() {
        // When
        AdminRolePermission rp = new AdminRolePermission(1L, "admin:user:read", null);

        // Then
        assertThat(rp.getPermissionName()).isNull();
    }

    @Test
    void given_null_roleId_when_create_then_success() {
        // When
        AdminRolePermission rp = new AdminRolePermission(null, "admin:user:read", "用户管理-查看");

        // Then
        assertThat(rp.getRoleId()).isNull();
        assertThat(rp.getPermissionCode()).isEqualTo("admin:user:read");
        assertThat(rp.getPermissionName()).isEqualTo("用户管理-查看");
    }
}
