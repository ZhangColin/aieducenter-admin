package com.aieducenter.admin.payment.infrastructure;

import com.aieducenter.admin.payment.application.dto.wire.OrderLifecycleWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.PaymentOrderDetailWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.PaymentOrderListWireRequest;
import com.aieducenter.admin.payment.application.dto.wire.PaymentOrderWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.RefundOrderDetailWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.RefundOrderListWireRequest;
import com.aieducenter.admin.payment.application.dto.wire.RefundOrderWireResponse;
import com.cartisan.openapi.client.OpenApiClient;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.response.PageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * payment 签名 HTTP 客户端——封装 {@link OpenApiClient}，屏蔽 wire 层细节。
 *
 * <p>所有对 payment 能力域的调用都经此客户端发起，自动签名（admin 自身 apiKey/admin-console，
 * 复用框架 {@link OpenApiClient} 既有 HMAC-SHA256 签名，无需新凭据——见 ADR-0007）。</p>
 *
 * <p>为 infrastructure 包内裸 {@code @Component}（BFF 出站客户端，不走 {@code @Port/@Adapter}，
 * 详见 ADR-0007「admin BFF 出站客户端为裸 @Component」）。</p>
 *
 * @since 0.1.0
 */
@Component
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

    private static final TypeReference<PageResponse<PaymentOrderWireResponse>> PAYMENT_PAGE_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<PageResponse<RefundOrderWireResponse>> REFUND_PAGE_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<PaymentOrderDetailWireResponse>> PAYMENT_DETAIL_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<RefundOrderDetailWireResponse>> REFUND_DETAIL_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<OrderLifecycleWireResponse>> LIFECYCLE_TYPEREF =
            new TypeReference<>() {};

    private final OpenApiClient openApiClient;
    private final String baseUrl;

    public PaymentClient(OpenApiClient openApiClient,
                         @Value("${admin.payment.base-url}") String baseUrl) {
        this.openApiClient = openApiClient;
        this.baseUrl = baseUrl;
    }

    /**
     * 分页查询支付订单列表（透传 payment）。
     *
     * @param filter wire 层过滤参数（由应用层从 {@code PaymentOrderQuery} 映射而来）
     * @param page   页码，<strong>1-based</strong>（应用层由 Spring {@code Pageable} 的 0-based 页码 +1 传入；
     *               此处 {@code page - 1} 还原为 payment 端 Spring {@code Pageable} 的 0-based）
     * @param size   每页大小
     * @return payment 返回的分页结果
     */
    public PageResponse<PaymentOrderWireResponse> listPayments(PaymentOrderListWireRequest filter, int page, int size) {
        // 入参 page 为 1-based，payment 端用 Spring Pageable 的 0-based，故 -1（与 AppRegistryClient.listApps 一致）。
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/api/v1/payments?page=").append(page - 1)
                .append("&size=").append(size);
        appendParam(url, "paymentOrderNo", filter.paymentOrderNo());
        appendParam(url, "businessOrderNo", filter.businessOrderNo());
        appendParam(url, "businessSystemName", filter.businessSystemName());
        if (filter.statuses() != null && !filter.statuses().isEmpty()) {
            for (String status : filter.statuses()) {
                appendParam(url, "status", status);
            }
        }
        appendParam(url, "payMode", filter.payMode());
        appendParam(url, "accessType", filter.accessType());
        appendParam(url, "paymentChannel", filter.paymentChannel());
        appendParam(url, "amountMin", filter.amountMin());
        appendParam(url, "amountMax", filter.amountMax());
        appendParam(url, "createdAtFrom", filter.createdAtFrom());
        appendParam(url, "createdAtTo", filter.createdAtTo());
        appendParam(url, "paidAtFrom", filter.paidAtFrom());
        appendParam(url, "paidAtTo", filter.paidAtTo());
        log.debug("PaymentClient.listPayments: {}", url);
        return openApiClient.get(url.toString(), PAYMENT_PAGE_TYPEREF);
    }

    /**
     * 分页查询退款订单列表（透传 payment）。
     *
     * @param filter wire 层过滤参数（由应用层从 {@code RefundOrderQuery} 映射而来）
     * @param page   页码，<strong>1-based</strong>（应用层由 Spring {@code Pageable} 的 0-based 页码 +1 传入；
     *               此处 {@code page - 1} 还原为 payment 端 Spring {@code Pageable} 的 0-based）
     * @param size   每页大小
     * @return payment 返回的分页结果
     */
    public PageResponse<RefundOrderWireResponse> listRefunds(RefundOrderListWireRequest filter, int page, int size) {
        // 入参 page 为 1-based，payment 端用 Spring Pageable 的 0-based，故 -1（与 listPayments / AppRegistryClient 一致）。
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/api/v1/refunds?page=").append(page - 1)
                .append("&size=").append(size);
        appendParam(url, "refundOrderNo", filter.refundOrderNo());
        appendParam(url, "paymentOrderNo", filter.paymentOrderNo());
        appendParam(url, "businessOrderNo", filter.businessOrderNo());
        appendParam(url, "businessSystemName", filter.businessSystemName());
        if (filter.statuses() != null && !filter.statuses().isEmpty()) {
            for (String status : filter.statuses()) {
                appendParam(url, "status", status);
            }
        }
        appendParam(url, "auditType", filter.auditType());
        appendParam(url, "auditorId", filter.auditorId());
        appendParam(url, "refundAmountMin", filter.refundAmountMin());
        appendParam(url, "refundAmountMax", filter.refundAmountMax());
        appendParam(url, "createdAtFrom", filter.createdAtFrom());
        appendParam(url, "createdAtTo", filter.createdAtTo());
        log.debug("PaymentClient.listRefunds: {}", url);
        return openApiClient.get(url.toString(), REFUND_PAGE_TYPEREF);
    }

    /**
     * 查询支付订单详情（透传 payment）。
     *
     * @param paymentOrderNo 支付订单号
     * @return payment 返回的支付订单聚合详情
     * @throws com.cartisan.openapi.client.OpenApiClientException payment 404（订单不存在）等透传，由应用层翻译
     */
    public PaymentOrderDetailWireResponse getPayment(String paymentOrderNo) {
        String url = baseUrl + "/api/v1/payments/" + encode(paymentOrderNo);
        log.debug("PaymentClient.getPayment: {}", url);
        ApiResponse<PaymentOrderDetailWireResponse> resp = openApiClient.get(url, PAYMENT_DETAIL_TYPEREF);
        return resp.data();
    }

    /**
     * 查询退款订单详情（透传 payment）。
     *
     * @param refundOrderNo 退款订单号
     * @return payment 返回的退款订单聚合详情
     * @throws com.cartisan.openapi.client.OpenApiClientException payment 404（订单不存在）等透传，由应用层翻译
     */
    public RefundOrderDetailWireResponse getRefund(String refundOrderNo) {
        String url = baseUrl + "/api/v1/refunds/" + encode(refundOrderNo);
        log.debug("PaymentClient.getRefund: {}", url);
        ApiResponse<RefundOrderDetailWireResponse> resp = openApiClient.get(url, REFUND_DETAIL_TYPEREF);
        return resp.data();
    }

    /**
     * 查询订单生命周期（透传 payment）。
     *
     * <p>payment 侧按 {@code orderNo} 把 PaymentLog + OperationLog union 后按时间排序返回
     * （payment ADR-0002 读模型）；admin 透传此<strong>已合并</strong>的时间线，不本地合并。</p>
     *
     * @param orderNo 订单号（支付单号或退款单号）
     * @return payment 返回的已合并生命周期时间线
     * @throws com.cartisan.openapi.client.OpenApiClientException payment 404（订单不存在）等透传，由应用层翻译
     */
    public OrderLifecycleWireResponse getLifecycle(String orderNo) {
        String url = baseUrl + "/api/v1/orders/" + encode(orderNo) + "/lifecycle";
        log.debug("PaymentClient.getLifecycle: {}", url);
        ApiResponse<OrderLifecycleWireResponse> resp = openApiClient.get(url, LIFECYCLE_TYPEREF);
        return resp.data();
    }

    private static void appendParam(StringBuilder url, String name, Object value) {
        if (value != null) {
            url.append('&').append(name).append('=').append(encode(value.toString()));
        }
    }

    private static String encode(String value) {
        // 与 AppRegistryClient 一致：简单 URL 编码，避免特殊字符问题
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
