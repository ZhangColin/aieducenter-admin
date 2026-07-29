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
import com.aieducenter.admin.domain.aggregate.AdminMenu;
import com.aieducenter.admin.domain.enums.MenuType;
import com.aieducenter.admin.domain.repository.AdminMenuRepository;
import com.cartisan.core.exception.DomainException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AdminMenu 树形结构集成测试。
 *
 * <p>使用真实数据库测试菜单树的层级关系、排序和删除规则。</p>
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
        // Clean up after each test - hard delete all menus
        menuRepository.deleteAllInBatch();
    }

    @Test
    void given_menu_hierarchy_when_query_tree_then_return_correct_structure() {
        // Given: 创建 3 级菜单树
        // Level 1: 系统管理 (depth=0)
        Long level1Id = menuManagementAppService.create(
            new CreateMenuCommand("系统管理", "/system", "system", null, 1)
        );

        // Level 2: 用户管理 (系统管理的子菜单, depth=1)
        Long level2Id = menuManagementAppService.create(
            new CreateMenuCommand("用户管理", "/system/users", "user", level1Id, 1)
        );

        // When: 查询菜单树
        List<MenuResponse> menuTree = menuManagementAppService.findTree();

        // Then: 验证树形结构
        assertThat(menuTree).hasSize(1);

        MenuResponse level1 = menuTree.get(0);
        assertThat(level1.id()).isEqualTo(level1Id);
        assertThat(level1.name()).isEqualTo("系统管理");
        assertThat(level1.parentId()).isNull();
        assertThat(level1.children()).hasSize(1);

        MenuResponse level2 = level1.children().get(0);
        assertThat(level2.id()).isEqualTo(level2Id);
        assertThat(level2.name()).isEqualTo("用户管理");
        assertThat(level2.parentId()).isEqualTo(level1Id);
        assertThat(level2.children()).isEmpty();

        // T1: type 字段透传到响应，且与节点语义一致（默认创建 = MENU）
        assertThat(level1.type()).isEqualTo(MenuType.MENU);
        assertThat(level2.type()).isEqualTo(MenuType.MENU);
    }

    @Test
    void given_menuResponse_when_serialize_then_type_is_integer_code() throws Exception {
        // T1: type 经全局 BaseEnumSerializer 序列化为整数 code（1=MENU/2=GROUP/3=DIVIDER）
        MenuResponse menu = new MenuResponse(1L, "用户管理", "/users", "user", null, 1, MenuType.MENU, null);
        MenuResponse group = new MenuResponse(2L, "分组", null, null, null, 2, MenuType.GROUP, null);
        MenuResponse divider = new MenuResponse(3L, "--", null, null, null, 3, MenuType.DIVIDER, null);

        assertThat(objectMapper.writeValueAsString(menu)).contains("\"type\":1");
        assertThat(objectMapper.writeValueAsString(group)).contains("\"type\":2");
        assertThat(objectMapper.writeValueAsString(divider)).contains("\"type\":3");
    }

    @Test
    void given_menus_with_sort_order_when_query_tree_then_children_can_be_sorted() {
        // Given: 创建多个同级菜单，使用不同的 sortOrder
        Long rootId = menuManagementAppService.create(
            new CreateMenuCommand("系统管理", "/system", "system", null, 1)
        );

        // 创建子菜单，sortOrder 顺序为乱序
        Long menuAId = menuManagementAppService.create(
            new CreateMenuCommand("菜单A", "/a", "a", rootId, 1)
        );
        Long menuBId = menuManagementAppService.create(
            new CreateMenuCommand("菜单B", "/b", "b", rootId, 2)
        );
        Long menuCId = menuManagementAppService.create(
            new CreateMenuCommand("菜单C", "/c", "c", rootId, 3)
        );

        // When: 查询菜单树
        List<MenuResponse> menuTree = menuManagementAppService.findTree();

        // Then: 验证子菜单都有正确的 sortOrder
        assertThat(menuTree).hasSize(1);
        MenuResponse root = menuTree.get(0);
        assertThat(root.children()).hasSize(3);

        // 验证所有子菜单的 sortOrder 字段正确
        assertThat(root.children()).anyMatch(menu -> menu.id().equals(menuAId) && menu.sortOrder() == 1);
        assertThat(root.children()).anyMatch(menu -> menu.id().equals(menuBId) && menu.sortOrder() == 2);
        assertThat(root.children()).anyMatch(menu -> menu.id().equals(menuCId) && menu.sortOrder() == 3);
    }

    @Test
    void given_parent_menu_with_children_when_delete_then_fail() {
        // Given: 创建父菜单和子菜单
        Long parentId = menuManagementAppService.create(
            new CreateMenuCommand("系统管理", "/system", "system", null, 1)
        );

        menuManagementAppService.create(
            new CreateMenuCommand("用户管理", "/users", "user", parentId, 1)
        );

        // When & Then: 尝试删除有子菜单的父菜单，应该抛出异常
        assertThatThrownBy(() -> menuManagementAppService.delete(parentId))
            .isInstanceOf(DomainException.class);

        // 验证父菜单仍然存在
        assertThat(menuRepository.existsById(parentId)).isTrue();
    }

    @Test
    void given_menu_tree_when_filter_by_ids_then_return_filtered_tree() {
        // Given: 创建菜单树
        Long menu1Id = menuManagementAppService.create(
            new CreateMenuCommand("系统管理", "/system", "system", null, 1)
        );
        Long menu2Id = menuManagementAppService.create(
            new CreateMenuCommand("用户管理", "/users", "user", menu1Id, 1)
        );
        Long menu3Id = menuManagementAppService.create(
            new CreateMenuCommand("角色管理", "/roles", "role", null, 2)
        );

        // When: 只获取系统管理相关的菜单（包含父子关系）
        List<MenuResponse> filteredTree = menuManagementAppService.findTree(
            java.util.Set.of(menu1Id, menu2Id)
        );

        // Then: 验证只返回指定的菜单，且保持父子关系
        assertThat(filteredTree).hasSize(1);
        assertThat(filteredTree.get(0).id()).isEqualTo(menu1Id);
        assertThat(filteredTree.get(0).name()).isEqualTo("系统管理");
        assertThat(filteredTree.get(0).children()).hasSize(1);
        assertThat(filteredTree.get(0).children().get(0).id()).isEqualTo(menu2Id);
        assertThat(filteredTree.get(0).children().get(0).name()).isEqualTo("用户管理");

        // 验证角色管理不在结果中
        assertThat(filteredTree).noneMatch(menu -> menu.id().equals(menu3Id));
    }

    @Test
    void given_child_assigned_but_parent_not_when_filter_then_ancestor_chain_completed() {
        // Given: 父子菜单
        Long menu1Id = menuManagementAppService.create(
            new CreateMenuCommand("系统管理", "/system", "system", null, 1)
        );
        Long menu2Id = menuManagementAppService.create(
            new CreateMenuCommand("用户管理", "/users", "user", menu1Id, 1)
        );

        // When: 只包含子菜单（父菜单未分配）
        List<MenuResponse> filteredTree = menuManagementAppService.findTree(
            java.util.Set.of(menu2Id)
        );

        // Then: 祖先链补全——父分组自动补回，子菜单不丢弃（修原"叶子静默丢弃"bug）
        assertThat(filteredTree).hasSize(1);
        assertThat(filteredTree.get(0).id()).isEqualTo(menu1Id);
        assertThat(filteredTree.get(0).children()).hasSize(1);
        assertThat(filteredTree.get(0).children().get(0).id()).isEqualTo(menu2Id);
    }

    @Test
    void given_empty_group_assigned_when_filter_then_trimmed() {
        // Given: 分组被分配但无可见子
        Long groupId = menuManagementAppService.create(
            new CreateMenuCommand("空分组", null, null, null, 1, MenuType.GROUP)
        );

        // When: 消费侧过滤
        List<MenuResponse> tree = menuManagementAppService.findTree(java.util.Set.of(groupId));

        // Then: 空 GROUP 被裁掉
        assertThat(tree).isEmpty();
    }

    @Test
    void given_dangling_divider_when_filter_then_trimmed() {
        // Given: 分隔线无可见内容兄弟
        Long dividerId = menuManagementAppService.create(
            new CreateMenuCommand("--", null, null, null, 1, MenuType.DIVIDER)
        );

        // When
        List<MenuResponse> tree = menuManagementAppService.findTree(java.util.Set.of(dividerId));

        // Then: 悬空 DIVIDER 被裁掉
        assertThat(tree).isEmpty();
    }

    @Test
    void given_divider_between_menus_when_filter_then_auto_kept() {
        // Given: MENU - DIVIDER - MENU（同级）
        Long aId = menuManagementAppService.create(
            new CreateMenuCommand("A", "/a", null, null, 1, MenuType.MENU));
        menuManagementAppService.create(
            new CreateMenuCommand("--", null, null, null, 2, MenuType.DIVIDER));
        Long bId = menuManagementAppService.create(
            new CreateMenuCommand("B", "/b", null, null, 3, MenuType.MENU));

        // When: 只分配两个 MENU（分隔线未分配）
        List<MenuResponse> tree = menuManagementAppService.findTree(java.util.Set.of(aId, bId));

        // Then: 分隔线按结构自动出现在两个 MENU 之间（B：不分配），按 sortOrder 排序
        assertThat(tree).extracting(MenuResponse::type)
                .containsExactly(MenuType.MENU, MenuType.DIVIDER, MenuType.MENU);
    }

    @Test
    void given_admin_view_when_findTree_then_emptyGroupAndDivider_kept() {
        // Given: 空 GROUP + DIVIDER
        menuManagementAppService.create(
            new CreateMenuCommand("空分组", null, null, null, 1, MenuType.GROUP));
        menuManagementAppService.create(
            new CreateMenuCommand("--", null, null, null, 2, MenuType.DIVIDER));

        // When: /menus 管理视图（menuIds=null，不裁剪）
        List<MenuResponse> tree = menuManagementAppService.findTree();

        // Then: 空 GROUP 与 DIVIDER 均保留
        assertThat(tree).extracting(MenuResponse::type)
                .containsExactly(MenuType.GROUP, MenuType.DIVIDER);
    }
}
