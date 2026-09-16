package com.aieducenter.admin.infrastructure;

import com.aieducenter.admin.application.AppManagementAppService;
import com.aieducenter.admin.application.dto.query.AppManagementQuery;
import com.cartisan.openapi.client.OpenApiClient;
import com.cartisan.openapi.config.CartisanOpenapiProperties;
import com.cartisan.web.request.Pagination;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * app-registry 列表端点的 wire 反序列化契约测试（对接 app-registry #24 迁移后的契约）。
 *
 * <p>与 payment / identity 不同：app-registry {@code GET /api/app-registry/apps} 返回 <strong>裸</strong>
 * {@code PageResponse}（{@code {"items","total","page","size"}}，无 {@code ApiResponse} 信封），
 * {@link AppRegistryClient#listApps} 按 {@code APP_PAGE_TYPEREF} 直接反序列化（不取 {@code .data()}）——
 * 信封形状按域各异，本测试把这条差异钉死在真实 deserialization seam 上。</p>
 *
 * <p>本测试 <strong>不</strong> mock {@code AppRegistryClient}（集成测试在方法边界 mock，恰好掩盖 wire 契约），
 * 而是子类化 {@link OpenApiClient} 仅替换 HTTP 传输，其余全真实：真实 {@code AppRegistryClient.listApps}
 * （传入它真实的 {@code TypeReference}）→ 真实 Jackson 反序列化 → 真实 {@link AppManagementAppService#list} 映射。</p>
 *
 * <p>分页契约（#73 钉死，ADR-0012）：全链 1-based——北向 {@code page} 1-based 经框架 {@code Pagination} 绑定，
 * wire 与北向<strong>同值直传</strong>（无 ±1）；app-registry #24（b56125c）起响应 {@code page} 回显==请求页码。
 * 行为注意：不传 sort 时 app-registry 默认 <strong>createdAt 降序</strong>（最新登记在前）——BFF 不传 sort、
 * 排序语义归下游，此处仅注释钉住、不在 admin 侧复刻。</p>
 *
 * @since 0.1.0
 */
class AppRegistryClientListEnvelopeContractTest {

    /**
     * app-registry GET /api/app-registry/apps 的真实响应形状：裸 PageResponse（无信封）。
     * {@code page} 按 app-registry 真实回显构造：本 fixture 对应 wire 请求 page=1（第 1 页）→ 回显 1（==请求页码）。
     */
    private static final String APP_LIST_BARE_PAGE = """
            {
              "items": [
                {
                  "id": 1,
                  "appCode": "course-svc",
                  "name": "课程服务",
                  "description": "课程域业务系统",
                  "status": 1,
                  "statusName": "启用",
                  "createdAt": "2026-08-01T10:00:00",
                  "updatedAt": "2026-08-01T10:00:00"
                },
                {
                  "id": 2,
                  "appCode": "pay-callback",
                  "name": "支付回调",
                  "description": "",
                  "status": 0,
                  "statusName": "禁用",
                  "createdAt": "2026-08-02T10:00:00",
                  "updatedAt": "2026-08-03T10:00:00"
                }
              ],
              "total": 2,
              "page": 1,
              "size": 20
            }
            """;

    @Test
    void given_appRegistryBarePage_when_listApps_then_itemsPopulatedAndPagePassthrough() {
        String[] wireUrl = new String[1];
        AppManagementAppService appService = appServiceWithStubTransport(APP_LIST_BARE_PAGE, wireUrl);

        var page = appService.list(
                new AppManagementQuery(null, null),
                new Pagination(1, 20, null));

        // 出站 page 直传（ADR-0012）：北向 page=1 → wire 即 page=1（无 ±1）
        assertThat(wireUrl[0]).endsWith("/api/app-registry/apps?page=1&size=20");

        // 裸 PageResponse 正确反序列化（非 null items）——证明 APP_PAGE_TYPEREF 按裸形状直取、不取 .data()
        assertThat(page.items()).hasSize(2);
        var first = page.items().get(0);
        assertThat(first.id()).isEqualTo(1L);
        assertThat(first.appCode()).isEqualTo("course-svc");
        assertThat(first.status()).isEqualTo(1);
        assertThat(first.statusName()).isEqualTo("启用");
        var second = page.items().get(1);
        assertThat(second.status()).isEqualTo(0);
        assertThat(second.statusName()).isEqualTo("禁用");
        // 响应 page 为 app-registry 回显（==请求页码），北向透传
        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);
    }

    /**
     * 子类化 OpenApiClient，仅替换 HTTP 传输：用对齐 Spring Boot 默认的 ObjectMapper 反序列化给定的裸
     * PageResponse JSON。{@code get} 被重写后永不触碰 signer/httpClient，故签名器传 null。
     */
    private static AppManagementAppService appServiceWithStubTransport(String barePageBody, String[] wireUrlSink) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);   // Spring Boot 默认

        OpenApiClient stubTransport = new OpenApiClient(new CartisanOpenapiProperties(), null, mapper) {
            @Override
            public <T> T get(String url, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;   // 捕获 wire URL——断言 page 直传（1-based，无 ±1）打到真实出站 seam
                try {
                    return mapper.readValue(barePageBody, typeReference);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        AppRegistryClient appRegistryClient = new AppRegistryClient(stubTransport, "http://stub-app-registry");
        return new AppManagementAppService(appRegistryClient);
    }
}
