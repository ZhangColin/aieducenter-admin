package com.aieducenter.admin.application.dto.response;

import java.time.LocalDateTime;
import java.util.Set;

import com.aieducenter.admin.domain.enums.AdminRoleStatus;

/**
 * 角色 Response。
 *
 * <p>{@code status} 经全局 {@code BaseEnumSerializer} 整数 code 出站；
 * {@code home} 为默认首页 route name（可空）；{@code createdAt}/{@code updatedAt} 出站审计时间。</p>
 *
 * @since 0.1.0
 */
public record RoleResponse(
        Long id,
        String name,
        String code,
        String description,
        Integer sortOrder,
        String home,
        AdminRoleStatus status,
        Set<Long> menuIds,
        Set<String> permissionCodes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
