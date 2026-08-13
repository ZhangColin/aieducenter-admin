package com.aieducenter.admin.account.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.aieducenter.admin.account.application.dto.wire.AccountReasonWireRequest;
import com.cartisan.openapi.client.OpenApiClient;
import com.cartisan.openapi.config.CartisanOpenapiProperties;
import com.cartisan.openapi.signature.HmacSha256SignatureCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * identity 状态写端点的 204 空 body 消费契约测试（cartisan-boot #19 已修后补的 proof）。
 *
 * <p>identity 的 {@code disable}/{@code activate}/{@code unlock} 返 raw <strong>204 No Content（空 body、无信封）</strong>。
 * {@link AccountClient} 三写方法用 {@code VOID_TYPEREF} 消费。cartisan-boot #19 前，{@link OpenApiClient} 对空 body
 * 无条件 {@code readValue} 抛 {@code MismatchedInputException}（实测确认）——彼时此测试无法写（一调就抛）。
 * #19 修了 {@code readBody}（空 body 返 null、不抛）后，本测试钉死 admin 的完整写路径：真实
 * {@link AccountClient#disable} → 真实 {@link OpenApiClient#post}（含 buildHeaders/send/validate/readBody）
 * 消费真实 HTTP 204 不抛，且出站 POST 体 == {@code {reason}}。</p>
 *
 * <p>镜像 cartisan-boot {@code OpenApiClientPutTest}：起本地 {@link HttpServer} 回 204 空 body，建真实
 * {@link AccountClient}（真实 {@link OpenApiClient} + 真实 HMAC 签名器 + dummy 凭据，指向本地服务）。
 * 唯一被 stub 的是网络对端（返 204）——这正是 #19 修复的真实代码路径。与 {@link
 * com.aieducenter.admin.integration.AccountBffIntegrationTest}（方法边界 mock，绕过反序列化）互补。</p>
 *
 * @since 0.1.0
 */
class AccountClientWriteVoidContractTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> receivedPath = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        // 单 context 兜所有 /api/account/* 写端点：一律回 204 空 body（identity 写端点的真实行为）
        server.createContext("/api/account/", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);   // 204 No Content，无 body
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /**
     * 三写端点共享同一 {@code openApiClient.post(url, body, VOID_TYPEREF)} 机制，逐一钉死：204 空 body 被消费不抛、
     * 出站路径 + {@code {reason}} body 正确。
     */
    static java.util.stream.Stream<Object[]> writeOps() {
        long userId = 1001L;
        BiConsumer<AccountClient, AccountReasonWireRequest> disable = (c, r) -> c.disable(userId, r);
        BiConsumer<AccountClient, AccountReasonWireRequest> activate = (c, r) -> c.activate(userId, r);
        BiConsumer<AccountClient, AccountReasonWireRequest> unlock = (c, r) -> c.unlock(userId, r);
        return java.util.stream.Stream.of(
                new Object[]{disable, "/disable", "违规账号，多次刷单"},
                new Object[]{activate, "/activate", "申诉成功"},
                new Object[]{unlock, "/unlock", "风控误判"});
    }

    @ParameterizedTest(name = "[{1}] identity 返 204 空 body → 写操作不抛 + 出站路径/body 正确")
    @MethodSource("writeOps")
    void given_identityReturns204EmptyBody_when_writeStatusOp_then_noThrow_andPostsReasonBody(
            BiConsumer<AccountClient, AccountReasonWireRequest> op, String pathSuffix, String reason) {
        AccountClient client = realClientAtLocalServer();

        // 真实 AccountClient.X → 真实 OpenApiClient.post（buildHeaders/send/validate/readBody 全真实）消费 204 不抛
        assertThatCode(() -> op.accept(client, new AccountReasonWireRequest(reason)))
                .as("identity 返 204 空 body 时，AccountClient 写操作不得抛（依赖 cartisan-boot #19 readBody 容忍空 body）")
                .doesNotThrowAnyException();

        // 出站路径 + {reason} body 正确（operator 身份不在 body——经 RequestContext header 透传，见 AccountClient javadoc）
        assertThat(receivedPath.get()).isEqualTo("/api/account/1001" + pathSuffix);
        assertThat(receivedBody.get()).isEqualTo("{\"reason\":\"" + reason + "\"}");
    }

    @Test
    void given_identityReturns204EmptyBody_when_disableWithNullReason_then_noThrow_andPostsNullReason() {
        // activate/unlock 允许 reason 可空；disable 经 @NotBlank 不会到这，但 client 层 null reason 也应正常出站
        AccountClient client = realClientAtLocalServer();

        assertThatCode(() -> client.disable(1001L, new AccountReasonWireRequest(null))).doesNotThrowAnyException();

        assertThat(receivedPath.get()).isEqualTo("/api/account/1001/disable");
        assertThat(receivedBody.get()).isEqualTo("{\"reason\":null}");
    }

    /**
     * 真实 {@link AccountClient}：真实 {@link OpenApiClient}（真实 HMAC 签名器 + dummy 凭据，指向本地 204 服务）。
     * dummy 凭据仅用于 buildHeaders 产出签名头（本地服务不验签）；真实读路径（readBody 消费 204）是被测对象。
     */
    private AccountClient realClientAtLocalServer() {
        CartisanOpenapiProperties props = new CartisanOpenapiProperties();
        props.getSelf().setApiKey("admin-console-test");
        props.getSelf().setApiSecret("test-secret");
        OpenApiClient openApiClient = new OpenApiClient(props,
                new HmacSha256SignatureCalculator(), new ObjectMapper());
        return new AccountClient(openApiClient, "http://localhost:" + port);
    }
}
