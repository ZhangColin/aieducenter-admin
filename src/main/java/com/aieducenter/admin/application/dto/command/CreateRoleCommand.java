package com.aieducenter.admin.application.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建角色命令。
 *
 * @since 0.1.0
 */
public record CreateRoleCommand(

        @NotBlank(message = "角色名称不能为空")
        @Size(max = 50, message = "角色名称长度不能超过50")
        String name,

        @NotBlank(message = "角色编码不能为空")
        @Size(max = 50, message = "角色编码长度不能超过50")
        String code,

        @Size(max = 255, message = "描述长度不能超过255")
        String description,

        Integer sortOrder

) {
}
