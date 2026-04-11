package com.aieducenter.admin.application.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建管理员命令。
 *
 * @since 0.1.0
 */
public record CreateAdminUserCommand(

        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,19}$", message = "用户名格式不正确")
        String username,

        @NotBlank(message = "密码不能为空")
        @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).{8,20}$", message = "密码强度不足")
        String password,

        @NotBlank(message = "昵称不能为空")
        @Size(max = 50, message = "昵称长度不能超过50")
        String nickname,

        @Size(max = 255, message = "邮箱长度不能超过255")
        String email,

        @Size(max = 20, message = "手机号长度不能超过20")
        String phone

) {
}
