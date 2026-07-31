package com.aieducenter.admin.domain.entity;

import java.util.Collections;
import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link MenuQueryParam} 列表的 JSON 列转换器。
 *
 * <p>DB 列 {@code query}（TEXT）存 JSON 字符串，实体侧为 {@code List<MenuQueryParam>}。
 * 空集合与 null 写入 null，读回 null/空白归一为空集合（实体字段恒非 null，便于透传）。</p>
 *
 * @since 0.1.0
 */
@Converter
public class MenuQueryParamConverter implements AttributeConverter<List<MenuQueryParam>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<MenuQueryParam> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("序列化菜单 query 参数失败", e);
        }
    }

    @Override
    public List<MenuQueryParam> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<List<MenuQueryParam>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("反序列化菜单 query 参数失败: " + dbData, e);
        }
    }
}
