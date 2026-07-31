package com.aieducenter.admin.application.dto.command;

import java.util.List;

/**
 * 分配角色命令。
 *
 * <p>允许传空集 = 清空该用户的全部角色（服务层 clear-then-add）。破窗号仍受
 * 「必须保留 SUPER_ADMIN」守卫约束（{@code BREAK_GLASS_SUPER_ADMIN_REQUIRED}）。</p>
 *
 * @since 0.1.0
 */
public record AssignRolesCommand(

        List<Long> roleIds

) {
}
