package com.aieducenter.admin.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.aieducenter.admin.application.dto.response.AdminUserResponse;
import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.cartisan.web.mapper.DomainMapper;

/**
 * 管理员 Mapper。
 *
 * @since 0.1.0
 */
@Mapper(componentModel = "spring")
public interface AdminUserMapper extends DomainMapper<AdminUser, AdminUserResponse> {

    @Mapping(target = "statusName", source = "status.name")
    @Override
    AdminUserResponse convert(AdminUser adminUser);
}
