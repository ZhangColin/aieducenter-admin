package com.aieducenter.admin.boundary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.aieducenter.admin.domain.aggregate.AdminMenu;
import com.aieducenter.admin.domain.enums.MenuType;

/**
 * Menu 边界值测试（Soybean directory/menu 模型）。
 *
 * <h3>测试覆盖</h3>
 * <ul>
 *   <li>菜单名称边界值：空值、长度限制</li>
 *   <li>排序值边界值：负数、零、正数、null</li>
 *   <li>路由路径边界值：directory/menu 均原样存储（后端不强制 path 不变量，见 ADR-0004）</li>
 *   <li>图标边界值：空值、iconify id</li>
 *   <li>父级ID边界值：null、正数、零</li>
 *   <li>菜单类型：directory/menu 两值</li>
 * </ul>
 */
@DisplayName("Menu 边界值测试")
class MenuBoundaryTest {

    private static AdminMenu create(String menuName, String routePath, String icon,
                                    Long parentId, Integer sortOrder) {
        return new AdminMenu(menuName, "route_name", routePath, null, icon, null,
                parentId, sortOrder, null, null, false, false, false, false,
                null, null, null, null, null);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", "  ", "\t", "a", "ab", "测试菜单"})
    @DisplayName("菜单名称边界值：原样存储（非空校验在 DB/命令层）")
    void given_menuName_boundary_when_create_menu_then_stored(String menuName) {
        AdminMenu menu = create(menuName, "/test", "test-icon", null, 0);
        assertThat(menu.getMenuName()).isEqualTo(menuName);
    }

    @ParameterizedTest
    @CsvSource({
        "-100,           -100",   // 负数排序
        "-1,             -1",     // -1
        "0,              0",      // 零
        "1,              1",      // 正数
        "100,            100",    // 大正数
        "2147483647,     2147483647"   // Integer.MAX_VALUE
    })
    @DisplayName("排序值边界值：原样存储")
    void given_sortOrder_boundary_when_create_menu_then_expected_result(
            Integer inputSortOrder, Integer expectedSortOrder) {
        AdminMenu menu = create("测试菜单", "/test", "test-icon", null, inputSortOrder);
        assertThat(menu.getSortOrder()).isEqualTo(expectedSortOrder);
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("排序值为 null 时默认为 0")
    void given_null_sortOrder_when_create_menu_then_default_to_zero(Integer inputSortOrder) {
        AdminMenu menu = create("测试菜单", "/test", "test-icon", null, inputSortOrder);
        assertThat(menu.getSortOrder()).isEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/test", "/manage/user", "/path/with/many/segments"})
    @DisplayName("menu 路由路径边界值：原样存储（无 path 不变量）")
    void given_valid_routePath_for_menu_when_create_then_stored(String routePath) {
        AdminMenu menu = create("测试菜单", routePath, "icon", null, 0);
        assertThat(menu.getMenuType()).isEqualTo(MenuType.MENU);
        assertThat(menu.getRoutePath()).isEqualTo(routePath);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", "  "})
    @DisplayName("directory 空 routePath 也原样存储（后端不强制，Soybean directory 可带 path）")
    void given_blank_routePath_for_directory_when_create_then_stored(String routePath) {
        AdminMenu menu = new AdminMenu("目录", "manage", routePath, null, null, null,
                null, 0, MenuType.DIRECTORY, null, false, false, false, false,
                null, null, null, null, null);
        assertThat(menu.getMenuType()).isEqualTo(MenuType.DIRECTORY);
        assertThat(menu.getRoutePath()).isEqualTo(routePath);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", "  ", "mdi:account", "mdi:monitor-dashboard", "local-icon"})
    @DisplayName("图标边界值：原样存储")
    void given_icon_boundary_when_create_menu_then_stored(String icon) {
        AdminMenu menu = create("测试菜单", "/test", icon, null, 0);
        assertThat(menu.getIcon()).isEqualTo(icon);
    }

    @ParameterizedTest
    @CsvSource({
        "1,              false",  // 有效父级ID
        "100,            false",  // 大父级ID
        "0,              false",  // 零（domain 不阻止）
        "-1,             false"   // 负数（domain 不阻止）
    })
    @DisplayName("父级ID边界值")
    void given_parentId_boundary_when_create_menu_then_expected_result(
            Long parentId, boolean shouldBeRoot) {
        AdminMenu menu = create("测试菜单", "/test", "test-icon", parentId, 0);
        assertThat(menu.getParentId()).isEqualTo(parentId);
        assertThat(menu.isRoot()).isEqualTo(shouldBeRoot);
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("父级ID为 null 时为根菜单")
    void given_null_parentId_when_create_menu_then_is_root(Long parentId) {
        AdminMenu menu = create("测试菜单", "/test", "test-icon", parentId, 0);
        assertThat(menu.getParentId()).isNull();
        assertThat(menu.isRoot()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "DIRECTORY,      DIRECTORY",  // 目录
        "MENU,           MENU"        // 菜单
    })
    @DisplayName("菜单类型边界值：directory/menu")
    void given_menuType_boundary_when_setMenuType_then_expected_result(
            MenuType inputType, MenuType expectedType) {
        AdminMenu menu = create("测试菜单", "/test", "icon", null, 0);
        menu.setMenuType(inputType);
        assertThat(menu.getMenuType()).isEqualTo(expectedType);
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("菜单类型为 null 时默认为 MENU")
    void given_null_menuType_when_setMenuType_then_default_to_MENU(MenuType inputType) {
        AdminMenu menu = create("测试菜单", "/test", "icon", null, 0);
        menu.setMenuType(inputType);
        assertThat(menu.getMenuType()).isEqualTo(MenuType.MENU);
    }
}
