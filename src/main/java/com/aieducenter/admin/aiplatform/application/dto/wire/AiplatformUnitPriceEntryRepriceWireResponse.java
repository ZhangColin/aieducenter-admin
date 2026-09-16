package com.aieducenter.admin.aiplatform.application.dto.wire;

/**
 * aiplatform 原子改价回执的 wire 镜像——与 aiplatform {@code UnitPriceEntryRepriceResponse}
 * （#160）字段同构：单调用原子的两行结果——被关旧行（区间落定）＋所开新行（敞口、带操作者）。
 * 两行同事务落库，回执即库内事实。
 *
 * @param closed 被关旧行（effectiveTo 已落；保留其原开行者，不被改写）
 * @param opened 所开新行（新单价/币种/起点；改价动作操作者随行落痕）
 */
public record AiplatformUnitPriceEntryRepriceWireResponse(
        AiplatformUnitPriceEntryWireResponse closed,
        AiplatformUnitPriceEntryWireResponse opened
) {
}
