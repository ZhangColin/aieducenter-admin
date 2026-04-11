package com.aieducenter.admin.application.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 重置密码命令（管理员操作）。
 *
 * @since 0.1.0
 */
public record ResetPasswordCommand(
        @NotBlank(message = "新密码不能为空")
        @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).{8,20}$", message = "密码强度不足")
        String newPassword
) {}
