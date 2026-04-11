package com.aieducenter.admin.application.dto.command;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 分配权限命令。
 *
 * @since 0.1.0
 */
public record AssignPermissionsCommand(

        @NotEmpty(message = "权限列表不能为空")
        List<String> permissionCodes

) {
}
