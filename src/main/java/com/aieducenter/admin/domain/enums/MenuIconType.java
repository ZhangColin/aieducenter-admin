package com.aieducenter.admin.domain.enums;

import com.cartisan.core.domain.BaseEnum;
import com.cartisan.data.jpa.converter.BaseEnumConverter;
import jakarta.persistence.Converter;

/**
 * 菜单图标类型枚举（对齐 Soybean 的 {@code IconType}）。
 *
 * <ul>
 *   <li>{@code 1} = iconify：iconify 图标（如 {@code mdi:xxx}），由 {@code SvgIcon} 原生渲染</li>
 *   <li>{@code 2} = local：本地 SVG 图标（{@code src/assets/svg-icon}）</li>
 * </ul>
 *
 * @since 0.1.0
 */
public enum MenuIconType implements BaseEnum<MenuIconType> {

    /**
     * iconify 图标——对应 Soybean IconType(1)。
     */
    ICONIFY(1, "iconify 图标"),

    /**
     * 本地 SVG 图标——对应 Soybean IconType(2)。
     */
    LOCAL(2, "本地图标");

    private final Integer code;
    private final String name;

    MenuIconType(Integer code, String name) {
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
    public static class JpaConverter extends BaseEnumConverter<MenuIconType> {
        public JpaConverter() {
            super(MenuIconType.class);
        }
    }
}
