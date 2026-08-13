package com.aieducenter.admin.payment.infrastructure;

import com.aieducenter.admin.payment.application.PaymentManagementAppService;
import com.cartisan.openapi.client.OpenApiClient;
import com.cartisan.openapi.config.CartisanOpenapiProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * payment wire 契约测试共享脚手架——子类化 {@link OpenApiClient} 仅替换 HTTP 传输：用对齐 Spring Boot
 * 默认的 {@link ObjectMapper} 反序列化给定的 payment 信封 JSON，其余全真实（真实 {@code PaymentClient} +
 * 真实 Jackson 反序列化 + 真实 {@code PaymentManagementAppService} 映射）。唯一被替换的是网络传输——对 wire
 * 反序列化契约无关。{@code ObjectMapper} 对齐 Spring Boot 默认（{@code FAIL_ON_UNKNOWN_PROPERTIES=false} +
 * {@code JavaTimeModule}）。
 *
 * <p>供 {@link PaymentClientListEnvelopeContractTest} / {@link PaymentEnumNamePassThroughContractTest} 复用，
 * 避免脚手架重复。</p>
 *
 * @since 0.1.0
 */
final class PaymentWireTestSupport {

    private PaymentWireTestSupport() {
    }

    /**
     * 用给定 payment 信封 JSON 构造一个 HTTP 传输被替换的 {@link PaymentManagementAppService}。
     * 其 {@code PaymentClient} 的 {@code get} 忽略 url、把 envelopeBody 按 {@code PaymentClient} 真实传入的
     * {@link TypeReference} 反序列化——真实反序列化路径完整保留。
     */
    static PaymentManagementAppService appServiceWithStubTransport(String envelopeBody) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);   // Spring Boot 默认

        OpenApiClient stubTransport = new OpenApiClient(new CartisanOpenapiProperties(), null, mapper) {
            @Override
            public <T> T get(String url, TypeReference<T> typeReference) {
                try {
                    return mapper.readValue(envelopeBody, typeReference);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return new PaymentManagementAppService(new PaymentClient(stubTransport, "http://stub-payment"));
    }
}
