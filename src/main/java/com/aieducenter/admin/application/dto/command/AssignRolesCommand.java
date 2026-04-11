package com.aieducenter.admin.application.dto.command;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 分配角色命令。
 *
 * @since 0.1.0
 */
public record AssignRolesCommand(

        @NotEmpty(message = "角色列表不能为空")
        List<Long> roleIds

) {
}
