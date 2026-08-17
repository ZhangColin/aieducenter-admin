package com.aieducenter.admin.account.application;

import com.aieducenter.admin.account.application.dto.query.AccountQuery;
import com.aieducenter.admin.account.application.dto.response.AccountManagementDetailResponse;
import com.aieducenter.admin.account.application.dto.response.AccountSummaryResponse;
import com.aieducenter.admin.account.application.dto.wire.AccountReasonWireRequest;
import com.aieducenter.admin.account.application.dto.wire.AccountSearchWireRequest;
import com.aieducenter.admin.account.application.dto.wire.AccountWireResponse;
import com.aieducenter.admin.account.infrastructure.AccountClient;
import com.cartisan.core.exception.BaseCodeMessage;
import com.cartisan.core.exception.DomainException;
import com.cartisan.openapi.client.OpenApiClientException;
import com.cartisan.web.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 账号管理 BFF 应用服务——聚合 identity 能力域的终端用户账号运营查询。
 *
 * <p>admin 作为 BFF：调接口 + DTO 转换 + 聚合，不持业务逻辑、不持账号数据、不记业务审计
 * （审计归 identity）。下游错误经 {@link #translateAccountError} 统一翻译为 {@link DomainException}
 * （携带 {@link BaseCodeMessage} CodeMessage），错误语义对齐 {@code PaymentManagementAppService}。</p>
 *
 * <p>对接 identity {@code GET /api/account}（#70 已冻结）。</p>
 *
 * @since 0.1.0
 */
@Service
public class AccountManagementAppService {

    private final AccountClient accountClient;

    public AccountManagementAppService(AccountClient accountClient) {
        this.accountClient = accountClient;
    }

    /**
     * 分页搜索平台账号（透传 identity）。
     *
     * <p>分页形状对齐 admin 现有列表端点（与 {@code /apps}、{@code /payments} 同形），平台分页协议
     * 「请求 0-based / 响应 1-based」（ADR-0010）：{@code Pageable} 0-based 页码 +1 传入客户端（1-based
     * 中间表示，client 上 wire 前 -1 还原 0-based）；identity 回显的 {@code page} 本就是 1-based
     * （identity #70 契约：回显「页码+1」），北向响应<strong>原样透传，不得再 +1</strong>。</p>
     */
    public PageResponse<AccountSummaryResponse> list(AccountQuery query, Pageable pageable) {
        // query（北向 controller 绑定）→ wire（出站载荷），与 PaymentManagementAppService.list 把 query 映射为 wire 同位
        var filter = new AccountSearchWireRequest(
                query.email(), query.phone(), query.userId(),
                query.status(), query.locked(),
                query.createdFrom(), query.createdTo());
        PageResponse<AccountWireResponse> page;
        try {
            page = accountClient.listAccounts(
                    filter,
                    pageable.getPageNumber() + 1,   // Spring Pageable 0-based → 客户端 1-based
                    pageable.getPageSize());
        } catch (OpenApiClientException e) {
            throw translateAccountError(e);
        }

        var items = page.items().stream()
                .map(AccountManagementAppService::toSummary)
                .toList();

        // page 为 identity 1-based 回显，北向透传（不得再 +1——#56 验证：响应已是 1-based，再加即 2-based 回归）
        return new PageResponse<>(items, page.total(), page.page(), page.size());
    }

    private static AccountSummaryResponse toSummary(AccountWireResponse wire) {
        return new AccountSummaryResponse(
                wire.userId(), wire.email(), wire.phone(),
                wire.nickname(), wire.avatar(),
                wire.status(), wire.locked(), wire.hasPassword());
    }

    /**
     * 查询账号管理详情（透传 identity）——状态/锁定/是否有密码 + 资料，供前端抽屉。
     *
     * <p>identity 404（账号不存在）翻译为 {@link BaseCodeMessage#NOT_FOUND}（404）。</p>
     */
    public AccountManagementDetailResponse getManagementDetail(Long userId) {
        AccountWireResponse wire;
        try {
            wire = accountClient.getManagementDetail(userId);
        } catch (OpenApiClientException e) {
            throw translateAccountError(e);
        }
        return toDetail(wire);
    }

    private static AccountManagementDetailResponse toDetail(AccountWireResponse wire) {
        return new AccountManagementDetailResponse(
                wire.userId(), wire.email(), wire.phone(),
                wire.nickname(), wire.avatar(),
                wire.status(), wire.locked(), wire.hasPassword());
    }

    /**
     * 封号（透传 identity）——状态置 DISABLED + identity 自动踢所有会话 + 审计。纯透传：仅转发 {reason}，不回读。
     *
     * <p>{@code reason} 来自前端（封号高危、必填，由 {@code DisableAccountCommand @NotBlank} 兜）；
     * operator 身份<strong>不</strong>经此方法——经框架 {@code RequestContext}→{@code X-User-Id/X-User-Name}
     * 自动带入出站 header（identity 管理端点从 RequestContext 读 operator 审计，与 payment 退款审核身份进 body 不同）。</p>
     *
     * <p>纯透传（spec「纯透传」）：identity 写端点返 raw 204、无 body，admin 不回读——避免「封号成功却因回读 GET 抖动
     * 报错」的歧义（成功操作不应因二次读失败而误报）。前端收到成功 ack 后自行回读详情刷新抽屉。</p>
     *
     * <p>错误翻译（复用 {@link #translateAccountError}）：identity 404（账号不存在）⟹
     * {@link BaseCodeMessage#NOT_FOUND}；identity 400（reason 缺失）⟹ {@link BaseCodeMessage#BAD_REQUEST}。</p>
     *
     * @param userId 用户 ID（identity TSID）
     * @param reason 封号原因（必填，非空——由 controller 层 @Valid 保证）
     */
    public void disable(Long userId, String reason) {
        try {
            accountClient.disable(userId, new AccountReasonWireRequest(reason));
        } catch (OpenApiClientException e) {
            throw translateAccountError(e);
        }
    }

    /**
     * 解封（透传 identity）——状态置 ACTIVE（不改会话：封号时已清，用户需重新登录）+ 审计。纯透传：仅转发 {reason}，不回读。
     *
     * <p>复用 {@link #disable} 的身份透传范式：{@code reason} 可空（低危可逆动作），operator 经框架 RequestContext 透传，
     * 纯透传不回读（同 {@link #disable}）。</p>
     *
     * <p>错误翻译：identity 404（账号不存在）⟹ {@link BaseCodeMessage#NOT_FOUND}。</p>
     *
     * @param userId 用户 ID（identity TSID）
     * @param reason 解封原因（可空）
     */
    public void activate(Long userId, String reason) {
        try {
            accountClient.activate(userId, new AccountReasonWireRequest(reason));
        } catch (OpenApiClientException e) {
            throw translateAccountError(e);
        }
    }

    /**
     * 解锁（透传 identity）——locked 置 false（解除系统自动锁定，独立于 status、不改会话）+ 审计。纯透传：仅转发 {reason}，不回读。
     *
     * <p>复用 {@link #disable} 的身份透传范式：{@code reason} 可空，operator 经框架 RequestContext 透传，
     * 纯透传不回读（同 {@link #disable}）。</p>
     *
     * <p>错误翻译：identity 404（账号不存在）⟹ {@link BaseCodeMessage#NOT_FOUND}。</p>
     *
     * @param userId 用户 ID（identity TSID）
     * @param reason 解锁原因（可空）
     */
    public void unlock(Long userId, String reason) {
        try {
            accountClient.unlock(userId, new AccountReasonWireRequest(reason));
        } catch (OpenApiClientException e) {
            throw translateAccountError(e);
        }
    }

    /**
     * 强制下线（透传 identity）——一键清退该用户所有 SSO 会话（独立踢人），<strong>不改账号状态</strong>
     * （区别于 {@link #disable}：封号「改状态+附带踢人」，revoke「只踢人、不动状态」）+ 审计。纯透传：仅转发 {reason}，不回读。
     *
     * <p>复用 {@link #disable} 的身份透传范式：{@code reason} 可空（用户可重新登录、无破坏性，低危可逆动作），
     * operator 经框架 RequestContext 透传，纯透传不回读（同 {@link #disable}）。</p>
     *
     * <p>「不改账号状态」的 BFF 侧保证：本方法只转发至 identity {@code /sessions/revoke}，<strong>不</strong>触发
     * disable/activate/unlock 等状态变更出站调用（见 {@code AccountBffIntegrationTest} 的 never() 断言）。
     * identity 端的实际不动状态由其 revokeSessions 契约保证（调 SsoSessionRevoker.revokeQuietly + 审计，不碰状态）。</p>
     *
     * <p>错误翻译：identity 404（账号不存在）⟹ {@link BaseCodeMessage#NOT_FOUND}。</p>
     *
     * @param userId 用户 ID（identity TSID）
     * @param reason 强制下线原因（可空）
     */
    public void revokeSessions(Long userId, String reason) {
        try {
            accountClient.revokeSessions(userId, new AccountReasonWireRequest(reason));
        } catch (OpenApiClientException e) {
            throw translateAccountError(e);
        }
    }

    /**
     * identity 下游错误翻译——按 HTTP 状态映射为 {@link DomainException}（携带 {@link BaseCodeMessage}），
     * 保留下游异常为 cause。供本上下文各调用点复用（与 {@code PaymentManagementAppService.translatePaymentError} 同款）。
     *
     * <ul>
     *   <li>400 → {@link BaseCodeMessage#BAD_REQUEST}（查询参数非法）</li>
     *   <li>404 → {@link BaseCodeMessage#NOT_FOUND}（资源不存在）</li>
     *   <li>409 → {@link BaseCodeMessage#CONFLICT}（状态冲突）</li>
     *   <li>其它（含 401/403 服务间鉴权失败、5xx）→ {@link BaseCodeMessage#THIRD_PARTY_ERROR}
     *       —— 运营侧已认证，服务间或下游故障统一对外为第三方错误</li>
     * </ul>
     */
    static DomainException translateAccountError(OpenApiClientException e) {
        return switch (e.getStatusCode()) {
            case 400 -> new DomainException(BaseCodeMessage.BAD_REQUEST, e);
            case 404 -> new DomainException(BaseCodeMessage.NOT_FOUND, e);
            case 409 -> new DomainException(BaseCodeMessage.CONFLICT, e);
            default -> new DomainException(BaseCodeMessage.THIRD_PARTY_ERROR, e, e.getStatusCode());
        };
    }
}
