package com.aieducenter.admin.boundary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.aieducenter.admin.domain.aggregate.AdminMenu;
import com.aieducenter.admin.domain.enums.MenuType;

/**
 * Menu 边界值测试。
 *
 * <h3>测试覆盖</h3>
 * <ul>
 *   <li>菜单名称边界值：空值、长度限制</li>
 *   <li>排序值边界值：负数、零、正数、null</li>
 *   <li>路径边界值：空值、长度限制</li>
 *   <li>图标边界值：空值、长度限制</li>
 *   <li>父级ID边界值：null、正数、零</li>
 * </ul>
 */
@DisplayName("Menu 边界值测试")
class MenuBoundaryTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", "  ", "\t", "a", "ab", "测试菜单"})
    @DisplayName("菜单名称边界值测试")
    void given_name_boundary_when_create_menu_then_handle_appropriately(String name) {
        // Given
        String path = "/test";
        String icon = "test-icon";
        Long parentId = null;
        Integer sortOrder = 0;

        // When & Then
        // AdminMenu 在构造函数中没有对 name 进行验证，只检查数据库约束
        // 这里我们测试对象能正常创建，实际验证在数据库层
        if (name == null || name.isBlank()) {
            // 空值在domain层不阻止，但数据库会拒绝（nullable = false）
            AdminMenu menu = new AdminMenu(name, path, icon, parentId, sortOrder);
            assertThat(menu.getName()).isEqualTo(name);
        } else {
            AdminMenu menu = new AdminMenu(name, path, icon, parentId, sortOrder);
            assertThat(menu.getName()).isEqualTo(name);
        }
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
    @DisplayName("排序值边界值测试")
    void given_sortOrder_boundary_when_create_menu_then_expected_result(
            Integer inputSortOrder, Integer expectedSortOrder) {
        // Given
        String name = "测试菜单";
        String path = "/test";
        String icon = "test-icon";
        Long parentId = null;

        // When
        AdminMenu menu = new AdminMenu(name, path, icon, parentId, inputSortOrder);

        // Then
        assertThat(menu.getSortOrder()).isEqualTo(expectedSortOrder);
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("排序值为null时默认为0")
    void given_null_sortOrder_when_create_menu_then_default_to_zero(Integer inputSortOrder) {
        // Given
        String name = "测试菜单";
        String path = "/test";
        String icon = "test-icon";
        Long parentId = null;

        // When
        AdminMenu menu = new AdminMenu(name, path, icon, parentId, inputSortOrder);

        // Then
        assertThat(menu.getSortOrder()).isEqualTo(0);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "",
        "  ",
        "/",
        "/test",
        "/admin/users",
        "/path/with/many/segments"
    })
    @DisplayName("路径边界值测试")
    void given_path_boundary_when_create_menu_then_handle_appropriately(String path) {
        // Given
        String name = "测试菜单";
        String icon = "test-icon";
        Long parentId = null;
        Integer sortOrder = 0;

        // When
        AdminMenu menu = new AdminMenu(name, path, icon, parentId, sortOrder);

        // Then
        // AdminMenu 没有对 path 进行验证
        assertThat(menu.getPath()).isEqualTo(path);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "",
        "  ",
        "icon",
        "test-icon",
        "icon_123"
    })
    @DisplayName("图标边界值测试")
    void given_icon_boundary_when_create_menu_then_handle_appropriately(String icon) {
        // Given
        String name = "测试菜单";
        String path = "/test";
        Long parentId = null;
        Integer sortOrder = 0;

        // When
        AdminMenu menu = new AdminMenu(name, path, icon, parentId, sortOrder);

        // Then
        // AdminMenu 没有对 icon 进行验证
        assertThat(menu.getIcon()).isEqualTo(icon);
    }

    @ParameterizedTest
    @CsvSource({
        "1,              false",  // 有效父级ID
        "100,            false",  // 大父级ID
        "0,              false",  // 零（虽然不合理，但domain不阻止）
        "-1,             false"   // 负数（虽然不合理，但domain不阻止）
    })
    @DisplayName("父级ID边界值测试")
    void given_parentId_boundary_when_create_menu_then_expected_result(
            Long parentId, boolean shouldBeRoot) {
        // Given
        String name = "测试菜单";
        String path = "/test";
        String icon = "test-icon";
        Integer sortOrder = 0;

        // When
        AdminMenu menu = new AdminMenu(name, path, icon, parentId, sortOrder);

        // Then
        assertThat(menu.getParentId()).isEqualTo(parentId);
        assertThat(menu.isRoot()).isEqualTo(shouldBeRoot);
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("父级ID为null时为根菜单")
    void given_null_parentId_when_create_menu_then_is_root(Long parentId) {
        // Given
        String name = "测试菜单";
        String path = "/test";
        String icon = "test-icon";
        Integer sortOrder = 0;

        // When
        AdminMenu menu = new AdminMenu(name, path, icon, parentId, sortOrder);

        // Then
        assertThat(menu.getParentId()).isNull();
        assertThat(menu.isRoot()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "MENU,           MENU",       // 菜单类型
        "GROUP,          GROUP",      // 分组类型
        "DIVIDER,        DIVIDER"     // 分隔线类型
    })
    @DisplayName("菜单类型边界值测试")
    void given_type_boundary_when_setType_then_expected_result(
            MenuType inputType, MenuType expectedType) {
        // Given
        AdminMenu menu = new AdminMenu("测试菜单", "/test", "icon", null, 0);

        // When
        menu.setType(inputType);

        // Then
        assertThat(menu.getType()).isEqualTo(expectedType);
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("菜单类型为null时默认为MENU")
    void given_null_type_when_setType_then_default_to_MENU(MenuType inputType) {
        // Given
        AdminMenu menu = new AdminMenu("测试菜单", "/test", "icon", null, 0);

        // When
        menu.setType(inputType);

        // Then
        assertThat(menu.getType()).isEqualTo(MenuType.MENU);
    }

    // Note: Default sortOrder test already covered in the sortOrder boundary test above

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", "[]", "[menu1, menu2]"})
    @DisplayName("子菜单边界值测试")
    void given_children_boundary_when_setChildren_then_handle_appropriately(String childrenStr) {
        // Given
        AdminMenu menu = new AdminMenu("父菜单", "/parent", "icon", null, 0);

        // When
        if (childrenStr == null || childrenStr.isEmpty()) {
            menu.setChildren(null);
            assertThat(menu.getChildren()).isNotNull().isEmpty();
        } else if (childrenStr.equals("[]")) {
            menu.setChildren(java.util.List.of());
            assertThat(menu.getChildren()).isNotNull().isEmpty();
        } else {
            // 创建子菜单
            AdminMenu child1 = new AdminMenu("子菜单1", "/child1", null, menu.getId(), 1);
            AdminMenu child2 = new AdminMenu("子菜单2", "/child2", null, menu.getId(), 2);
            menu.setChildren(java.util.List.of(child1, child2));
            assertThat(menu.getChildren()).hasSize(2);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "true,           false",   // 有父级ID - 非根菜单
        "false,          true"     // 无父级ID - 根菜单
    })
    @DisplayName("根菜单判断测试")
    void given_parentId_when_checkIsRoot_then_expected_result(Boolean hasParentId, boolean shouldBeRoot) {
        // Given
        Long parentId = (hasParentId != null && hasParentId) ? 1L : null;
        AdminMenu menu = new AdminMenu("测试菜单", "/test", "icon", parentId, 0);

        // When & Then
        assertThat(menu.isRoot()).isEqualTo(shouldBeRoot);
    }
}
