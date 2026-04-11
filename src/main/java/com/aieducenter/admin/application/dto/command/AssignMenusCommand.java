package com.aieducenter.admin.application.dto.command;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 分配菜单命令。
 *
 * @since 0.1.0
 */
public record AssignMenusCommand(

        @NotEmpty(message = "菜单列表不能为空")
        List<Long> menuIds

) {
}
