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
 * MenuTreeAssembler 纯单测（Soybean directory/menu 模型）：排序 / 祖先链补全 / 裁空 directory / 管理视图不裁。
 *
 * <p>不依赖 Spring 与 DB——直接构造 AdminMenu 喂给纯函数。DIVIDER 概念已随 ADR-0004 移除。</p>
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
        AdminMenu root = menu("root", null, MenuType.DIRECTORY, null, 0, 1L);
        AdminMenu dir = menu("dir", null, MenuType.DIRECTORY, 1L, 0, 2L);
        AdminMenu leaf = menu("leaf", "/x", MenuType.MENU, 2L, 0, 3L);

        List<AdminMenu> roots = MenuTreeAssembler.assemble(List.of(root, dir, leaf), Set.of(3L));

        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).getId()).isEqualTo(1L);
        assertThat(roots.get(0).getChildren()).extracting(AdminMenu::getId).containsExactly(2L);
        assertThat(roots.get(0).getChildren().get(0).getChildren()).extracting(AdminMenu::getId)
                .containsExactly(3L);
    }

    @Test
    void given_empty_directory_assigned_when_assemble_filtered_then_trimmed() {
        // 空目录（无可见子）在消费侧裁掉，并级联收敛父
        AdminMenu root = menu("root", null, MenuType.DIRECTORY, null, 0, 1L);
        AdminMenu emptyDir = menu("empty", null, MenuType.DIRECTORY, 1L, 0, 2L); // 无子

        List<AdminMenu> roots = MenuTreeAssembler.assemble(List.of(root, emptyDir), Set.of(2L));

        assertThat(roots).isEmpty();
    }

    @Test
    void given_directory_with_surviving_menu_when_assemble_filtered_then_kept() {
        AdminMenu root = menu("root", null, MenuType.DIRECTORY, null, 0, 1L);
        AdminMenu dir = menu("dir", null, MenuType.DIRECTORY, 1L, 0, 2L);
        AdminMenu leaf = menu("leaf", "/x", MenuType.MENU, 2L, 0, 3L);

        List<AdminMenu> roots = MenuTreeAssembler.assemble(List.of(root, dir, leaf), Set.of(3L));

        // dir 因有存活子 leaf 而保留
        assertThat(roots.get(0).getChildren()).extracting(AdminMenu::getId).containsExactly(2L);
        assertThat(roots.get(0).getChildren().get(0).getChildren()).extracting(AdminMenu::getId)
                .containsExactly(3L);
    }

    @Test
    void given_admin_view_when_assemble_then_no_trim() {
        // 管理视图：空 directory 也保留（管理员要能编辑空目录）
        AdminMenu root = menu("root", null, MenuType.DIRECTORY, null, 0, 1L);
        AdminMenu emptyDir = menu("empty", null, MenuType.DIRECTORY, 1L, 0, 2L);

        List<AdminMenu> roots = MenuTreeAssembler.assemble(List.of(root, emptyDir), null);

        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).getChildren()).extracting(AdminMenu::getId).containsExactly(2L);
    }

    // ========== helper ==========

    private static AdminMenu menu(String name, String routePath, MenuType type, Long parentId, int sortOrder, Long id) {
        AdminMenu m = new AdminMenu(name, name, routePath, null, null, null, parentId, sortOrder, type,
                null, false, false, false, false, null, null, null, null, null);
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
