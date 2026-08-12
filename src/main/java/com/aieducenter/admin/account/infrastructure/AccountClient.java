package com.aieducenter.admin.account.infrastructure;

import com.aieducenter.admin.account.application.dto.wire.AccountSearchWireRequest;
import com.aieducenter.admin.account.application.dto.wire.AccountWireResponse;
import com.cartisan.openapi.client.OpenApiClient;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.response.PageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * identity 签名 HTTP 客户端——封装 {@link OpenApiClient}，屏蔽 wire 层细节。
 *
 * <p>所有对 identity 账号管理能力域的调用都经此客户端发起，自动签名（admin 自身 apiKey/admin-console，
 * 复用框架 {@link OpenApiClient} 既有 HMAC-SHA256 签名，无需新凭据——见 ADR-0007）。操作者身份经
 * 框架 {@code RequestContext}→{@code X-User-Id/X-User-Name} 自动透传，identity 据此审计。</p>
 *
 * <p>为 infrastructure 包内裸 {@code @Component}（BFF 出站客户端，不走 {@code @Port/@Adapter}，
 * 详见 ADR-0007「admin BFF 出站客户端为裸 @Component」，与 {@code PaymentClient} / {@code AppRegistryClient} 同款）。</p>
 *
 * <p>identity 账号管理契约（identity #67–#72）未全部冻结，本客户端按 #49 spec 计划形态对接，
 * 字段/端点最终以 identity 实现契约为准。client / appservice 可先写、用 mock 验，待 identity 就绪再接真。</p>
 *
 * @since 0.1.0
 */
@Component
public class AccountClient {

    private static final Logger log = LoggerFactory.getLogger(AccountClient.class);

    // identity 的列表端点返回 ApiResponse<PageResponse<...>> 信封（{code,message,data:{items,total,page,size}}），
    // 须按信封反序列化并取 .data()——与 payment 列表端点一致（镜像 PaymentClient）。
    private static final TypeReference<ApiResponse<PageResponse<AccountWireResponse>>> ACCOUNT_PAGE_TYPEREF =
            new TypeReference<>() {};

    private final OpenApiClient openApiClient;
    private final String baseUrl;

    public AccountClient(OpenApiClient openApiClient,
                         @Value("${admin.identity.base-url}") String baseUrl) {
        this.openApiClient = openApiClient;
        this.baseUrl = baseUrl;
    }

    /**
     * 分页搜索平台账号（透传 identity）。
     *
     * @param filter wire 层过滤参数（由应用层从 {@code AccountQuery} 映射而来）
     * @param page   页码，<strong>1-based</strong>（应用层由 Spring {@code Pageable} 的 0-based 页码 +1 传入；
     *               此处 {@code page - 1} 还原为 identity 端 Spring {@code Pageable} 的 0-based）
     * @param size   每页大小
     * @return identity 返回的分页结果
     */
    public PageResponse<AccountWireResponse> listAccounts(AccountSearchWireRequest filter, int page, int size) {
        // 入参 page 为 1-based，identity 端用 Spring Pageable 的 0-based，故 -1（与 PaymentClient.listPayments 一致）。
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/api/account?page=").append(page - 1)
                .append("&size=").append(size);
        appendParam(url, "email", filter.email());
        appendParam(url, "phone", filter.phone());
        appendParam(url, "userId", filter.userId());
        appendParam(url, "status", filter.status());
        appendParam(url, "locked", filter.locked());
        appendParam(url, "registeredAtFrom", filter.registeredAtFrom());
        appendParam(url, "registeredAtTo", filter.registeredAtTo());
        log.debug("AccountClient.listAccounts: {}", url);
        ApiResponse<PageResponse<AccountWireResponse>> resp =
                openApiClient.get(url.toString(), ACCOUNT_PAGE_TYPEREF);
        return resp.data();
    }

    private static void appendParam(StringBuilder url, String name, Object value) {
        if (value != null) {
            url.append('&').append(name).append('=').append(encode(value.toString()));
        }
    }

    private static String encode(String value) {
        // 与 PaymentClient / AppRegistryClient 一致：简单 URL 编码，避免特殊字符问题
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
