package com.aieducenter.admin.domain.enums;

import com.cartisan.core.domain.BaseEnum;
import com.cartisan.data.jpa.converter.BaseEnumConverter;
import jakarta.persistence.Converter;

/**
 * 管理员性别枚举（对齐 Soybean Admin 用户档案）。
 *
 * <p>整数 code 存储（{@code 1}=男 / {@code 2}=女），经 {@link BaseEnum} 双向转换；
 * JSON 经 cartisan-web 自动整数 code↔枚举（详见基座约定）。可空——用户档案可不填性别。</p>
 *
 * @since 0.1.0
 */
public enum AdminUserGender implements BaseEnum<AdminUserGender> {

    /**
     * 男。
     */
    MALE(1, "男"),

    /**
     * 女。
     */
    FEMALE(2, "女");

    private final Integer code;
    private final String name;

    AdminUserGender(Integer code, String name) {
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
    public static class JpaConverter extends BaseEnumConverter<AdminUserGender> {
        public JpaConverter() {
            super(AdminUserGender.class);
        }
    }
}
