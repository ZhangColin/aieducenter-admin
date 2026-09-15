package com.aieducenter.admin.aiplatform.application;

import com.cartisan.openapi.client.OpenApiClientException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * aiplatform 下游错误透传异常——携带 provider 错误信封原样（spec #62 定稿：忠实透传，不做映射翻译）。
 *
 * <p>aiplatform 错误信封 {@code code} 装数字业务码（域码×1000＋序号，如 {@code IDN_004}→6004、
 * {@code ORD_012}→5012，见 aiplatform {@code ErrorCodePrefix}），HTTP 状态由
 * {@code CodeMessage.httpStatus()} 独立决定——两者不同值。而框架 {@code GlobalExceptionHandler}
 * 渲染 {@code CartisanException} 时把 {@code httpStatus()} 同时装进 HTTP 状态与信封 {@code code}
 * （{@code ApiResponse.error(CodeMessage)} 的实现），无法表达「数字业务码 ≠ HTTP 状态」——因此本异常
 * <strong>不</strong>继承 {@code CartisanException}，由 {@code AiplatformUpstreamErrorAdvice}
 * 以最高序接管渲染（镜像 aiplatform 自身 {@code BusinessCodeEnvelopeAdvice} 的手法），北向出口还原
 * provider 形状：HTTP 状态照旧 + 信封 {@code code}＝数字业务码 + {@code message} 原文。</p>
 *
 * <p>区别于 payment/identity 的映射式翻译（{@code translatePaymentError}→{@code BaseCodeMessage}）：
 * 那是「admin 语义化下游故障」；本异常是「照抄 provider 语义」——运营侧前端比对 aiplatform 业务码
 * 的分支才不至于全为死分支（aiplatform #169 事故的同款教训）。</p>
 *
 * <p>由 {@link com.aieducenter.admin.aiplatform.infrastructure.AiplatformClient} 在出站调用点统一抛出，
 * 六域 AppService 无需逐处 try/catch 翻译。</p>
 *
 * @since 0.1.0
 */
public final class AiplatformUpstreamException extends RuntimeException {

    private static final ObjectMapper ENVELOPE_MAPPER = new ObjectMapper();

    private final int httpStatus;

    /** provider 错误信封的数字业务码（如 IDN_004→6004）；body 不可解析时回落 HTTP 状态 */
    private final int envelopeCode;

    private AiplatformUpstreamException(int httpStatus, int envelopeCode, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.envelopeCode = envelopeCode;
    }

    /** provider 的 HTTP 状态（北向照抄，如 404）。 */
    public int httpStatus() {
        return httpStatus;
    }

    /** provider 错误信封的数字业务码（北向信封 {@code code} 照抄，如 6004）。 */
    public int envelopeCode() {
        return envelopeCode;
    }

    /**
     * 从框架 {@link OpenApiClientException}（≥400 时由 {@code OpenApiClient.validateResponse} 抛出，
     * body＝provider 错误信封原文）解析透传异常。
     *
     * <p>body 为 aiplatform {@code ApiResponse} 形状时取其 {@code code}（数字业务码）与
     * {@code message} 原文；body 不可解析（非 aiplatform 信封，如网关 HTML 错误页/空 body）时
     * 回落：数字业务码＝HTTP 状态、message＝HTTP 状态行描述——仍不引入 admin 侧语义翻译。</p>
     */
    public static AiplatformUpstreamException from(OpenApiClientException e) {
        String body = e.getBody();
        if (body != null && !body.isBlank()) {
            try {
                JsonNode node = ENVELOPE_MAPPER.readTree(body);
                if (node != null && node.isObject() && node.path("code").isInt()) {
                    return new AiplatformUpstreamException(
                            e.getStatusCode(), node.path("code").asInt(),
                            node.path("message").asText(null), e);
                }
            } catch (Exception ignored) {
                // body 非 JSON——走回落分支
            }
        }
        return new AiplatformUpstreamException(
                e.getStatusCode(), e.getStatusCode(),
                "aiplatform 服务错误（HTTP " + e.getStatusCode() + "）", e);
    }
}
