package com.aieducenter.admin.application.dto.query;

/**
 * 应用管理列表查询参数（BFF 透传 app-registry）。
 *
 * @since 0.1.0
 */
public record AppManagementQuery(
        /** 关键字模糊搜索（appCode + name） */
        String keyword,

        /** 状态筛选（透传 app-registry RegisteredAppStatus code） */
        Integer status
) {}
