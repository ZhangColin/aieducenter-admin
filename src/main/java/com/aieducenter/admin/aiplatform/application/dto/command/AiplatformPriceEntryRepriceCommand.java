package com.aieducenter.admin.aiplatform.application.dto.command;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AI 平台单价行原子改价北向命令——运营人员提交的改价意图，逐字镜像 aiplatform
 * {@code RepricePriceEntryCommand}（#160，spec #62 定稿、issue #68）：单调用原子「关当前行＋
 * 开新行」——新行沿用被关行的匹配键（provider/model/tokenKind），单价与币种取本命令。
 *
 * <p><strong>刻意不带 bean 校验注解</strong>（与 provider 命令同款形制）：字段合法性（不完整/
 * 单价负数 METER_004、币种非 ISO 4217 METER_010、起点倒挂 METER_005）由 provider 聚合守卫
 * 裁决，错误信封原样透传——BFF 不加戏不重复校验（忠实透传零加戏，spec #62）。操作者身份
 * 不在命令体——经框架 {@code RequestContext}→{@code X-User-Id/X-User-Name} 自动透传落痕新行，
 * 前端无法伪造改价归属。</p>
 *
 * <p>{@code unitPrice} 北向按 JSON 明文小数交接（BigDecimal 语义，前后端均无浮点）；
 * {@code effectiveFrom} 为 ISO-8601 Instant（UTC 带 Z，如 2026-09-14T16:00:00Z），可空＝
 * 缺省即时（provider 裁决 now()，BFF 不代填）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformPriceEntryRepriceCommand(

        /** 新每 token 单价（非负；0＝免费档——合法性归 provider 聚合守卫） */
        BigDecimal unitPrice,

        /** 新币种（ISO 4217 代码，如 USD） */
        String currency,

        /** 新行生效起点（可空＝缺省即时；含未来时点＝预发布，对齐供应商凌晨调价） */
        Instant effectiveFrom
) {
}
