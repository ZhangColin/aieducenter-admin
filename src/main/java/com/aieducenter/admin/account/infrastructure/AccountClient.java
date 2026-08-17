package com.aieducenter.admin.account.infrastructure;

import com.aieducenter.admin.account.application.dto.wire.AccountReasonWireRequest;
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
 * <p>对接 identity {@code GET /api/account}（#70 已冻结：分页多条件搜索，返回
 * {@code ApiResponse<PageResponse<AccountManagementView>>}）。查询参数 / 响应字段与 identity
 * {@code AccountSearchQuery} / {@code AccountManagementView} 同构。</p>
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

    // identity 管理详情端点返回 ApiResponse<AccountManagementView> 信封（详情与列表项同构，复用 AccountWireResponse）。
    private static final TypeReference<ApiResponse<AccountWireResponse>> MANAGEMENT_DETAIL_TYPEREF =
            new TypeReference<>() {};

    // identity 写端点（disable/activate/unlock/sessions-revoke）返回 raw 204 No Content（空 body，无信封）。
    // 用 ApiResponse<Void> typerref 消费：cartisan-boot OpenApiClient.readBody 对空 body 返回 null、不抛
    // （cartisan-boot #19 已修；真实 204 消费路径由 AccountClientWriteVoidContractTest 钉死）。
    private static final TypeReference<ApiResponse<Void>> VOID_TYPEREF =
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
     * @return identity 返回的分页结果（{@code page} 为 identity 1-based 回显「wire 页码+1」，
     *                identity #70 契约；应用层北向透传，见 ADR-0010）
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
        appendParam(url, "createdFrom", filter.createdFrom());
        appendParam(url, "createdTo", filter.createdTo());
        log.debug("AccountClient.listAccounts: {}", url);
        ApiResponse<PageResponse<AccountWireResponse>> resp =
                openApiClient.get(url.toString(), ACCOUNT_PAGE_TYPEREF);
        return resp.data();
    }

    /**
     * 查询账号管理详情（透传 identity）——状态/锁定/是否有密码 + 资料，供前端抽屉。
     *
     * <p>对接 identity {@code GET /api/account/{userId}/management}（#67 已冻结）：返回
     * {@code ApiResponse<AccountManagementView>}，详情与列表项同构（复用 {@link AccountWireResponse}）。</p>
     *
     * @param userId 用户 ID（identity TSID）
     * @return identity 返回的账号管理详情
     * @throws com.cartisan.openapi.client.OpenApiClientException identity 404（账号不存在）等透传，由应用层翻译
     */
    public AccountWireResponse getManagementDetail(Long userId) {
        String url = baseUrl + "/api/account/" + encode(userId.toString()) + "/management";
        log.debug("AccountClient.getManagementDetail: {}", url);
        ApiResponse<AccountWireResponse> resp = openApiClient.get(url, MANAGEMENT_DETAIL_TYPEREF);
        return resp.data();
    }

    /**
     * 封号（透传 identity）——状态置 DISABLED + identity 自动清该用户所有 SSO 会话（踢人）+ 审计。
     *
     * <p>对接 identity {@code POST /api/account/{userId}/disable}（#68 已冻结）：body {@code {reason}}（reason 必填，
     * 由北向 {@link com.aieducenter.admin.account.application.dto.command.DisableAccountCommand} 兜）；
     * 返回 raw 204 无 body（用 {@link #VOID_TYPEREF} 消费，返回值忽略）。operator 身份经框架
     * {@code RequestContext}→{@code X-User-Id/X-User-Name} 自动带入出站 header，identity 据此审计——<strong>不</strong>在
     * body 里塞 operator（与 payment {@code AuditRefundWireRequest} 不同）。</p>
     *
     * <p>identity 返 raw 204 空 body，cartisan-boot {@code OpenApiClient.readBody} 对空 body 返 null（#19 已修，
     * 真实 204 消费路径由 {@code AccountClientWriteVoidContractTest} 钉死）。</p>
     *
     * @param userId  用户 ID（identity TSID）
     * @param request wire 层封号载荷（{reason}）
     * @throws com.cartisan.openapi.client.OpenApiClientException identity 404（账号不存在）、400（reason 缺失）等透传，由应用层翻译
     */
    public void disable(Long userId, AccountReasonWireRequest request) {
        String url = baseUrl + "/api/account/" + encode(userId.toString()) + "/disable";
        log.debug("AccountClient.disable: {}", url);
        openApiClient.post(url, request, VOID_TYPEREF);
    }

    /**
     * 解封（透传 identity）——状态置 ACTIVE（不改会话：封号时已清，用户需重新登录）+ 审计。
     *
     * <p>对接 identity {@code POST /api/account/{userId}/activate}（#69 已冻结）：body {@code {reason}}（reason 可空，
     * 允许空 body）；返回 raw 204 无 body。operator 身份经框架 {@code RequestContext} 透传（同 {@link #disable}）。</p>
     *
     * <p>identity 返 raw 204 空 body（#19 已修，{@code readBody} 返 null——同 {@link #disable}）。</p>
     *
     * @param userId  用户 ID（identity TSID）
     * @param request wire 层解封载荷（{reason}，可空）
     * @throws com.cartisan.openapi.client.OpenApiClientException identity 404（账号不存在）等透传，由应用层翻译
     */
    public void activate(Long userId, AccountReasonWireRequest request) {
        String url = baseUrl + "/api/account/" + encode(userId.toString()) + "/activate";
        log.debug("AccountClient.activate: {}", url);
        openApiClient.post(url, request, VOID_TYPEREF);
    }

    /**
     * 解锁（透传 identity）——locked 置 false（解除登录失败累计等触发的系统锁，独立于 status、不改会话）+ 审计。
     *
     * <p>对接 identity {@code POST /api/account/{userId}/unlock}（#69 已冻结）：body {@code {reason}}（reason 可空）；
     * 返回 raw 204 无 body。operator 身份经框架 {@code RequestContext} 透传（同 {@link #disable}）。</p>
     *
     * <p>identity 返 raw 204 空 body（#19 已修，{@code readBody} 返 null——同 {@link #disable}）。</p>
     *
     * @param userId  用户 ID（identity TSID）
     * @param request wire 层解锁载荷（{reason}，可空）
     * @throws com.cartisan.openapi.client.OpenApiClientException identity 404（账号不存在）等透传，由应用层翻译
     */
    public void unlock(Long userId, AccountReasonWireRequest request) {
        String url = baseUrl + "/api/account/" + encode(userId.toString()) + "/unlock";
        log.debug("AccountClient.unlock: {}", url);
        openApiClient.post(url, request, VOID_TYPEREF);
    }

    /**
     * 强制下线（透传 identity）——一键清退该用户所有 SSO 会话（独立踢人），<strong>不改账号状态</strong>
     * （区别于 {@link #disable}：封号是「改状态 + 附带踢人」，revoke 是「只踢人、不动状态」）+ 审计。
     *
     * <p>对接 identity {@code POST /api/account/{userId}/sessions/revoke}（#69 已冻结）：body {@code {reason}}
     * （reason 可空）；返回 raw 204 无 body。该用户当前无在线会话时撤销 0 个、正常返回 204（不报错）。operator
     * 身份经框架 {@code RequestContext}→{@code X-User-Id/X-User-Name} 自动带入出站 header，identity 据此审计——
     * <strong>不</strong>在 body 里塞 operator（同 {@link #disable}）。</p>
     *
     * <p>identity 返 raw 204 空 body（#19 已修，{@code readBody} 返 null——同 {@link #disable}）。</p>
     *
     * @param userId  用户 ID（identity TSID）
     * @param request wire 层强制下线载荷（{reason}，可空）
     * @throws com.cartisan.openapi.client.OpenApiClientException identity 404（账号不存在）等透传，由应用层翻译
     */
    public void revokeSessions(Long userId, AccountReasonWireRequest request) {
        String url = baseUrl + "/api/account/" + encode(userId.toString()) + "/sessions/revoke";
        log.debug("AccountClient.revokeSessions: {}", url);
        openApiClient.post(url, request, VOID_TYPEREF);
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
