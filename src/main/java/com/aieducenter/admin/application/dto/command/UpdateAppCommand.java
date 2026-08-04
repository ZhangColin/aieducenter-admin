package com.aieducenter.admin.application.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新应用命令——仅 name 和 description 可修改，appCode 不可变。
 *
 * @since 0.1.0
 */
public record UpdateAppCommand(

        @NotBlank(message = "应用名称不能为空")
        @Size(max = 100, message = "应用名称长度不能超过100")
        String name,

        @Size(max = 500, message = "应用描述长度不能超过500")
        String description

) {}
