package com.aieducenter.admin.application.dto.response;

/**
 * 角色轻量字典项（{@code GET /roles/all}）——仅启用角色、不分页、精简 {id,name,code}，
 * 供下拉选择，避免拉重分页接口。
 *
 * @since 0.1.0
 */
public record RoleOptionResponse(
        Long id,
        String name,
        String code
) {}
