package com.aieducenter.admin.application.dto.command;

import java.util.List;

import com.aieducenter.admin.domain.entity.MenuQueryParam;
import com.aieducenter.admin.domain.enums.AdminUserStatus;
import com.aieducenter.admin.domain.enums.MenuIconType;
import com.aieducenter.admin.domain.enums.MenuType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 更新菜单命令——整体替换 Soybean 路由生成器全字段（见 ADR-0004）。
 *
 * <p>与 {@link CreateMenuCommand} 同构：{@code updateDetails} 全字段覆盖，故必备字段同样校验非空。</p>
 *
 * @since 0.1.0
 */
public record UpdateMenuCommand(

        @NotBlank(message = "菜单名称不能为空")
        @Size(max = 100, message = "菜单名称长度不能超过100")
        String menuName,

        @NotBlank(message = "路由名称不能为空")
        @Size(max = 100, message = "路由名称长度不能超过100")
        String routeName,

        @Size(max = 255, message = "路由路径长度不能超过255")
        String routePath,

        @Size(max = 255, message = "组件长度不能超过255")
        String component,

        @Size(max = 100, message = "图标长度不能超过100")
        String icon,

        MenuIconType iconType,

        Long parentId,

        Integer sortOrder,

        @NotNull(message = "菜单类型不能为空")
        MenuType menuType,

        @Size(max = 100, message = "i18nKey 长度不能超过100")
        String i18nKey,

        boolean keepAlive,

        boolean constant,

        boolean multiTab,

        boolean hideInMenu,

        @Size(max = 100, message = "activeMenu 长度不能超过100")
        String activeMenu,

        @Size(max = 255, message = "href 长度不能超过255")
        String href,

        Integer fixedIndexInTab,

        List<MenuQueryParam> query,

        AdminUserStatus status

) {
}
