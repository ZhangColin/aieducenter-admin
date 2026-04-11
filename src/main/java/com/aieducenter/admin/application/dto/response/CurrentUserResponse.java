package com.aieducenter.admin.application.dto.response;

import java.util.List;

/**
 * 当前用户响应 DTO。
 *
 * <p>包含用户基本信息、角色、菜单和权限。
 *
 * @since 0.1.0
 */
public record CurrentUserResponse(
        AdminUserResponse user,
        List<String> roleCodes,
        List<MenuResponse> menus,
        List<String> permissions
) {}
