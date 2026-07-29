package com.aieducenter.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aieducenter.admin.domain.aggregate.AdminMenu;
import com.aieducenter.admin.domain.enums.MenuType;

/**
 * MenuTreeAssembler 纯单测：排序 / 祖先链补全 / 裁剪（空 GROUP、悬空 DIVIDER）/ 管理视图不裁。
 *
 * <p>不依赖 Spring 与 DB——直接构造 AdminMenu 喂给纯函数。</p>
 */
@DisplayName("MenuTreeAssembler 菜单树组装")
class MenuTreeAssemblerTest {

    @Test
    void given_unsorted_when_assemble_full_then_sorted_by_sortOrder_then_id() {
        AdminMenu a = menu("A", "/a", MenuType.MENU, null, 3, 1L);
        AdminMenu b = menu("B", "/b", MenuType.MENU, null, 1, 2L);
        AdminMenu c1 = menu("c1", "/a/1", MenuType.MENU, 1L, 2, 10L);
        AdminMenu c2 = menu("c2", "/a/2", MenuType.MENU, 1L, 1, 11L);

        List<AdminMenu> roots = MenuTreeAssembler.assemble(List.of(c2, c1, a, b), null);

        assertThat(roots).extracting(AdminMenu::getId).containsExactly(2L, 1L); // B(1) 先于 A(3)
        assertThat(roots.get(1).getChildren()).extracting(AdminMenu::getId)
                .containsExactly(11L, 10L); // c2(1) 先于 c1(2)
    }

    @Test
    void given_same_sortOrder_when_assemble_then_sorted_by_id_tiebreak() {
        AdminMenu a = menu("A", "/a", MenuType.MENU, null, 0, 5L);
        AdminMenu b = menu("B", "/b", MenuType.MENU, null, 0, 2L);

        List<AdminMenu> roots = MenuTreeAssembler.assemble(List.of(a, b), null);

        assertThat(roots).extracting(AdminMenu::getId).containsExactly(2L, 5L);
    }

    @Test
    void given_leaf_assigned_but_parent_not_when_assemble_filtered_then_ancestor_chain_completed() {
        AdminMenu root = menu("root", null, MenuType.GROUP, null, 0, 1L);
        AdminMenu group = menu("group", null, MenuType.GROUP, 1L, 0, 2L);
        AdminMenu leaf = menu("leaf", "/x", MenuType.MENU, 2L, 0, 3L);

        List<AdminMenu> roots = MenuTreeAssembler.assemble(List.of(root, group, leaf), Set.of(3L));

        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).getId()).isEqualTo(1L);
        assertThat(roots.get(0).getChildren()).extracting(AdminMenu::getId).containsExactly(2L);
        assertThat(roots.get(0).getChildren().get(0).getChildren()).extracting(AdminMenu::getId)
                .containsExactly(3L);
    }

    @Test
    void given_empty_group_assigned_when_assemble_filtered_then_trimmed() {
        AdminMenu root = menu("root", null, MenuType.GROUP, null, 0, 1L);
        AdminMenu emptyGroup = menu("empty", null, MenuType.GROUP, 1L, 0, 2L); // 无子

        List<AdminMenu> roots = MenuTreeAssembler.assemble(List.of(root, emptyGroup), Set.of(2L));

        assertThat(roots).isEmpty();
    }

    @Test
    void given_dangling_divider_when_assemble_filtered_then_trimmed() {
        AdminMenu root = menu("root", null, MenuType.GROUP, null, 0, 1L);
        AdminMenu divider = menu("--", null, MenuType.DIVIDER, 1L, 0, 2L); // 无内容兄弟

        List<AdminMenu> roots = MenuTreeAssembler.assemble(List.of(root, divider), Set.of(2L));

        assertThat(roots).isEmpty();
    }

    @Test
    void given_two_adjacent_dividers_without_content_when_assemble_filtered_then_both_trimmed() {
        AdminMenu root = menu("root", null, MenuType.GROUP, null, 0, 1L);
        AdminMenu d1 = menu("--", null, MenuType.DIVIDER, 1L, 0, 2L);
        AdminMenu d2 = menu("--", null, MenuType.DIVIDER, 1L, 1, 3L);

        List<AdminMenu> roots = MenuTreeAssembler.assemble(List.of(root, d1, d2), Set.of(2L, 3L));

        assertThat(roots).isEmpty();
    }

    @Test
    void given_divider_not_assigned_but_between_assigned_menus_when_assemble_filtered_then_auto_kept() {
        // B：分隔线不分配——只要父容器可见、两侧有被分配的 MENU，就按结构自动出现
        AdminMenu root = menu("root", null, MenuType.GROUP, null, 0, 1L);
        AdminMenu a = menu("A", "/a", MenuType.MENU, 1L, 1, 2L);
        AdminMenu divider = menu("--", null, MenuType.DIVIDER, 1L, 2, 3L);
        AdminMenu b = menu("B", "/b", MenuType.MENU, 1L, 3, 4L);

        // 只分配两个 MENU（分隔线 id 3 不在 menuIds）
        List<AdminMenu> roots = MenuTreeAssembler.assemble(List.of(root, a, divider, b), Set.of(2L, 4L));

        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).getChildren()).extracting(AdminMenu::getId)
                .containsExactly(2L, 3L, 4L); // 分隔线自动补在两个 MENU 之间
    }

    @Test
    void given_divider_with_content_only_on_one_side_when_assemble_filtered_then_kept() {
        // 钉死行为：分隔线只要有任一侧可见内容即保留（前导/尾随分隔线不在本规则裁剪范围）
        AdminMenu root = menu("root", null, MenuType.GROUP, null, 0, 1L);
        AdminMenu a = menu("A", "/a", MenuType.MENU, 1L, 1, 2L);
        AdminMenu divider = menu("--", null, MenuType.DIVIDER, 1L, 2, 3L); // 后面无内容

        List<AdminMenu> roots = MenuTreeAssembler.assemble(List.of(root, a, divider), Set.of(2L));

        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).getChildren()).extracting(AdminMenu::getId)
                .containsExactly(2L, 3L); // 分隔线靠 a 这个内容兄弟保留（尾随）
    }

    @Test
    void given_admin_view_when_assemble_then_no_trim() {
        AdminMenu root = menu("root", null, MenuType.GROUP, null, 0, 1L);
        AdminMenu emptyGroup = menu("empty", null, MenuType.GROUP, 1L, 0, 2L);
        AdminMenu divider = menu("--", null, MenuType.DIVIDER, 1L, 1, 3L);

        List<AdminMenu> roots = MenuTreeAssembler.assemble(List.of(root, emptyGroup, divider), null);

        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).getChildren()).extracting(AdminMenu::getId)
                .containsExactly(2L, 3L); // 空 GROUP + DIVIDER 均保留（管理视图不裁）
    }

    // ========== helper ==========

    private static AdminMenu menu(String name, String path, MenuType type, Long parentId, int sortOrder, Long id) {
        AdminMenu m = new AdminMenu(name, path, null, parentId, sortOrder, type);
        try {
            Field f = AdminMenu.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(m, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return m;
    }
}
