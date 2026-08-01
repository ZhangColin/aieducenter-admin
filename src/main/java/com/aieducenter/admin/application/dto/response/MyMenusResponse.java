package com.aieducenter.admin.application.dto.response;

import java.util.List;

/**
 * 「我的导航」Response（{@code GET /menus/my}，REQ-13-T2）——贴 Soybean {@code UserRoute} 原生形状
 * {@code {routes, home}}，前端动态路由模式直通。
 *
 * <p>{@code home} 可空：用户全部启用角色无非空白 home 时为 {@code null}——项目全局 Jackson 含 null
 * 序列化，出 {@code "home": null} 而非省略字段，前端按 null 兜底（重定向到第一个可见叶子）。</p>
 *
 * @since 0.1.0
 */
public record MyMenusResponse(
        String home,
        List<MenuResponse> menus
) {
}
