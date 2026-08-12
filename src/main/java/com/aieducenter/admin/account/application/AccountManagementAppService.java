package com.aieducenter.admin.account.application;

import com.aieducenter.admin.account.application.dto.query.AccountQuery;
import com.aieducenter.admin.account.application.dto.response.AccountSummaryResponse;
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
     * <p>分页形状对齐 admin 现有列表端点（与 {@code /apps}、{@code /payments} 同形）：{@code Pageable} 0-based 页码
     * +1 传入客户端（客户端约定 1-based），响应沿用 identity 回显的 {@code total/page/size}。</p>
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

        return new PageResponse<>(items, page.total(), page.page(), page.size());
    }

    private static AccountSummaryResponse toSummary(AccountWireResponse wire) {
        return new AccountSummaryResponse(
                wire.userId(), wire.email(), wire.phone(),
                wire.nickname(), wire.avatar(),
                wire.status(), wire.locked(), wire.hasPassword());
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
