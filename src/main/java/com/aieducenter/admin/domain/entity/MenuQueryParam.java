package com.aieducenter.admin.domain.entity;

/**
 * 菜单路由静态 query 参数项（对齐 Soybean {@code RouteMeta.query} 的 {@code {key, value}}）。
 *
 * <p>后端透传：仅承载与回显，不自创校验不变量（见 ADR-0004）。</p>
 *
 * @param key 参数键
 * @param value 参数值
 * @since 0.1.0
 */
public record MenuQueryParam(String key, String value) {
}
