package com.aieducenter.admin.application.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建应用命令——appCode 创建后不可修改。
 *
 * @since 0.1.0
 */
public record CreateAppCommand(

        @NotBlank(message = "应用编码不能为空")
        @Size(max = 100, message = "应用编码长度不能超过100")
        String appCode,

        @NotBlank(message = "应用名称不能为空")
        @Size(max = 100, message = "应用名称长度不能超过100")
        String name,

        @Size(max = 500, message = "应用描述长度不能超过500")
        String description

) {}
