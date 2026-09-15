package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.time.LocalDateTime;

/**
 * aiplatform 项目清单条目的 wire 镜像——与 aiplatform {@code BackofficeProjectSummaryResponse}
 * （#159 项目域）字段同构。用于 Jackson 反序列化 {@link com.cartisan.openapi.client.OpenApiClient}
 * 响应，不与 aiplatform 内部 DTO 耦合。
 *
 * <p>类型口径（spec #62）：{@code id} 为 <strong>String</strong>（TSID 十进制串）；{@code type}/
 * {@code status} 为 <strong>Integer code</strong> + {@code typeName}/{@code statusName} 中文名
 * （provider 已配对，BFF 透传不做 code→中文映射）；{@code status} 为派生态（1=进行中 3=已归档，
 * 归档优先）+ {@code archived} 原始事实位。</p>
 *
 * @since 0.1.0
 */
public record AiplatformProjectSummaryWireResponse(

        /** 项目标识（TSID 十进制字符串） */
        String id,

        /** 项目名 */
        String name,

        /** 归属账号显示名（跨 BC 软引用取名；无主/缺档为 null） */
        String ownerDisplayName,

        /** 项目类型（1=官网 2=电商） */
        Integer type,

        /** 项目类型名（provider 出口提供） */
        String typeName,

        /** 派生项目状态（1=进行中 3=已归档，归档优先） */
        Integer status,

        /** 派生状态名（provider 出口提供） */
        String statusName,

        /** 是否已归档（单向终点） */
        Boolean archived,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间（审计列） */
        LocalDateTime updatedAt
) {
}
