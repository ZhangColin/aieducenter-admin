package com.aieducenter.admin.application.dto.query;

import com.cartisan.data.jpa.specification.Condition;
import com.cartisan.data.jpa.specification.ConditionType;

/**
 * 角色查询条件。
 *
 * @since 0.1.0
 */
public record AdminRoleQuery(
    /**
     * 角色名称模糊查询。
     */
    @Condition(type = ConditionType.INNER_LIKE) String name,

    /**
     * 角色编码模糊查询。
     */
    @Condition(type = ConditionType.INNER_LIKE) String code,

    /**
     * 关键字模糊搜索（角色名称、编码、描述）。
     */
    @Condition(blurry = "name,code,description") String keyword
) {}
