package com.aieducenter.admin.application.mapper;

import org.mapstruct.Mapper;

import com.aieducenter.admin.application.dto.response.RoleResponse;
import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.cartisan.web.mapper.DomainMapper;

/**
 * 角色 Mapper。
 *
 * @since 0.1.0
 */
@Mapper(componentModel = "spring")
public interface AdminRoleMapper extends DomainMapper<AdminRole, RoleResponse> {
}
