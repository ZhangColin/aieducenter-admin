package com.aieducenter.admin.application.dto.response;

import java.util.List;

/**
 * 当前用户响应 DTO。
 *
 * <p>包含用户基本信息、角色和权限（身份 claims）。导航资源（menus/home）归 menu 域，
 * 由 {@code GET /api/admin/menus/my} 承接（REQ-13-T3）。
 *
 * @since 0.1.0
 */
public record CurrentUserResponse(
        AdminUserResponse user,
        List<String> roleCodes,
        List<String> permissions
) {}
