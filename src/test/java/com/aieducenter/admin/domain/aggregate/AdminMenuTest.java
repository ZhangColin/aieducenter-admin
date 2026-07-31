package com.aieducenter.admin.domain.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aieducenter.admin.domain.entity.MenuQueryParam;
import com.aieducenter.admin.domain.enums.AdminUserStatus;
import com.aieducenter.admin.domain.enums.MenuIconType;
import com.aieducenter.admin.domain.enums.MenuType;

/**
 * AdminMenu 聚合根测试（Soybean 路由生成器模型）。
 */
class AdminMenuTest {

    @Test
    void given_full_soybean_fields_when_create_menu_then_all_fields_set() {
        // When
        AdminMenu menu = new AdminMenu(
                "用户管理", "manage_user", "/manage/user", "view.manage_user",
                "mdi:account", MenuIconType.ICONIFY, 60L, 1, MenuType.MENU,
                "route.manage_user", true, false, true, false,
                null, null, 2,
                List.of(new MenuQueryParam("id", "1")), AdminUserStatus.ACTIVE);

        // Then
        assertThat(menu.getMenuName()).isEqualTo("用户管理");
        assertThat(menu.getRouteName()).isEqualTo("manage_user");
        assertThat(menu.getRoutePath()).isEqualTo("/manage/user");
        assertThat(menu.getComponent()).isEqualTo("view.manage_user");
        assertThat(menu.getIcon()).isEqualTo("mdi:account");
        assertThat(menu.getIconType()).isEqualTo(MenuIconType.ICONIFY);
        assertThat(menu.getParentId()).isEqualTo(60L);
        assertThat(menu.getSortOrder()).isEqualTo(1);
        assertThat(menu.getMenuType()).isEqualTo(MenuType.MENU);
        assertThat(menu.getI18nKey()).isEqualTo("route.manage_user");
        assertThat(menu.isKeepAlive()).isTrue();
        assertThat(menu.isConstant()).isFalse();
        assertThat(menu.isMultiTab()).isTrue();
        assertThat(menu.isHideInMenu()).isFalse();
        assertThat(menu.getFixedIndexInTab()).isEqualTo(2);
        assertThat(menu.getQuery()).containsExactly(new MenuQueryParam("id", "1"));
        assertThat(menu.getStatus()).isEqualTo(AdminUserStatus.ACTIVE);
    }

    @Test
    void given_omitted_optionals_when_create_then_defaults_applied() {
        // When（iconType/menuType/status/query/sortOrder 全缺省）
        AdminMenu menu = menu("用户管理", "manage_user", "/manage/user", null, null, null);

        // Then
        assertThat(menu.getMenuType()).isEqualTo(MenuType.MENU);     // 缺省 menu
        assertThat(menu.getIconType()).isEqualTo(MenuIconType.ICONIFY); // 缺省 iconify
        assertThat(menu.getStatus()).isEqualTo(AdminUserStatus.ACTIVE);  // 缺省启用
        assertThat(menu.getSortOrder()).isZero();                    // 缺省 0
        assertThat(menu.isKeepAlive()).isFalse();
        assertThat(menu.isConstant()).isFalse();
        assertThat(menu.isMultiTab()).isFalse();
        assertThat(menu.isHideInMenu()).isFalse();
        assertThat(menu.getQuery()).isEmpty();                       // 缺省空集合
    }

    @Test
    void given_directory_type_when_create_then_menuType_directory() {
        AdminMenu dir = menu("系统管理", "manage", "/manage", null, 2, MenuType.DIRECTORY);

        assertThat(dir.getMenuType()).isEqualTo(MenuType.DIRECTORY);
        assertThat(dir.getRoutePath()).isEqualTo("/manage"); // directory 也可带 path（Soybean 语义，后端不强制）
    }

    @Test
    void given_root_menu_when_isRoot_then_true() {
        assertThat(menu("用户管理", "manage_user", "/manage/user", null, 1, MenuType.MENU).isRoot()).isTrue();
    }

