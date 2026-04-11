package com.aieducenter.admin.application.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 修改密码命令。
 *
 * @since 0.1.0
 */
public record UpdatePasswordCommand(
        @NotBlank(message = "旧密码不能为空")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).{8,20}$", message = "密码强度不足")
        String newPassword
) {}
