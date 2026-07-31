package com.aieducenter.admin.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MenuQueryParamConverter round-trip：List&lt;MenuQueryParam&gt; ↔ JSON 字符串。
 */
@DisplayName("MenuQueryParamConverter JSON 列转换")
class MenuQueryParamConverterTest {

    private final MenuQueryParamConverter converter = new MenuQueryParamConverter();

    @Test
    void given_nonEmpty_when_toColumn_then_back_to_same_list() {
        // Given
        List<MenuQueryParam> params = List.of(
                new MenuQueryParam("id", "1"),
                new MenuQueryParam("tab", "detail")
        );

        // When
        String json = converter.convertToDatabaseColumn(params);
        List<MenuQueryParam> back = converter.convertToEntityAttribute(json);

        // Then
        assertThat(back).isEqualTo(params);
        assertThat(json).contains("\"key\":\"id\"", "\"value\":\"1\"");
    }

    @Test
    void given_empty_when_toColumn_then_null() {
        assertThat(converter.convertToDatabaseColumn(List.of())).isNull();
    }

    @Test
    void given_null_when_toColumn_then_null() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void given_nullColumn_when_toAttribute_then_emptyList() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }

    @Test
    void given_blankColumn_when_toAttribute_then_emptyList() {
        assertThat(converter.convertToEntityAttribute("   ")).isEmpty();
    }

    @Test
    void given_singleParam_when_roundTrip_then_key_value_preserved() {
        List<MenuQueryParam> params = List.of(new MenuQueryParam("q", "搜索词"));

        List<MenuQueryParam> back = converter.convertToEntityAttribute(
                converter.convertToDatabaseColumn(params));

        assertThat(back).hasSize(1);
        assertThat(back.get(0).key()).isEqualTo("q");
        assertThat(back.get(0).value()).isEqualTo("搜索词");
    }
}