    @Test
    void given_child_menu_when_isRoot_then_false() {
        assertThat(menu("用户列表", "manage_user_list", "/manage/user/list", 1L, 1, MenuType.MENU).isRoot()).isFalse();
    }

    @Test
    void given_menu_when_addChild_then_childAdded() {
        AdminMenu parent = menu("用户管理", "manage_user", "/manage/user", null, 1, MenuType.MENU);
        AdminMenu child = menu("详情", "manage_user_detail", "/manage/user/detail", 1L, 1, MenuType.MENU);

        parent.addChild(child);

        assertThat(parent.getChildren()).containsExactly(child);
    }

    @Test
    void given_null_children_when_setChildren_then_emptyList() {
        AdminMenu menu = menu("用户管理", "manage_user", "/manage/user", null, 1, MenuType.MENU);

        menu.setChildren(null);

        assertThat(menu.getChildren()).isNotNull().isEmpty();
    }

    @Test
    void given_menu_with_children_when_setChildren_then_childrenReplaced() {
        AdminMenu parent = menu("用户管理", "manage_user", "/manage/user", null, 1, MenuType.MENU);
        AdminMenu child1 = menu("列表", "manage_user_list", "/manage/user/list", 1L, 1, MenuType.MENU);
        AdminMenu child2 = menu("新增", "manage_user_add", "/manage/user/add", 1L, 2, MenuType.MENU);
        parent.addChild(child1);

        parent.setChildren(List.of(child2));

        assertThat(parent.getChildren()).containsExactly(child2);
    }

    @Test
    void given_setMenuType_when_null_then_default_menu() {
        AdminMenu menu = menu("用户管理", "manage_user", "/manage/user", null, 1, MenuType.DIRECTORY);

        menu.setMenuType(null);

        assertThat(menu.getMenuType()).isEqualTo(MenuType.MENU);
    }

    @Test
    void given_active_status_when_isEnabled_then_true() {
        AdminMenu menu = menu("用户管理", "manage_user", "/manage/user", null, 1, MenuType.MENU);
        assertThat(menu.isEnabled()).isTrue();
    }

    @Test
    void given_updateDetails_when_update_then_all_fields_replaced() {
        AdminMenu menu = menu("旧名", "old", "/old", null, 1, MenuType.MENU);

        menu.updateDetails(
                "新名", "manage_user", "/manage/user", "view.manage_user",
                "mdi:account", MenuIconType.LOCAL, 60L, 3, MenuType.MENU,
                "route.manage_user", true, true, false, true,
                "manage", "/ext", 1,
                List.of(new MenuQueryParam("tab", "detail")), AdminUserStatus.DISABLED);

        assertThat(menu.getMenuName()).isEqualTo("新名");
        assertThat(menu.getRouteName()).isEqualTo("manage_user");
        assertThat(menu.getRoutePath()).isEqualTo("/manage/user");
        assertThat(menu.getComponent()).isEqualTo("view.manage_user");
        assertThat(menu.getIconType()).isEqualTo(MenuIconType.LOCAL);
        assertThat(menu.getSortOrder()).isEqualTo(3);
        assertThat(menu.isKeepAlive()).isTrue();
        assertThat(menu.isConstant()).isTrue();
        assertThat(menu.isHideInMenu()).isTrue();
        assertThat(menu.getActiveMenu()).isEqualTo("manage");
        assertThat(menu.getHref()).isEqualTo("/ext");
        assertThat(menu.getStatus()).isEqualTo(AdminUserStatus.DISABLED);
        assertThat(menu.isEnabled()).isFalse();
    }

    // ========== helper ==========

    /** 便捷构造：仅指定关键字段，其余缺省（iconType/menuType/status 等交由聚合归一）。 */
    private static AdminMenu menu(String menuName, String routeName, String routePath,
                                  Long parentId, Integer sortOrder, MenuType menuType) {
        return new AdminMenu(menuName, routeName, routePath, null, null, null,
                parentId, sortOrder, menuType, null, false, false, false, false,
                null, null, null, null, null);
    }
}
