package com.aieducenter.admin.aiplatform.application.dto.response;

import java.time.Instant;

/**
 * AI 平台知识素材详情——北向出口，逐字镜像 aiplatform {@code BackofficeMaterialDetailResponse}
 * （#166）：元数据（与清单行同形）＋素材全文。运营读全文才能判断停不停用，故详情必带内容。
 *
 * <p>{@code content}＝块按 seq 以空行拼接的 PRD 全文（段落级重组：超长单段硬切的切点呈现为
 * 段落断——内容无损、排版尽力）。其余字段语义同 {@link AiplatformMaterialSummaryResponse}。</p>
 *
 * @since 0.1.0
 */
public record AiplatformMaterialDetailResponse(

        /** 素材标识（TSID 十进制字符串） */
        String id,

        /** 素材类别（v1 业务口径 PRD） */
        String kind,

        /** 来源项目 id（容缺直读，不校验存在） */
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
        String operatorName,

        /** 素材全文（块按 seq 以空行拼接） */
        String content
) {
}
