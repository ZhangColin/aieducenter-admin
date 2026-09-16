package com.aieducenter.admin.aiplatform.application.dto.wire;

/**
 * aiplatform 运营取消命令体的 wire 镜像——与 aiplatform {@code CancelOrderCommand}
 * （#157 后台机机面）字段同构：取消原因必填（运营内部口径留档，不呈现用户面）。
 *
 * <p>字段合法性（缺失/超长至多 1000 字）归 provider 聚合守卫（ORD_013/ORD_014），
 * BFF 不代判不代填——同 {@link AiplatformOrderQuoteWireRequest} 形制。</p>
 *
 * @param reason 取消原因（必填，至多 1000 字——缺失/超长归 provider ORD_013/014）
 */
public record AiplatformOrderCancelWireRequest(
        String reason
) {
}
