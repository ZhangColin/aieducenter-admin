package com.aieducenter.admin.application.dto.response;

import java.util.List;

/**
 * 菜单 Response。
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
        List<MenuResponse> children
) {}
