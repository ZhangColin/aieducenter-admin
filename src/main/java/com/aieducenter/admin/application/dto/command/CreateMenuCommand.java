package com.aieducenter.admin.application.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建菜单命令。
 *
 * @since 0.1.0
 */
public record CreateMenuCommand(

        @NotBlank(message = "菜单名称不能为空")
        @Size(max = 50, message = "菜单名称长度不能超过50")
        String name,

        @Size(max = 255, message = "路径长度不能超过255")
        String path,

        @Size(max = 50, message = "图标长度不能超过50")
        String icon,

        Long parentId,

        Integer sortOrder

) {
}
