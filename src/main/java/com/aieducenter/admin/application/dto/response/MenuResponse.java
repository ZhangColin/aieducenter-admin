package com.aieducenter.admin.application.dto.response;

import java.util.List;

import com.aieducenter.admin.domain.enums.MenuType;

/**
 * 菜单 Response。
 *
 * <p>{@code type} 描述节点的渲染角色（与深度正交），整数 code 出站：
 * 1=MENU（可路由叶子）/ 2=GROUP（分组容器）/ 3=DIVIDER（同级分隔线）。
 * 经全局 {@code BaseEnumSerializer} 序列化，无需逐字段配置。</p>
 *
 * @since 0.1.0
 */
public record MenuResponse(
        Long id,
        String name,
        String path,
        String icon,
        Long parentId,
        Integer sortOrder,
        MenuType type,
        List<MenuResponse> children
) {
    /**
     * 旧 7 参构造（{@code type} 缺省 MENU），保持既有调用点兼容。
     */
    public MenuResponse(Long id, String name, String path, String icon, Long parentId, Integer sortOrder,
                        List<MenuResponse> children) {
        this(id, name, path, icon, parentId, sortOrder, MenuType.MENU, children);
    }
}
