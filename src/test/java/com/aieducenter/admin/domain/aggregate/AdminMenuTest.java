package com.aieducenter.admin.domain.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aieducenter.admin.domain.enums.MenuType;
import com.aieducenter.admin.domain.error.AdminMessage;
import com.cartisan.core.exception.DomainException;

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

    // ========== T2: type↔path 不变量（聚合内强制） ==========

    @Test
    void given_menuType_without_path_when_construct_then_throw_admin014_3() {
        // MENU 类型必须有非空 path；null 或空白都拒绝
        assertThatThrownBy(() -> new AdminMenu("用户管理", null, "user", null, 1, MenuType.MENU))
                .isInstanceOf(DomainException.class)
                .extracting("codeMessage")
                .isEqualTo(AdminMessage.MENU_TYPE_PATH_MISMATCH);
        assertThatThrownBy(() -> new AdminMenu("用户管理", "   ", "user", null, 1, MenuType.MENU))
                .isInstanceOf(DomainException.class)
                .extracting("codeMessage")
                .isEqualTo(AdminMessage.MENU_TYPE_PATH_MISMATCH);
    }

    @Test
    void given_group_or_divider_when_construct_then_path_normalized_to_null() {
        // GROUP / DIVIDER 的 path 无意义，无论传入什么都归一为 null
        AdminMenu group = new AdminMenu("分组", "/ignored", null, null, 1, MenuType.GROUP);
        assertThat(group.getType()).isEqualTo(MenuType.GROUP);
        assertThat(group.getPath()).isNull();

        AdminMenu divider = new AdminMenu("--", null, null, null, 2, MenuType.DIVIDER);
        assertThat(divider.getType()).isEqualTo(MenuType.DIVIDER);
        assertThat(divider.getPath()).isNull();
    }

    @Test
    void given_omitted_type_when_construct_5arg_then_menu_with_path() {
        // 旧 5 参构造：type 缺省 → MENU，path 正常保留
        AdminMenu menu = new AdminMenu("用户管理", "/users", "user", null, 1);
        assertThat(menu.getType()).isEqualTo(MenuType.MENU);
        assertThat(menu.getPath()).isEqualTo("/users");
    }

    @Test
    void given_updateDetails_to_menu_without_path_when_update_then_throw() {
        AdminMenu menu = new AdminMenu("用户管理", "/users", "user", null, 1);

        assertThatThrownBy(() -> menu.updateDetails("用户管理", null, "user", null, 1, MenuType.MENU))
                .isInstanceOf(DomainException.class)
                .extracting("codeMessage")
                .isEqualTo(AdminMessage.MENU_TYPE_PATH_MISMATCH);
    }

    @Test
    void given_updateDetails_to_group_then_path_normalized_to_null() {
        AdminMenu menu = new AdminMenu("用户管理", "/users", "user", null, 1);

        menu.updateDetails("分组", "/whatever", "icon", null, 1, MenuType.GROUP);

        assertThat(menu.getType()).isEqualTo(MenuType.GROUP);
        assertThat(menu.getPath()).isNull();
    }
}
