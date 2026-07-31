package com.aieducenter.admin.application.dto.command;

import java.util.List;

/**
 * 分配权限命令。
 *
 * <p>允许传空集 = 清空该角色的全部权限（服务层 clear-then-add）。</p>
 *
 * @since 0.1.0
 */
public record AssignPermissionsCommand(

        List<String> permissionCodes

) {
}
