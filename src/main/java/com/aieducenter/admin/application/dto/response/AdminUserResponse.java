package com.aieducenter.admin.application.dto.response;

import com.aieducenter.admin.domain.enums.AdminUserGender;
import com.aieducenter.admin.domain.enums.AdminUserStatus;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 管理员响应 DTO。
 *
 * <p>{@code roles} 在列表与详情端点均填充（批量查询一次完成，无 N+1）；
 * {@code /auth/current} 不填充（null），经 {@code NON_NULL} 序列化策略从响应中省略。</p>
 *
 * @since 0.1.0
 */
public record AdminUserResponse(
        Long id,
        String username,
        String nickname,
        String email,
        String phone,
        String avatar,
        AdminUserGender gender,
        String genderName,
        AdminUserStatus status,
        String statusName,
        boolean breakGlass,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<AssignedRoleResponse> roles
) {}
