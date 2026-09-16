package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.time.Instant;

/**
 * aiplatform 知识素材清单的 wire 过滤参数——出站查询串成分（issue #69）。
 *
 * <p>与北向 {@code AiplatformMaterialQuery} 字段一一对应（Integer code + Instant + 精确串），
 * 独立成 wire 记录以守 {@code AiplatformClient} 不 import query DTO 的分层口径
 * （同订单/项目/沙箱/单价表清单先例）。{@code sunkFrom}/{@code sunkTo} 为 ISO-8601 Instant
 * （UTC 带 Z），出站经 {@code appendParam} 默认分支取 {@code Instant.toString()} 确定形。</p>
 *
 * @param status    状态单选 code（1=启用 2=停用）；null = 全部
 * @param sunkFrom  沉淀时间下界（含）；null = 不限
 * @param sunkTo    沉淀时间上界（含）；null = 不限
 * @param projectId 来源项目 id 精确；null = 不限
 */
public record AiplatformMaterialListWireRequest(
        Integer status,
        Instant sunkFrom,
        Instant sunkTo,
        String projectId
) {
}
