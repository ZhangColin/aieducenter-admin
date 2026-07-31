package com.aieducenter.admin.application.dto.query;

import com.aieducenter.admin.domain.enums.AdminUserStatus;
import com.aieducenter.admin.domain.enums.MenuType;
import com.cartisan.data.jpa.specification.Condition;
import com.cartisan.data.jpa.specification.ConditionType;

/**
 * 菜单查询条件（扁平分页 {@code GET /menus/page} 用）。
 *
 * @since 0.1.0
 */
public record MenuQuery(

        /** 菜单名称模糊（{@code menu_name} INNER_LIKE）。 */
        @Condition(type = ConditionType.INNER_LIKE) String menuName,

        /** 菜单类型精确。 */
        @Condition(type = ConditionType.EQUAL) MenuType menuType,

        /** 启停状态精确。 */
        @Condition(type = ConditionType.EQUAL) AdminUserStatus status,

        /** 名称/路由名/路由路径模糊（blurry）。 */
        @Condition(blurry = "menuName,routeName,routePath") String keyword

) {
}
