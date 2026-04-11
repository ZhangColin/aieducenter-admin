package com.aieducenter.admin.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * AdminUserRole 实体测试。
 */
class AdminUserRoleTest {

    @Test
    void given_valid_input_when_create_then_success() {
        // When
        AdminUserRole userRole = new AdminUserRole(1L, 2L);

        // Then
        assertThat(userRole.getAdminId()).isEqualTo(1L);
        assertThat(userRole.getRoleId()).isEqualTo(2L);
    }

    @Test
    void given_null_adminId_when_create_then_success() {
        // When
        AdminUserRole userRole = new AdminUserRole(null, 2L);

        // Then
        assertThat(userRole.getAdminId()).isNull();
        assertThat(userRole.getRoleId()).isEqualTo(2L);
    }

    @Test
    void given_null_roleId_when_create_then_success() {
        // When
        AdminUserRole userRole = new AdminUserRole(1L, null);

        // Then
        assertThat(userRole.getAdminId()).isEqualTo(1L);
        assertThat(userRole.getRoleId()).isNull();
    }

    @Test
    void given_same_adminId_and_roleId_when_equals_then_true() {
        // Given
        AdminUserRole ur1 = new AdminUserRole(1L, 2L);
        AdminUserRole ur2 = new AdminUserRole(1L, 2L);

        // When & Then
        assertThat(ur1).isEqualTo(ur2);
    }

    @Test
    void given_different_adminId_when_equals_then_false() {
        // Given
        AdminUserRole ur1 = new AdminUserRole(1L, 2L);
        AdminUserRole ur2 = new AdminUserRole(3L, 2L);

        // When & Then
        assertThat(ur1).isNotEqualTo(ur2);
    }

    @Test
    void given_different_roleId_when_equals_then_false() {
        // Given
        AdminUserRole ur1 = new AdminUserRole(1L, 2L);
        AdminUserRole ur2 = new AdminUserRole(1L, 3L);

        // When & Then
        assertThat(ur1).isNotEqualTo(ur2);
    }

    @Test
    void given_null_adminId_when_equals_then_false() {
        // Given
        AdminUserRole ur1 = new AdminUserRole(1L, 2L);
        AdminUserRole ur2 = new AdminUserRole(null, 2L);

        // When & Then
        assertThat(ur1).isNotEqualTo(ur2);
    }

    @Test
    void given_null_roleId_when_equals_then_false() {
        // Given
        AdminUserRole ur1 = new AdminUserRole(1L, 2L);
        AdminUserRole ur2 = new AdminUserRole(1L, null);

        // When & Then
        assertThat(ur1).isNotEqualTo(ur2);
    }

    @Test
    void given_null_both_when_equals_then_true() {
        // Given
        AdminUserRole ur1 = new AdminUserRole(null, null);
        AdminUserRole ur2 = new AdminUserRole(null, null);

        // When & Then
        assertThat(ur1).isEqualTo(ur2);
    }

    @Test
    void given_same_values_when_hashCode_then_same() {
        // Given
        AdminUserRole ur1 = new AdminUserRole(1L, 2L);
        AdminUserRole ur2 = new AdminUserRole(1L, 2L);

        // When & Then
        assertThat(ur1.hashCode()).isEqualTo(ur2.hashCode());
    }

    @Test
    void given_different_values_when_hashCode_then_different() {
        // Given
        AdminUserRole ur1 = new AdminUserRole(1L, 2L);
        AdminUserRole ur2 = new AdminUserRole(3L, 4L);

        // When & Then
        assertThat(ur1.hashCode()).isNotEqualTo(ur2.hashCode());
    }
}
