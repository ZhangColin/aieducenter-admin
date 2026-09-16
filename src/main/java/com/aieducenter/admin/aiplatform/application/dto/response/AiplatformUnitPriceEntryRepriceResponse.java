package com.aieducenter.admin.aiplatform.application.dto.response;

/**
 * AI 平台原子改价回执——北向出口，逐字镜像 aiplatform {@code UnitPriceEntryRepriceResponse}
 * （#160）：单调用原子的两行结果——被关旧行（区间落定）＋所开新行（敞口、带操作者）。
 * 两行同事务落库（中途任一守卫失败两行都不动），回执即库内事实。
 *
 * @since 0.1.0
 */
public record AiplatformUnitPriceEntryRepriceResponse(

        /** 被关旧行（effectiveTo 已落＝新行起点；保留其原开行者留痕，不被改写） */
        AiplatformUnitPriceEntryResponse closed,

        /** 所开新行（新单价/币种/起点；改价动作操作者随行落痕） */
        AiplatformUnitPriceEntryResponse opened
) {
}
