package com.aieducenter.admin.payment.application;

import com.aieducenter.admin.payment.application.dto.query.PaymentOrderQuery;
import com.aieducenter.admin.payment.application.dto.query.RefundOrderQuery;
import com.aieducenter.admin.payment.application.dto.response.PaymentOrderSummaryResponse;
import com.aieducenter.admin.payment.application.dto.response.RefundOrderSummaryResponse;
import com.aieducenter.admin.payment.application.dto.wire.PaymentOrderListWireRequest;
import com.aieducenter.admin.payment.application.dto.wire.PaymentOrderWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.RefundOrderListWireRequest;
import com.aieducenter.admin.payment.application.dto.wire.RefundOrderWireResponse;
import com.aieducenter.admin.payment.infrastructure.PaymentClient;
import com.cartisan.core.exception.BaseCodeMessage;
import com.cartisan.core.exception.DomainException;
import com.cartisan.openapi.client.OpenApiClientException;
import com.cartisan.web.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 支付管理 BFF 应用服务——聚合 payment 能力域的运营查询。
 *
 * <p>admin 作为 BFF：调接口 + DTO 转换 + 聚合，不持业务逻辑、不记业务审计（审计归 payment
 * {@code OperationLog}）。下游错误经 {@link #translatePaymentError} 统一翻译为 {@link DomainException}
 * （携带 {@link BaseCodeMessage} CodeMessage；payment 专属 {@code ADMIN_*} 码待 issue #37 落地后补充），
 * 错误语义对齐 {@code AppManagementAppService}。</p>
 *
 * @since 0.1.0
 */
@Service
public class PaymentManagementAppService {

    private final PaymentClient paymentClient;

    public PaymentManagementAppService(PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    /**
     * 分页查询支付订单列表（透传 payment）。
     *
     * <p>分页形状对齐 admin 现有列表端点（与 {@code /apps} 同形）：{@code Pageable} 0-based 页码 +1
     * 传入客户端（客户端约定 1-based），响应沿用 payment 回显的 {@code total/page/size}。</p>
     */
    public PageResponse<PaymentOrderSummaryResponse> list(PaymentOrderQuery query, Pageable pageable) {
        // query（北向 controller 绑定）→ wire（出站载荷），与 AppManagementAppService 把 query 拆成 wire 参数同位
        var filter = new PaymentOrderListWireRequest(
                query.paymentOrderNo(), query.businessOrderNo(), query.businessSystemName(),
                query.statuses(), query.payMode(), query.accessType(), query.paymentChannel(),
                query.amountMin(), query.amountMax(),
                query.createdAtFrom(), query.createdAtTo(), query.paidAtFrom(), query.paidAtTo());
        PageResponse<PaymentOrderWireResponse> page;
        try {
            page = paymentClient.listPayments(
                    filter,
                    pageable.getPageNumber() + 1,   // Spring Pageable 0-based → 客户端 1-based
                    pageable.getPageSize());
        } catch (OpenApiClientException e) {
            throw translatePaymentError(e);
        }

        var items = page.items().stream()
                .map(PaymentManagementAppService::toSummary)
                .toList();

        return new PageResponse<>(items, page.total(), page.page(), page.size());
    }

    private static PaymentOrderSummaryResponse toSummary(PaymentOrderWireResponse wire) {
        return new PaymentOrderSummaryResponse(
                wire.paymentOrderNo(), wire.businessOrderNo(), wire.businessSystemName(),
                wire.status(), wire.amount(), wire.payMode(), wire.accessType(),
                wire.paymentChannel(), wire.paidAt(), wire.createdAt());
    }

    /**
     * 分页查询退款订单列表（透传 payment）。
     *
     * <p>分页形状对齐 admin 现有列表端点（与 {@code /apps}、{@code /payments} 同形）：{@code Pageable}
     * 0-based 页码 +1 传入客户端（客户端约定 1-based），响应沿用 payment 回显的 {@code total/page/size}。</p>
     */
    public PageResponse<RefundOrderSummaryResponse> listRefunds(RefundOrderQuery query, Pageable pageable) {
        // query（北向 controller 绑定）→ wire（出站载荷），与 list 把 PaymentOrderQuery 拆成 wire 参数同位
        var filter = new RefundOrderListWireRequest(
                query.refundOrderNo(), query.paymentOrderNo(), query.businessOrderNo(),
                query.businessSystemName(), query.statuses(), query.auditType(), query.auditorId(),
                query.refundAmountMin(), query.refundAmountMax(),
                query.createdAtFrom(), query.createdAtTo());
        PageResponse<RefundOrderWireResponse> page;
        try {
            page = paymentClient.listRefunds(
                    filter,
                    pageable.getPageNumber() + 1,   // Spring Pageable 0-based → 客户端 1-based
                    pageable.getPageSize());
        } catch (OpenApiClientException e) {
            throw translatePaymentError(e);
        }

        var items = page.items().stream()
                .map(PaymentManagementAppService::toRefundSummary)
                .toList();

        return new PageResponse<>(items, page.total(), page.page(), page.size());
    }

    private static RefundOrderSummaryResponse toRefundSummary(RefundOrderWireResponse wire) {
        return new RefundOrderSummaryResponse(
                wire.refundOrderNo(), wire.paymentOrderNo(), wire.businessOrderNo(),
                wire.businessSystemName(), wire.status(), wire.refundAmount(),
                wire.auditType(), wire.auditorId(), wire.auditorName(),
                wire.auditedAt(), wire.createdAt());
    }

    /**
     * payment 下游错误翻译——按 HTTP 状态映射为 {@link DomainException}（携带 {@link BaseCodeMessage}），
     * 保留下游异常为 cause。供本上下文各调用点复用。
     *
     * <ul>
     *   <li>400 → {@link BaseCodeMessage#BAD_REQUEST}（查询参数非法）</li>
     *   <li>404 → {@link BaseCodeMessage#NOT_FOUND}（资源不存在，如详情/退款单未找到）</li>
     *   <li>409 → {@link BaseCodeMessage#CONFLICT}（状态冲突）</li>
     *   <li>其它（含 401/403 服务间鉴权失败、5xx）→ {@link BaseCodeMessage#THIRD_PARTY_ERROR}
     *       —— 运营侧已认证，服务间或下游故障统一对外为第三方错误</li>
     * </ul>
     */
    static DomainException translatePaymentError(OpenApiClientException e) {
        return switch (e.getStatusCode()) {
            case 400 -> new DomainException(BaseCodeMessage.BAD_REQUEST, e);
            case 404 -> new DomainException(BaseCodeMessage.NOT_FOUND, e);
            case 409 -> new DomainException(BaseCodeMessage.CONFLICT, e);
            default -> new DomainException(BaseCodeMessage.THIRD_PARTY_ERROR, e, e.getStatusCode());
        };
    }
}