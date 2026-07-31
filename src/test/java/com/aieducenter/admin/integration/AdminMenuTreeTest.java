package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.admin.application.MenuManagementAppService;
import com.aieducenter.admin.application.dto.command.CreateMenuCommand;
import com.aieducenter.admin.application.dto.response.MenuResponse;
import com.aieducenter.admin.domain.enums.MenuType;
import com.aieducenter.admin.domain.repository.AdminMenuRepository;
import com.cartisan.core.exception.DomainException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AdminMenu 树形结构集成测试（Soybean directory/menu 模型）。
 *
 * <p>使用真实数据库测试菜单树的层级关系、排序、过滤与删除规则。</p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class AdminMenuTreeTest {

    @Autowired
    private MenuManagementAppService menuManagementAppService;

    @Autowired
    private AdminMenuRepository menuRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        menuRepository.deleteAllInBatch();
    }

    @Test
    void given_menu_hierarchy_when_query_tree_then_return_correct_structure() {
        // Given: directory(manage) → menu(manage_user)
        Long dirId = menuManagementAppService.create(
                dir("系统管理", "manage", "/manage", null, 1));
        Long leafId = menuManagementAppService.create(
                menu("用户管理", "manage_user", "/manage/user", dirId, 1));

        // When
        List<MenuResponse> menuTree = menuManagementAppService.findTree();

        // Then
        assertThat(menuTree).hasSize(1);
        MenuResponse directory = menuTree.get(0);
        assertThat(directory.id()).isEqualTo(dirId);
        assertThat(directory.menuName()).isEqualTo("系统管理");
        assertThat(directory.menuType()).isEqualTo(MenuType.DIRECTORY);
        assertThat(directory.parentId()).isNull();
        assertThat(directory.children()).hasSize(1);

        MenuResponse leaf = directory.children().get(0);
        assertThat(leaf.id()).isEqualTo(leafId);
        assertThat(leaf.menuName()).isEqualTo("用户管理");
        assertThat(leaf.menuType()).isEqualTo(MenuType.MENU);
        assertThat(leaf.parentId()).isEqualTo(dirId);
        assertThat(leaf.children()).isEmpty();
    }

    @Test
    void given_menuResponse_when_serialize_then_menuType_is_integer_code() throws Exception {
        // BaseEnumSerializer：directory=1 / menu=2
        MenuResponse directory = resp(1L, "系统管理", MenuType.DIRECTORY);
        MenuResponse leaf = resp(2L, "用户管理", MenuType.MENU);

        assertThat(objectMapper.writeValueAsString(directory)).contains("\"menuType\":1");
        assertThat(objectMapper.writeValueAsString(leaf)).contains("\"menuType\":2");
    }

    @Test
    void given_menus_with_sort_order_when_query_tree_then_children_sorted() {
        Long rootId = menuManagementAppService.create(dir("系统管理", "manage", "/manage", null, 1));

        Long aId = menuManagementAppService.create(menu("菜单A", "a", "/a", rootId, 3));
        Long bId = menuManagementAppService.create(menu("菜单B", "b", "/b", rootId, 1));
        Long cId = menuManagementAppService.create(menu("菜单C", "c", "/c", rootId, 2));

        List<MenuResponse> menuTree = menuManagementAppService.findTree();

        assertThat(menuTree).hasSize(1);
        MenuResponse root = menuTree.get(0);
        assertThat(root.children()).extracting(MenuResponse::id).containsExactly(bId, cId, aId); // 1,2,3
    }

    @Test
    void given_parent_menu_with_children_when_delete_then_fail() {
        Long parentId = menuManagementAppService.create(dir("系统管理", "manage", "/manage", null, 1));
        menuManagementAppService.create(menu("用户管理", "manage_user", "/manage/user", parentId, 1));

        assertThatThrownBy(() -> menuManagementAppService.delete(parentId))
                .isInstanceOf(DomainException.class);

        assertThat(menuRepository.existsById(parentId)).isTrue();
    }

    @Test
    void given_menu_tree_when_filter_by_ids_then_return_filtered_tree() {
        Long dir1Id = menuManagementAppService.create(dir("系统管理", "manage", "/manage", null, 1));
        Long leafId = menuManagementAppService.create(menu("用户管理", "manage_user", "/manage/user", dir1Id, 1));
        Long otherId = menuManagementAppService.create(menu("其它", "other", "/other", null, 2));

        List<MenuResponse> filteredTree = menuManagementAppService.findTree(java.util.Set.of(dir1Id, leafId));

        assertThat(filteredTree).hasSize(1);
        assertThat(filteredTree.get(0).id()).isEqualTo(dir1Id);
        assertThat(filteredTree.get(0).children()).hasSize(1);
        assertThat(filteredTree.get(0).children().get(0).id()).isEqualTo(leafId);
        assertThat(filteredTree).noneMatch(m -> m.id().equals(otherId));
    }

    @Test
    void given_child_assigned_but_parent_not_when_filter_then_ancestor_chain_completed() {
        Long dirId = menuManagementAppService.create(dir("系统管理", "manage", "/manage", null, 1));
        Long leafId = menuManagementAppService.create(menu("用户管理", "manage_user", "/manage/user", dirId, 1));

        // 只分配子（父未分配）→ 祖先链补全，父目录自动补回
        List<MenuResponse> filteredTree = menuManagementAppService.findTree(java.util.Set.of(leafId));

        assertThat(filteredTree).hasSize(1);
        assertThat(filteredTree.get(0).id()).isEqualTo(dirId);
        assertThat(filteredTree.get(0).children()).hasSize(1);
        assertThat(filteredTree.get(0).children().get(0).id()).isEqualTo(leafId);
    }

    @Test
    void given_empty_directory_assigned_when_filter_then_trimmed() {
        // 空 directory（无可见子）在消费侧裁掉
        Long dirId = menuManagementAppService.create(dir("空目录", "empty", "/empty", null, 1));

        List<MenuResponse> tree = menuManagementAppService.findTree(java.util.Set.of(dirId));

        assertThat(tree).isEmpty();
    }

    @Test
    void given_admin_view_when_findTree_then_empty_directory_kept() {
        // 管理视图（menuIds=null）不裁剪，空 directory 保留
        menuManagementAppService.create(dir("空目录", "empty", "/empty", null, 1));

        List<MenuResponse> tree = menuManagementAppService.findTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).menuType()).isEqualTo(MenuType.DIRECTORY);
    }

    // ========== helper ==========

    private static CreateMenuCommand dir(String menuName, String routeName, String routePath,
                                         Long parentId, int sortOrder) {
        return new CreateMenuCommand(menuName, routeName, routePath, "layout.base", null, null,
                parentId, sortOrder, MenuType.DIRECTORY, null, false, false, false, false,
                null, null, null, null, null);
    }

    private static CreateMenuCommand menu(String menuName, String routeName, String routePath,
                                          Long parentId, int sortOrder) {
        return new CreateMenuCommand(menuName, routeName, routePath, "view.page", null, null,
                parentId, sortOrder, MenuType.MENU, null, false, false, false, false,
                null, null, null, null, null);
    }

    private static MenuResponse resp(Long id, String menuName, MenuType menuType) {
        return new MenuResponse(
                id, menuName, "route_name", "/route", null, null, null, null, null,
                menuType, null, false, false, false, false, null, null, null,
                null, null, null, null, null);
    }
}
