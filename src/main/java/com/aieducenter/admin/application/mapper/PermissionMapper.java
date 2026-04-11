package com.aieducenter.admin.application.mapper;

import org.mapstruct.Mapper;

import com.aieducenter.admin.application.dto.response.PermissionResponse;
import com.cartisan.security.permission.Permission;
import com.cartisan.web.mapper.DomainMapper;

/**
 * 权限 Mapper。
 *
 * @since 0.1.0
 */
@Mapper(componentModel = "spring")
public interface PermissionMapper extends DomainMapper<Permission, PermissionResponse> {
    // convert() 和 convertList() 由 DomainMapper 基类提供
}
