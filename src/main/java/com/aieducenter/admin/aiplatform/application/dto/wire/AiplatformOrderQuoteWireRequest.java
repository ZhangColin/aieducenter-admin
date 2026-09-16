package com.aieducenter.admin.aiplatform.application.dto.wire;

/**
 * aiplatform 报价/改价命令体的 wire 镜像——与 aiplatform {@code SubmitQuoteCommand}
 * （#29 交易环②后台机机面）字段同构：金额单位分（正整数）、备注为后台文本（用户面展示）。
 *
 * <p>出站序列化口径：{@code amount} 为 Long → JSON <strong>string</strong>
 * （cartisan-web 全局 {@code Long→ToStringSerializer}，与回执金额的出口口径一致——
 * provider 侧 Jackson 默认 string→Long 强转可回读）；{@code note} null 字段照常序列化
 * 在场（全局 Jackson 含 null）。字段合法性（正数、超长）归 provider 聚合守卫
 * （ORD_008/ORD_009），BFF 不代判。</p>
 *
 * @param amount 总价（分，正数——非正归 provider 聚合守卫 ORD_008）
 * @param note   报价备注（可空，至多 1000 字——超长归 provider ORD_009）
 */
public record AiplatformOrderQuoteWireRequest(
        Long amount,
        String note
) {
}
