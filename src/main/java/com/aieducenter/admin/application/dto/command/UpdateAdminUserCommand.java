package com.aieducenter.admin.application.dto.command;

import jakarta.validation.constraints.Size;

/**
 * 更新管理员命令。
 *
 * @since 0.1.0
 */
public record UpdateAdminUserCommand(

        @Size(max = 50, message = "昵称长度不能超过50")
        String nickname,

        @Size(max = 255, message = "邮箱长度不能超过255")
        String email,

        @Size(max = 20, message = "手机号长度不能超过20")
        String phone,

        @Size(max = 512, message = "头像URL长度不能超过512")
        String avatar

) {
}
