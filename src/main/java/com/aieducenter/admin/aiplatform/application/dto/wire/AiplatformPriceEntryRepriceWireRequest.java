package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * aiplatform 原子改价命令体的 wire 镜像——与 aiplatform {@code RepricePriceEntryCommand}
 * （#160 后台机机面）字段同构：单调用原子「关当前行＋开新行」——新行沿用被关行的匹配键
 * （provider/model/tokenKind），单价与币种取本命令。
 *
 * <p>出站序列化口径：{@code unitPrice} 为 BigDecimal → JSON 明文小数（cartisan-web 全局
 * {@code WRITE_BIGDECIMAL_AS_PLAIN} 已启用，不落科学计数）；{@code effectiveFrom} 为 Instant →
 * ISO-8601（UTC 带 Z），null 字段照常序列化在场（全局 Jackson 含 null）——「缺省即时」由
 * provider 裁决（{@code null → Instant.now()}），BFF 不代填时点。</p>
 *
 * @param unitPrice     新每 token 单价（非负；0＝免费档——合法性归 provider 聚合守卫 METER_004）
 * @param currency      新币种（ISO 4217 代码，如 USD——非 ISO 归 provider METER_010）
 * @param effectiveFrom 新行生效起点（可空＝缺省即时；含未来时点＝预发布，对齐供应商凌晨调价；
 *                      须不早于被关行起点，否则 provider METER_005）
 */
public record AiplatformPriceEntryRepriceWireRequest(
        BigDecimal unitPrice,
        String currency,
        Instant effectiveFrom
) {
}
