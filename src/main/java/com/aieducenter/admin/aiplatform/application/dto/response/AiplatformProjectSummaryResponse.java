package com.aieducenter.admin.aiplatform.application.dto.response;

import java.time.LocalDateTime;

/**
 * AI 平台项目清单条目——北向出口，逐字镜像 aiplatform {@code BackofficeProjectSummaryResponse}
 * （#159 项目域），无增删字段、无换型（spec #62 忠实透传）。
 *
 * <p>类型口径：{@code id} 为 String（TSID 十进制串）；{@code type}/{@code status} 为 Integer
 * code + {@code typeName}/{@code statusName} 中文名（前端直读 *Name）；{@code status} 为派生态
 * （1=进行中 3=已归档，归档优先）+ {@code archived} 原始事实位。</p>
 *
 * @since 0.1.0
 */
public record AiplatformProjectSummaryResponse(

        /** 项目标识（TSID 十进制字符串） */
        String id,

        /** 项目名 */
        String name,

        /** 归属账号显示名（无主/缺档为 null） */
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
