package com.aieducenter.admin.application.dto.response;

import java.util.Set;

/**
 * 角色 Response。
 *
 * @since 0.1.0
 */
public record RoleResponse(
        Long id,
        String name,
        String code,
        String description,
        Integer sortOrder,
        Set<Long> menuIds,
        Set<String> permissionCodes
) {}
