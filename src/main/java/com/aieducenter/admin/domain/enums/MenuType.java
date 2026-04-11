package com.aieducenter.admin.domain.enums;

import com.cartisan.core.domain.BaseEnum;
import com.cartisan.data.jpa.converter.BaseEnumConverter;
import jakarta.persistence.Converter;

/**
 * 菜单类型枚举。
 *
 * @since 0.1.0
 */
public enum MenuType implements BaseEnum<MenuType> {
    /**
     * 普通菜单（可点击，有路由）。
     */
    MENU(1, "菜单"),

    /**
     * 分组标题（不可点击，纯展示，可带图标）。
     */
    GROUP(2, "分组"),

    /**
     * 分隔线。
     */
    DIVIDER(3, "分隔线");

    private final Integer code;
    private final String name;

    MenuType(Integer code, String name) {
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
    public static class JpaConverter extends BaseEnumConverter<MenuType> {
        public JpaConverter() {
            super(MenuType.class);
        }
    }
}
