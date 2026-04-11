package com.aieducenter.admin.application.dto.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员登录命令。
 *
 * @since 0.1.0
 */
public record AdminUserLoginCommand(

        @NotBlank(message = "用户名不能为空")
        String username,

        @NotBlank(message = "密码不能为空")
        String password,

        boolean rememberMe

) {
}
