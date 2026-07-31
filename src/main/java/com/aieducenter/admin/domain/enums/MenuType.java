package com.aieducenter.admin.domain.enums;

import com.cartisan.core.domain.BaseEnum;
import com.cartisan.data.jpa.converter.BaseEnumConverter;
import jakarta.persistence.Converter;

/**
 * 菜单类型枚举（对齐 Soybean 路由生成器的 {@code MenuType}）。
 *
 * <p>Soybean 的菜单类型只有两值（见 ADR-0004）：</p>
 * <ul>
 *   <li>{@code 1} = directory：目录容器（路由前缀，可含子菜单）</li>
 *   <li>{@code 2} = menu：叶子页面（可路由）</li>
 * </ul>
 * <p>原 {@code MENU/GROUP/DIVIDER} 三值模型已作废。</p>
 *
 * @since 0.1.0
 */
public enum MenuType implements BaseEnum<MenuType> {

    /**
     * 目录（容器节点，路由前缀，可含子菜单）——对应 Soybean directory(1)。
     */
    DIRECTORY(1, "目录"),

    /**
     * 菜单（叶子页面，可路由）——对应 Soybean menu(2)。
     */
    MENU(2, "菜单");

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
