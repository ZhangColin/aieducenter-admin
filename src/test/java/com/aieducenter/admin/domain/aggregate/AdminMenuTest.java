package com.aieducenter.admin.domain.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aieducenter.admin.domain.enums.MenuType;

/**
 * AdminMenu 聚合根测试。
 */
class AdminMenuTest {

    @Test
    void given_valid_input_when_create_menu_then_success() {
        // When
        AdminMenu menu = new AdminMenu("用户管理", "/users", "user", null, 1);

        // Then
        assertThat(menu.getName()).isEqualTo("用户管理");
        assertThat(menu.getPath()).isEqualTo("/users");
        assertThat(menu.getIcon()).isEqualTo("user");
        assertThat(menu.getParentId()).isNull();
        assertThat(menu.getSortOrder()).isEqualTo(1);
        assertThat(menu.getType()).isEqualTo(MenuType.MENU);
    }

    @Test
    void given_root_menu_when_isRoot_then_true() {
        // Given
        AdminMenu menu = new AdminMenu("用户管理", "/users", "user", null, 1);

        // When & Then
        assertThat(menu.isRoot()).isTrue();
    }

    @Test
    void given_child_menu_when_isRoot_then_false() {
        // Given
        AdminMenu menu = new AdminMenu("用户列表", "/users/list", "list", 1L, 1);

        // When & Then
        assertThat(menu.isRoot()).isFalse();
    }

    @Test
    void given_menu_when_addChild_then_childAdded() {
        // Given
        AdminMenu parent = new AdminMenu("用户管理", "/users", "user", null, 1);
        AdminMenu child = new AdminMenu("用户列表", "/users/list", "list", 1L, 1);

        // When
        parent.addChild(child);

        // Then
        assertThat(parent.getChildren()).containsExactly(child);
    }

    @Test
    void given_null_children_when_setChildren_then_emptyList() {
        // Given
        AdminMenu menu = new AdminMenu("用户管理", "/users", "user", null, 1);

        // When
        menu.setChildren(null);

        // Then
        assertThat(menu.getChildren()).isNotNull().isEmpty();
    }

    @Test
    void given_menuType_when_setType_then_typeUpdated() {
        // Given
        AdminMenu menu = new AdminMenu("用户管理", "/users", "user", null, 1);

        // When
        menu.setType(MenuType.GROUP);

        // Then
        assertThat(menu.getType()).isEqualTo(MenuType.GROUP);
    }

    @Test
    void given_null_type_when_setType_then_defaultToMenu() {
        // Given
        AdminMenu menu = new AdminMenu("用户管理", "/users", "user", null, 1);

        // When
        menu.setType(null);

        // Then
        assertThat(menu.getType()).isEqualTo(MenuType.MENU);
    }

    @Test
    void given_menu_with_children_when_setChildren_then_childrenReplaced() {
        // Given
        AdminMenu parent = new AdminMenu("用户管理", "/users", "user", null, 1);
        AdminMenu child1 = new AdminMenu("用户列表", "/users/list", "list", 1L, 1);
        AdminMenu child2 = new AdminMenu("用户添加", "/users/add", "add", 1L, 2);
        parent.addChild(child1);

        // When
        parent.setChildren(List.of(child2));

        // Then
        assertThat(parent.getChildren()).containsExactly(child2);
    }
}
