package com.aieducenter.admin.aiplatform.application.dto.query;

/**
 * AI 平台单价行清单查询参数（BFF 透传 aiplatform）——字段名与 aiplatform
 * {@code GET /api/backoffice/price-entries} 查询参数逐字镜像（#160，spec #62 定稿、issue #68）。
 *
 * <p>provider/model 均为匹配键成分＝<strong>精确等值</strong>过滤（标识符不做模糊）、均可缺省
 * （缺省＝全量行，含历史行——价史全貌；effectiveTo 为 null 即当前行）。空白串不在本层归一——
 * provider 的 {@code BackofficePriceEntryQuery} 构造期已把空白归一为 null（空串不当过滤值），
 * BFF 逐字透传不重复归一。分页参数 {@code page}/{@code size} 不在此（controller 独立绑定，
 * 缺省 1/20 镜像 provider）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformPriceEntryQuery(

        /** 模型提供方（精确） */
        String provider,

        /** 模型（精确） */
        String model
) {
}
