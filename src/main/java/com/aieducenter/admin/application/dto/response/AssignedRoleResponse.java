package com.aieducenter.admin.application.dto.response;

/**
 * 用户已分配角色的裁剪投影（用户详情「分配角色」回显用）。
 *
 * <p>仅 {id, name, code} 三字段——禁止复用 {@link RoleResponse}（会把 menuIds/permissionCodes
 * 重数据泄进用户接口）。id 为 Long，序列化时经框架 Jackson 配置输出为字符串（与响应中
 * 其他 id 字段一致的 Long 精度约定）。</p>
 *
 * @since 0.1.0
 */
public record AssignedRoleResponse(
        Long id,
        String name,
        String code
) {}
