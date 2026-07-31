package com.aieducenter.admin.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.aieducenter.admin.domain.entity.MenuQueryParam;
import com.aieducenter.admin.domain.enums.AdminUserStatus;
import com.aieducenter.admin.domain.enums.MenuIconType;
import com.aieducenter.admin.domain.enums.MenuType;

/**
 * 菜单 Response——承载 Soybean 路由生成器全字段（见 ADR-0004）。
 *
 * <p>{@code menuType}/{@code iconType}/{@code status} 经全局 {@code BaseEnumSerializer} 整数 code 出站；
 * {@code sortOrder} 保留命名（Soybean 叫 {@code order}，避 PG 保留字、前端适配）。
 * {@code createdAt}/{@code updatedAt} 出站审计时间；{@code children} 仅树端点填充。</p>
 *
 * @since 0.1.0
 */
public record MenuResponse(
        Long id,
        String menuName,
        String routeName,
        String routePath,
        String component,
        String icon,
        MenuIconType iconType,
        Long parentId,
        Integer sortOrder,
        MenuType menuType,
        String i18nKey,
        boolean keepAlive,
        boolean constant,
        boolean multiTab,
        boolean hideInMenu,
        String activeMenu,
        String href,
        Integer fixedIndexInTab,
        List<MenuQueryParam> query,
        AdminUserStatus status,
        List<MenuResponse> children,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
