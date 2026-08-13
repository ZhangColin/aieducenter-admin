package com.aieducenter.admin.payment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.cartisan.openapi.client.OpenApiClient;
import com.cartisan.openapi.config.CartisanOpenapiProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * payment 统计端点 from/to（+overview granularity）查询串构建契约测试（issue #51）。
 *
 * <p>根因：payment {@code StatsApiV1Controller} 对 6 个时间窗口端点（overview / gateway-health /
 * operations-audit / by-business-system / by-channel / operations-activity）声明 {@code from}/{@code to}
 * 为<strong>必填</strong> {@code @RequestParam}，但 admin {@link PaymentClient} 旧实现一律不带参 → payment 缺必填
 * 参数返 400 → admin 译为「Invalid request」。本测试钉死修复：6 端点的 admin 出站查询串须把前端给的
 * {@code from}/{@code to}（+overview 的 {@code granularity}）如实、URL 编码后拼入 payment 路径。</p>
 *
 * <p>测试方式：子类化 {@link OpenApiClient} 仅替换 HTTP 传输——<strong>捕获</strong> {@code PaymentClient} 传入
 * {@code get(url, ...)} 的 url 串（这正是出站查询串构建的唯一 seam），用对齐 Spring Boot 默认的 {@link ObjectMapper}
 * 反序列化一个 {@code data:null} 信封以走通方法返回路径，其余全真实（真实 {@link PaymentClient}）。与
 * {@link PaymentWireTestSupport}（信封反序列化 seam）互补：彼处验响应形状，此处验请求查询串。</p>
 *
 * <p>命名/类型/必填与 payment 端对齐（{@code from}/{@code to} 必填、ISO DATE_TIME 的 {@link LocalDateTime}；
 * {@code granularity} 可选、原值透传 payment {@code StatsGranularity}，admin 不拥有该枚举）。</p>
 *
 * @since 0.1.0
 */
class PaymentClientStatsQueryContractTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 7, 14, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 8, 13, 23, 59, 59);

    // LocalDateTime.toString() 的 ISO 形式经 URLEncoder.encode 后：':' → '%3A'，其余（数字 / 'T' / '-'）不编码。
    private static final String FROM_ENC = "2026-07-14T00%3A00";
    private static final String TO_ENC = "2026-08-13T23%3A59%3A59";

    static java.util.stream.Stream<Object[]> statsQueryCases() {
        String query = "?from=" + FROM_ENC + "&to=" + TO_ENC;
        String base = "http://payment/api/v1/stats/";
        Consumer<PaymentClient> overviewWithGranularity = c -> c.getPaymentOverview(FROM, TO, "DAY");
        Consumer<PaymentClient> overviewNoGranularity = c -> c.getPaymentOverview(FROM, TO, null);
        Consumer<PaymentClient> gatewayHealth = c -> c.getGatewayHealth(FROM, TO);
        Consumer<PaymentClient> operationsAudit = c -> c.getOperationsAudit(FROM, TO);
        Consumer<PaymentClient> byBusinessSystem = c -> c.getByBusinessSystem(FROM, TO);
        Consumer<PaymentClient> byChannel = c -> c.getByChannel(FROM, TO);
        Consumer<PaymentClient> operationsActivity = c -> c.getOperationsActivity(FROM, TO);
        return java.util.stream.Stream.of(
                // overview 非空 granularity → 拼入查询串
                new Object[]{overviewWithGranularity, base + "payments/overview" + query + "&granularity=DAY", "overview+granularity"},
                // overview 空 granularity → 不得出现 granularity 参数
                new Object[]{overviewNoGranularity, base + "payments/overview" + query, "overview 无 granularity"},
                new Object[]{gatewayHealth, base + "gateway/health" + query, "gateway-health"},
                new Object[]{operationsAudit, base + "operations/audit" + query, "operations-audit"},
                new Object[]{byBusinessSystem, base + "by-business-system" + query, "by-business-system"},
                new Object[]{byChannel, base + "by-channel" + query, "by-channel"},
                new Object[]{operationsActivity, base + "operations/activity" + query, "operations-activity"});
    }

    @ParameterizedTest(name = "[{2}] from/to（+granularity）如实 URL 编码后拼入 payment 出站查询串")
    @MethodSource("statsQueryCases")
    void given_fromTo_when_statsEndpoint_then_queryStringBuiltAndEncoded(
            Consumer<PaymentClient> call, String expectedUrl, String label) {
        List<String> capturedUrls = new ArrayList<>();
        PaymentClient client = clientCapturingUrls(capturedUrls);

        call.accept(client);

        // 钉死出站查询串：路径 + from/to 必填（编码后）+ overview 的可选 granularity 原值透传。
        assertThat(capturedUrls).as("PaymentClient 必须对 payment 发起一次 GET").hasSize(1);
        assertThat(capturedUrls.get(0)).isEqualTo(expectedUrl);
    }

    /**
     * 真实 {@link PaymentClient}，唯一被替换的是 HTTP 传输：捕获 {@code get(url, ...)} 的 url 串，
     * 并以 {@code data:null} 信封反序列化以走通方法返回路径（测试只断言查询串，不断言返回值）。
     */
    private static PaymentClient clientCapturingUrls(List<String> sink) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);   // Spring Boot 默认
        OpenApiClient stubTransport = new OpenApiClient(new CartisanOpenapiProperties(), null, mapper) {
            @Override
            public <T> T get(String url, TypeReference<T> typeReference) {
                sink.add(url);
                try {
                    return mapper.readValue("{\"code\":0,\"message\":\"ok\",\"data\":null}", typeReference);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return new PaymentClient(stubTransport, "http://payment");
    }
}
