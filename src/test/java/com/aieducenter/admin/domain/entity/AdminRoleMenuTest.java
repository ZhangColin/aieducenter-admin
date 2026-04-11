package com.aieducenter.admin.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * AdminRoleMenu 测试。
 */
class AdminRoleMenuTest {

    @Test
    void given_valid_input_when_create_then_success() {
        // When
        AdminRoleMenu roleMenu = new AdminRoleMenu(1L, 2L);

        // Then
        assertThat(roleMenu.getRoleId()).isEqualTo(1L);
        assertThat(roleMenu.getMenuId()).isEqualTo(2L);
    }

    @Test
    void given_null_menuId_when_create_then_throw_exception() {
        // When & Then
        assertThatThrownBy(() -> new AdminRoleMenu(1L, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void given_same_roleId_and_menuId_when_equals_then_true() {
        // Given
        AdminRoleMenu rm1 = new AdminRoleMenu(1L, 2L);
        AdminRoleMenu rm2 = new AdminRoleMenu(1L, 2L);

        // When & Then
        assertThat(rm1).isEqualTo(rm2);
        assertThat(rm1.hashCode()).isEqualTo(rm2.hashCode());
    }

    @Test
    void given_null_roleId_when_create_then_success() {
        // When
        AdminRoleMenu roleMenu = new AdminRoleMenu(null, 2L);

        // Then
        assertThat(roleMenu.getRoleId()).isNull();
        assertThat(roleMenu.getMenuId()).isEqualTo(2L);
    }
}
