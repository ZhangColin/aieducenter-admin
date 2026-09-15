package com.aieducenter.admin.aiplatform.endpoints.controller;

import com.aieducenter.admin.aiplatform.application.AiplatformUpstreamException;
import com.cartisan.core.context.RequestContext;
import com.cartisan.web.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * aiplatform 下游错误透传渲染——把 {@link AiplatformUpstreamException} 还原为 provider 错误信封形状
 * （spec #62 定稿：忠实透传，不做映射翻译；issue #63 T1 基座）。
 *
 * <p>框架 {@code GlobalExceptionHandler} 渲染 {@code CartisanException} 时把 {@code httpStatus()}
 * 同时装进 HTTP 状态与信封 {@code code}，无法表达 aiplatform 的「数字业务码（如 IDN_004→6004）
 * ≠ HTTP 状态（404）」——故 {@code AiplatformUpstreamException} 不继承 {@code CartisanException}，
 * 由本 advice 以最高序抢占渲染（手法同 aiplatform 自身的 {@code BusinessCodeEnvelopeAdvice}）：
 * HTTP 状态照抄 provider、信封 {@code code}＝provider 数字业务码、{@code message} 原文。
 * {@code requestId} 为 admin 自身请求标识（北向信封的传输元数据，非 provider 业务数据）。</p>
 *
 * @since 0.1.0
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AiplatformUpstreamErrorAdvice {

    @ExceptionHandler(AiplatformUpstreamException.class)
    public ResponseEntity<ApiResponse<Void>> handle(AiplatformUpstreamException exception) {
        return ResponseEntity
                .status(exception.httpStatus())
                .body(ApiResponse.error(exception.envelopeCode(), exception.getMessage())
                        .withRequestId(RequestContext.getRequestId()));
    }
}
