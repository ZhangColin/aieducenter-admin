package com.aieducenter.admin.domain.enums;

import com.cartisan.core.domain.BaseEnum;
import com.cartisan.data.jpa.converter.BaseEnumConverter;
import jakarta.persistence.Converter;

/**
 * 角色状态枚举（启用/禁用，语义对齐 Soybean）。
 *
 * <p>独立于 {@link AdminUserStatus}——每个聚合自有其状态语义（用户「激活/禁用」、
 * 角色「启用/禁用」）。整数 code 与全系统一致：1=启用、0=禁用。</p>
 *
 * @since 0.1.0
 */
public enum AdminRoleStatus implements BaseEnum<AdminRoleStatus> {

    /**
     * 启用。
     */
    ENABLED(1, "启用"),

    /**
     * 禁用。
     */
    DISABLED(0, "禁用");

    private final Integer code;
    private final String name;

    AdminRoleStatus(Integer code, String name) {
        this.code = code;
        this.name = name;
    }

    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * JPA 转换器，自动应用。
     */
    @Converter(autoApply = true)
    public static class JpaConverter extends BaseEnumConverter<AdminRoleStatus> {
        public JpaConverter() {
            super(AdminRoleStatus.class);
        }
    }
}
