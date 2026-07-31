package com.aieducenter.admin.application;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.hutool.core.collection.CollUtil;

import com.aieducenter.admin.domain.aggregate.AdminMenu;
import com.aieducenter.admin.domain.enums.MenuType;

/**
 * 菜单树组装（纯函数，对齐 Soybean directory/menu 模型）。
 *
 * <p>两端点共用：</p>
 * <ul>
 *   <li>{@code menuIds == null}（{@code /menus} 管理视图 / 超管）：全量，仅按 sortOrder（再按 id）排序，<b>不裁剪</b>
 *       ——管理员要能编辑空目录。</li>
 *   <li>{@code menuIds != null}（{@code /auth/current} 角色过滤消费侧）：
 *     <ol>
 *       <li><b>纳入</b>：被分配节点 + 其祖先目录（祖先链补全，消除孤儿叶子）；</li>
 *       <li>排序；</li>
 *       <li><b>裁剪</b>：空 directory（无可见子）裁掉，级联收敛。</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <p>可见性规则（消费侧）由 {@link #prune} 单遍自底向上完成：{@code menu} 恒保留；
 * {@code directory} 仅当 ≥1 存活子。裁子会使父 directory 变空→级联裁。Soybean 无 DIVIDER 概念
 * （见 ADR-0004），故不再有分隔线裁剪分支。</p>
 */
public final class MenuTreeAssembler {

    private MenuTreeAssembler() {
    }

    /**
     * @param allMenus 全部菜单（尚未组装父子关系）
     * @param menuIds  {@code null}=全量（管理视图，仅排序不裁）；非 null=角色过滤（祖先补全 + 排序 + 裁剪）
     * @return 组装后的根节点列表（已排序；过滤视图下已裁剪）
     */
    public static List<AdminMenu> assemble(List<AdminMenu> allMenus, Set<Long> menuIds) {
        Map<Long, Long> parentOf = new HashMap<>();
        for (AdminMenu m : allMenus) {
            parentOf.put(m.getId(), m.getParentId());
        }

        Set<Long> keptIds = (menuIds == null) ? null : expandAncestors(menuIds, parentOf);

        Map<Long, AdminMenu> menuMap = new LinkedHashMap<>();
        for (AdminMenu m : allMenus) {
            if (keptIds == null || keptIds.contains(m.getId())) {
                m.setChildren(CollUtil.newArrayList()); // 防御性重置，避免跨调用 children 累积
                menuMap.put(m.getId(), m);
            }
        }

        List<AdminMenu> roots = CollUtil.newArrayList();
        for (AdminMenu m : menuMap.values()) {
            Long pid = m.getParentId();
            if (pid == null || !menuMap.containsKey(pid)) {
                roots.add(m);
            } else {
                menuMap.get(pid).addChild(m);
            }
        }

        sortTree(roots);
        return (menuIds == null) ? roots : prune(roots);
    }

    /** menuIds ∪ 各节点的全部祖先（沿 parentId 上溯）。 */
    private static Set<Long> expandAncestors(Set<Long> menuIds, Map<Long, Long> parentOf) {
        Set<Long> kept = new HashSet<>(menuIds);
        for (Long id : menuIds) {
            Long p = parentOf.get(id);
            while (p != null && kept.add(p)) {
                p = parentOf.get(p);
            }
        }
        return kept;
    }

    /** 按 sortOrder 升序，再按 id 升序兜底；递归排序各层子节点。 */
    private static void sortTree(List<AdminMenu> nodes) {
        nodes.sort(Comparator
                .comparingInt((AdminMenu m) -> m.getSortOrder())
                .thenComparing(AdminMenu::getId));
        for (AdminMenu n : nodes) {
            sortTree(n.getChildren());
        }
    }

    /**
     * 消费侧可见性裁剪（自底向上）：menu 恒保留；directory 仅当 ≥1 存活子。
     * 子先裁，故空 directory 会级联裁掉。
     */
    private static List<AdminMenu> prune(List<AdminMenu> nodes) {
        for (AdminMenu n : nodes) {
            n.setChildren(prune(n.getChildren()));
        }
        List<AdminMenu> surviving = CollUtil.newArrayList();
        for (AdminMenu n : nodes) {
            if (n.getMenuType() == MenuType.MENU) {
                surviving.add(n);
            } else { // directory
                if (!n.getChildren().isEmpty()) {
                    surviving.add(n);
                }
            }
        }
        return surviving;
    }
}
