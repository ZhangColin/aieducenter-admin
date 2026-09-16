package com.aieducenter.admin.aiplatform.application.dto.wire;

/**
 * aiplatform 单价行清单的 wire 过滤参数——与 aiplatform {@code GET /api/backoffice/price-entries}
 * 查询参数逐字镜像（#160）：provider/model 均为匹配键成分＝<strong>精确等值</strong>过滤、均可缺省
 * （缺省＝全量行，含历史行）；null 字段不出站（该维不参与过滤）。
 *
 * @param provider 模型提供方（精确）
 * @param model    模型（精确）
 */
public record AiplatformPriceEntryListWireRequest(
        String provider,
        String model
) {
}
