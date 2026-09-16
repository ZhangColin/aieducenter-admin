package com.aieducenter.admin.aiplatform.application.dto.command;

/**
 * AI 平台运营取消订单北向命令——逐字镜像 aiplatform {@code CancelOrderCommand}
 * （#157 后台机机面，spec #62 定稿、issue #70）：限未支付态，语义与用户取消完全一致
 * （订单落已取消、项目解冻回迭代）。取消原因必填——运营内部口径留档，不呈现任何
 * 用户面读面、后台订单详情可见。
 *
 * <p><strong>刻意不带 bean 校验注解</strong>（与 provider 命令同款形制，同
 * {@link AiplatformOrderQuoteCommand}）：字段合法性（原因缺失 ORD_013、原因超长至多
 * 1000 字 ORD_014）由 provider 聚合守卫裁决，错误信封原样透传。操作者身份不在命令体
 * ——经框架 {@code RequestContext}→{@code X-User-Id/X-User-Name} 自动透传落痕订单行。</p>
 *
 * @since 0.1.0
 */
public record AiplatformOrderCancelCommand(

        /** 取消原因（必填，至多 1000 字——运营内部口径；缺失/超长归 provider ORD_013/014） */
        String reason
) {
}
