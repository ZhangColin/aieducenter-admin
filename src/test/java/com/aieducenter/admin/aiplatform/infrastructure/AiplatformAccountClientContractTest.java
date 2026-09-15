package com.aieducenter.admin.aiplatform.infrastructure;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aieducenter.admin.aiplatform.application.AiplatformUpstreamException;

/**
 * aiplatform 账号读口的 wire 反序列化契约测试（对接 aiplatform #154 已冻结契约，issue #63）。
 *
 * <p>aiplatform {@code GET /api/backoffice/accounts/{externalId}} 返回
 * {@code ApiResponse<BackofficeAccountProfileResponse>} 信封（{@code {"code","message","data",...}}）。
 * {@link AiplatformClient} 必须按信封反序列化并取 {@code .data()}；若把信封体直接当档案反序列化，
 * 则在 Spring Boot 默认 {@code FAIL_ON_UNKNOWN_PROPERTIES=false} 下信封字段被静默忽略、
 * {@code data} 缺失——与 payment/identity 列表端点同款隐患。</p>
 *
 * <p>本测试 <strong>不</strong> mock {@code AiplatformClient}（BFF 集成测试在方法边界 mock，恰好掩盖
 * wire 契约）。而是经 {@link AiplatformWireTestSupport} 子类化 {@link com.cartisan.openapi.client.OpenApiClient}
 * 仅替换 HTTP 传输，其余全真实。aiplatform 档案真实形状（源码 {@code BackofficeAccountProfileResponse}）：
 * {@code id} 为 <strong>String</strong>（TSID 十进制串——aiplatform REST Long 序列化口径，区别于
 * identity {@code AccountManagementView.userId} 的 Long）、{@code createdAt} 为 ISO 时间。</p>
 *
 * <p>错误契约（spec #62 定稿：忠实透传）：aiplatform 错误信封 {@code code} 装数字业务码
 * （域码×1000＋序号，如 {@code IDN_004}→6004，见 aiplatform {@code ErrorCodePrefix}），
 * HTTP 状态独立。BFF 原样透传（{@link AiplatformUpstreamException}），<strong>不做</strong>
 * payment/identity 式的 {@code BaseCodeMessage} 映射翻译。</p>
 *
 * @since 0.1.0
 */
class AiplatformAccountClientContractTest {

    /** aiplatform GET /api/backoffice/accounts/{externalId} 的真实成功响应形状（ApiResponse 信封 + 四字段档案）。 */
    private static final String ACCOUNT_PROFILE_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "id": "3829492001234567",
                "externalId": "auth0|65f2c8a1b3d4e5f6a7b8c9d0",
                "displayName": "文野",
                "createdAt": "2026-01-15T10:30:00"
              },
              "requestId": "req-1a2b3c",
              "errors": null
            }
            """;

    /** aiplatform externalId 未命中的真实错误响应形状：HTTP 404 + 信封 code=6004（IDN_004 数字业务码）。 */
    private static final String IDN_004_ERROR_ENVELOPE = """
            {
              "code": 6004,
              "message": "账号不存在",
              "data": null,
              "requestId": "req-4d5e6f",
              "errors": null
            }
            """;

    @Test
    void given_aiplatformApiResponseEnvelope_when_getAccountProfile_then_envelopeUnwrappedAndTypesCorrect() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.accountAppServiceWithStubTransport(
                ACCOUNT_PROFILE_ENVELOPE, wireUrl);

        var profile = appService.getAccountProfile("auth0|65f2c8a1b3d4e5f6a7b8c9d0");

        // 出站路径逐字镜像 provider backoffice 路由（externalId URL 编码——OIDC sub 可含 | 等特殊字符）
        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/accounts/auth0%7C65f2c8a1b3d4e5f6a7b8c9d0");

        // 信封正确拆开（data 非 null）——证明 ACCOUNT_PROFILE_TYPEREF 按 ApiResponse<…> 反序列化并取 .data()
        assertThat(profile.id()).isEqualTo("3829492001234567");            // String（TSID 十进制串）
        assertThat(profile.externalId()).isEqualTo("auth0|65f2c8a1b3d4e5f6a7b8c9d0");
        assertThat(profile.displayName()).isEqualTo("文野");
        assertThat(profile.createdAt()).isEqualTo(LocalDateTime.of(2026, 1, 15, 10, 30, 0));  // ISO 时间
    }

    @Test
    void given_idn004ErrorEnvelope_when_getAccountProfile_then_passThroughWithoutMapping() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.accountAppServiceWithErrorTransport(
                404, IDN_004_ERROR_ENVELOPE, wireUrl);

        // 忠实透传（spec #62）：HTTP 404 + 数字业务码 6004（IDN_004）+ provider message 原样，
        // 不翻译成 BaseCodeMessage.NOT_FOUND（区别于 payment/identity 的映射式翻译）
        assertThatThrownBy(() -> appService.getAccountProfile("auth0|unknown"))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(404);
                    assertThat(upstream.envelopeCode()).isEqualTo(6004);
                    assertThat(upstream.getMessage()).isEqualTo("账号不存在");
                });
    }

    @Test
    void given_unparseableErrorBody_when_getAccountProfile_then_fallBackToHttpStatusAsCode() {
        String[] wireUrl = new String[1];
        // 非 aiplatform 信封形状（如网关 HTML 错误页）——无法解析出数字业务码，回落 HTTP 状态作 code
        var appService = AiplatformWireTestSupport.accountAppServiceWithErrorTransport(
                502, "<html>Bad Gateway</html>", wireUrl);

        assertThatThrownBy(() -> appService.getAccountProfile("auth0|anyone"))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(502);
                    assertThat(upstream.envelopeCode()).isEqualTo(502);
                });
    }
}
