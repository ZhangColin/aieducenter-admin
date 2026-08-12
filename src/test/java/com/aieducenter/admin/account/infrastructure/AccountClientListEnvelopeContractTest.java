package com.aieducenter.admin.account.infrastructure;

import com.aieducenter.admin.account.application.AccountManagementAppService;
import com.aieducenter.admin.account.application.dto.query.AccountQuery;
import com.cartisan.openapi.client.OpenApiClient;
import com.cartisan.openapi.config.CartisanOpenapiProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * identity 列表端点的 wire 反序列化契约测试（对接 identity #70 已冻结契约）。
 *
 * <p>identity {@code GET /api/account} 返回 {@code ApiResponse<PageResponse<AccountManagementView>>} 信封
 * （{@code {"code","message","data":{"items","total","page","size"},...}}）。{@link AccountClient} 必须按信封
 * 反序列化并取 {@code .data()}；若把信封体直接当 {@code PageResponse} 反序列化，则在 Spring Boot 默认
 * {@code FAIL_ON_UNKNOWN_PROPERTIES=false} 下，信封的 {@code code/message/data} 被静默忽略，
 * {@code PageResponse.items} 默认为 {@code null}，继而在 {@code AccountManagementAppService.list} 的
 * {@code page.items().stream()} 处抛 NPE（生产 {@code AccountController.list} 500）——与 payment 同款隐患。</p>
 *
 * <p>本测试 <strong>不</strong> mock {@code AccountClient}（{@code AccountBffIntegrationTest} 在方法边界 mock，
 * 恰好掩盖了这条 wire 契约）。而是子类化 {@link OpenApiClient}，仅替换 HTTP 传输（{@code get} 改为用同一个
 * {@code ObjectMapper} 反序列化 identity 的真实信封 JSON），其余全真实：真实 {@link AccountClient#listAccounts}
 * （传入它真实的 {@code TypeReference}）→ 真实 Jackson 反序列化 → 真实 {@link AccountManagementAppService#list}。
 * 唯一被替换的是网络传输——对本反序列化契约无关。{@code ObjectMapper} 对齐 Spring Boot 默认
 * （{@code FAIL_ON_UNKNOWN_PROPERTIES=false} + {@code JavaTimeModule}），正是 NPE 得以静默发生的配置。</p>
 *
 * <p>identity {@code AccountManagementView} 真实形状：{@code status} 经 cartisan-web {@code BaseEnumSerializer}
 * 序列化为 Integer code（1=ACTIVE / 0=DISABLED）、{@code userId} 为 Long TSID、{@code locked}/{@code hasPassword}
 * 为原始 boolean、不含注册时间字段。本测试一并钉死这些类型契约。</p>
 *
 * @since 0.1.0
 */
class AccountClientListEnvelopeContractTest {

    /**
     * identity GET /api/account 的真实响应形状：ApiResponse&lt;PageResponse&lt;AccountManagementView&gt;&gt; 信封。
     * 每项按 identity AccountManagementView 字段（status 为 Integer code、userId 为 Long、locked/hasPassword 为 boolean）。
     */
    private static final String ACCOUNT_LIST_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "items": [
                  {
                    "userId": 3829492001234567,
                    "email": "alice@example.com",
                    "phone": "13800000001",
                    "nickname": "爱丽丝",
                    "avatar": "https://cdn/avatar1.png",
                    "status": 1,
                    "locked": false,
                    "hasPassword": true
                  },
                  {
                    "userId": 3829492001234568,
                    "email": "bob@example.com",
                    "phone": "13800000002",
                    "nickname": null,
                    "avatar": null,
                    "status": 0,
                    "locked": true,
                    "hasPassword": false
                  }
                ],
                "total": 2,
                "page": 0,
                "size": 20
              },
              "requestId": null,
              "errors": null
            }
            """;

    @Test
    void given_identityApiResponseEnvelope_when_listAccounts_then_itemsPopulatedAndTypesCorrect() {
        AccountManagementAppService appService = appServiceWithStubTransport(ACCOUNT_LIST_ENVELOPE);

        var page = appService.list(
                new AccountQuery(null, null, null, null, null, null, null),
                org.springframework.data.domain.PageRequest.of(0, 20));

        // 信封正确拆开（非 null items）——证明 ACCOUNT_PAGE_TYPEREF 按 ApiResponse<PageResponse<…>> 反序列化并取 .data()
        assertThat(page.items()).hasSize(2);
        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);

        // identity AccountManagementView 类型契约逐字段
        var first = page.items().get(0);
        assertThat(first.userId()).isEqualTo(3829492001234567L);   // Long TSID
        assertThat(first.email()).isEqualTo("alice@example.com");
        assertThat(first.status()).isEqualTo(1);                    // Integer BaseEnum code = ACTIVE
        assertThat(first.locked()).isFalse();                       // 原始 boolean
        assertThat(first.hasPassword()).isTrue();                   // 原始 boolean
        var second = page.items().get(1);
        assertThat(second.userId()).isEqualTo(3829492001234568L);
        assertThat(second.status()).isEqualTo(0);                    // DISABLED
        assertThat(second.locked()).isTrue();
        assertThat(second.hasPassword()).isFalse();
        // 资料/头像可空（identity Profile 可空）
        assertThat(second.nickname()).isNull();
        assertThat(second.avatar()).isNull();
    }

    /**
     * 子类化 OpenApiClient，仅替换 HTTP 传输：用真实 ObjectMapper 反序列化真实信封 JSON。
     * {@code get} 被重写后永不触碰 signer/httpClient，故签名器传 null；{@code properties} 构造期被读取
     * （{@code getTimeout()}），传无参构造的 {@link CartisanOpenapiProperties}（默认初始化各字段）即可。
     * {@code AccountClient} 传入的 {@code TypeReference} 即被测对象——真实反序列化路径完整保留。
     */
    private static AccountManagementAppService appServiceWithStubTransport(String envelopeBody) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);   // Spring Boot 默认，NPE 静默发生的关键

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
        AccountClient accountClient = new AccountClient(stubTransport, "http://stub-identity");
        return new AccountManagementAppService(accountClient);
    }
}
