package com.aieducenter.admin.application.dto.query;

import com.aieducenter.admin.domain.enums.AdminUserGender;
import com.aieducenter.admin.domain.enums.AdminUserStatus;
import com.cartisan.data.jpa.specification.Condition;
import com.cartisan.data.jpa.specification.ConditionType;

/**
 * 管理员查询条件。
 *
 * @since 0.1.0
 */
public record AdminUserQuery(
    /**
     * 用户名模糊查询。
     */
    @Condition(type = ConditionType.INNER_LIKE) String username,

    /**
     * 手机号模糊查询（对齐 Soybean 用户档案独立搜索项）。
     */
    @Condition(type = ConditionType.INNER_LIKE) String phone,

    /**
     * 状态查询。
     */
    @Condition(type = ConditionType.EQUAL) AdminUserStatus status,

    /**
     * 性别查询（整数枚举等值）。
     */
    @Condition(type = ConditionType.EQUAL) AdminUserGender gender,

    /**
     * 关键字模糊搜索（用户名、昵称、邮箱）。
     */
    @Condition(blurry = "username,nickname,email") String keyword
) {}
