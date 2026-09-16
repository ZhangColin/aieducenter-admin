package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.time.Instant;

/**
 * aiplatform 后台知识素材详情的 wire 镜像——与 aiplatform {@code BackofficeMaterialDetailResponse}
 * （#166）字段同构：元数据（与清单行同形）＋素材全文。运营读全文才能判断停不停用，故详情必带
 * 内容。
 *
 * <p>{@code content}＝块按 seq 以空行拼接的全文（段落级重组：分块按空行切段落合并成块，超长
 * 单段硬切的切点呈现为段落断——内容无损、排版尽力）。</p>
 *
 * @param id           素材标识（TSID 十进制字符串）
 * @param kind         素材类别（v1 业务口径 PRD）
 * @param projectId    来源项目 id（容缺直读，不校验存在）
 * @param projectName  来源项目名（登记面冗余）
 * @param title        素材标题
 * @param status       素材状态 code（1=启用 2=停用）
 * @param statusName   素材状态名（直读展示）
 * @param sunkAt       首沉淀时间
 * @param operatorId   最近管理动作操作者 id（未治理过为 null）
 * @param operatorName 最近管理动作操作者名（直读展示）
 * @param content      素材全文（块按 seq 以空行拼接）
 */
public record AiplatformMaterialDetailWireResponse(
        String id,
        String kind,
        String projectId,
        String projectName,
        String title,
        Integer status,
        String statusName,
        Instant sunkAt,
        String operatorId,
        String operatorName,
        String content
) {
}
