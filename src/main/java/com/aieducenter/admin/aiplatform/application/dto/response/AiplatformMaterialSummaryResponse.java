package com.aieducenter.admin.aiplatform.application.dto.response;

import java.time.Instant;

/**
 * AI 平台知识素材——北向出口，逐字镜像 aiplatform {@code BackofficeMaterialSummaryResponse}
 * （#166，清单行/停用⇄启用回执/删除回执共形）：素材元数据一行。管理单元＝素材＝
 * 项目 × 素材类型（登记表一行），非块。
 *
 * <p>{@code status} 为 Integer code + {@code statusName} 中文名随行（1=启用 2=停用）；
 * {@code sunkAt}＝首沉淀时间（重沉淀与治理动作不改此列）；操作者两肢为 admin 侧管理员标识
 * （X-User-Id/X-User-Name 透传落痕——知识治理动作必留痕，与单价表缺头落空有意不同），
 * 未治理过的素材如实出 JSON null。删除回执＝删除前终态（provider 契约如此，逐字镜像）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformMaterialSummaryResponse(

        /** 素材标识（TSID 十进制字符串，URL 柄） */
        String id,

        /** 素材类别（v1 业务口径 PRD） */
        String kind,

        /** 来源项目 id（容缺直读） */
        String projectId,

        /** 来源项目名（登记面冗余） */
        String projectName,

        /** 素材标题 */
        String title,

        /** 素材状态 code（1=启用 2=停用） */
        Integer status,

        /** 素材状态名（直读展示） */
        String statusName,

        /** 首沉淀时间（ISO-8601 Instant，UTC 带 Z） */
        Instant sunkAt,

        /** 最近管理动作操作者 id（未治理过为 null） */
        String operatorId,

        /** 最近管理动作操作者名（直读展示） */
        String operatorName
) {
}
