package com.aieducenter.admin.application.mapper;

import org.mapstruct.Mapper;

import com.aieducenter.admin.application.dto.response.MenuResponse;
import com.aieducenter.admin.domain.aggregate.AdminMenu;
import com.cartisan.web.mapper.DomainMapper;

/**
 * 菜单 Mapper。
 *
 * @since 0.1.0
 */
@Mapper(componentModel = "spring")
public interface AdminMenuMapper extends DomainMapper<AdminMenu, MenuResponse> {
}
