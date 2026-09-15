package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.aieducenter.admin.aiplatform.application.AiplatformAccountAppService;
import com.aieducenter.admin.aiplatform.application.AiplatformUpstreamException;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformAccountProfileResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformAccountProfileWireResponse;
import com.aieducenter.admin.aiplatform.infrastructure.AiplatformClient;
import com.cartisan.openapi.client.OpenApiClientException;

/**
 * aiplatform BFF 集成测试（issue #63 T1 首批用例）——mock {@link AiplatformClient}，验证
 * {@link AiplatformAccountAppService} 在 Spring 上下文中的完整接线（DI、wire→response DTO 映射、
 * 下游错误透传不映射）。
 *
 * <p>镜像 {@code AccountBffIntegrationTest} / {@code PaymentBffIntegrationTest}。不模拟安全层
 * （权限在 {@code AiplatformRbacEnforcementIntegrationTest} 覆盖）；不直测 {@link AiplatformClient}
 * （wire 反序列化契约在 {@code AiplatformAccountClientContractTest} 以真实传输 stub 钉死，
 * 方法边界 mock 会绕过反序列化——已知反例）。</p>
 *
 * <p>mock 数据按 aiplatform {@code BackofficeAccountProfileResponse} 真实形状构造：
 * {@code id} 为 String（TSID 十进制串——区别于 identity 的 Long）、{@code createdAt} 为
 * {@code LocalDateTime}。错误路径断言 <strong>透传</strong>（{@link AiplatformUpstreamException}
 * 携带 provider 的 HTTP 状态 + 数字业务码 + message），而非 payment/identity 式的
 * {@code BaseCodeMessage} 映射（spec #62 定稿：aiplatform 不做映射翻译）。</p>
 *
 * @since 0.1.0
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AiplatformBffIntegrationTest {

    @Autowired
    private AiplatformAccountAppService accountAppService;

    @MockBean
    private AiplatformClient aiplatformClient;

    // ========== 账号档案 · DTO 映射 ==========

    @Test
    void given_accountProfile_when_get_then_returnMappedProfile() {
        when(aiplatformClient.getAccountProfile("auth0|65f2c8a1")).thenReturn(
                new AiplatformAccountProfileWireResponse(
                        "3829492001234567", "auth0|65f2c8a1", "文野",
                        LocalDateTime.of(2026, 1, 15, 10, 30, 0)));

        AiplatformAccountProfileResponse profile = accountAppService.getAccountProfile("auth0|65f2c8a1");

        // DTO 映射：wire → response 逐字段（逐字镜像——无增删字段、无换型）
        assertThat(profile.id()).isEqualTo("3829492001234567");           // String（TSID 十进制串）
        assertThat(profile.externalId()).isEqualTo("auth0|65f2c8a1");
        assertThat(profile.displayName()).isEqualTo("文野");
        assertThat(profile.createdAt()).isEqualTo(LocalDateTime.of(2026, 1, 15, 10, 30, 0));
    }

    @Test
    void given_wireTranslationThrowsUpstream_when_get_then_propagateWithoutMapping() {
        // 错误信封已由 AiplatformClient 翻译为透传异常（IDN_004→HTTP 404 + 数字业务码 6004），
        // AppService 不再二次翻译——原样上抛，由北向 advice 还原 provider 信封
        when(aiplatformClient.getAccountProfile("auth0|unknown")).thenThrow(
                AiplatformUpstreamException.from(
                        new OpenApiClientException(404, "{\"code\":6004,\"message\":\"账号不存在\",\"data\":null}")));

        assertThatThrownBy(() -> accountAppService.getAccountProfile("auth0|unknown"))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(404);
                    assertThat(upstream.envelopeCode()).isEqualTo(6004);
                    assertThat(upstream.getMessage()).isEqualTo("账号不存在");
                });
    }
}
