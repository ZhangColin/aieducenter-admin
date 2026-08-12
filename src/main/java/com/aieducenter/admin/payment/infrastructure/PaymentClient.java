package com.aieducenter.admin.payment.infrastructure;

import com.aieducenter.admin.payment.application.dto.wire.AuditRefundWireRequest;
import com.aieducenter.admin.payment.application.dto.wire.GatewayHealthWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.OperationLogListWireRequest;
import com.aieducenter.admin.payment.application.dto.wire.OperationLogWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.OperationsAuditWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.OrderLifecycleWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.OrderStatusDistributionWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.PaymentLogListWireRequest;
import com.aieducenter.admin.payment.application.dto.wire.PaymentLogWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.PaymentOrderDetailWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.PaymentOrderListWireRequest;
import com.aieducenter.admin.payment.application.dto.wire.PaymentOrderWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.PaymentOverviewWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.RefundOrderDetailWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.RefundOrderListWireRequest;
import com.aieducenter.admin.payment.application.dto.wire.RefundOrderWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.ResendNotificationWireRequest;
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

    private static final TypeReference<PageResponse<PaymentLogWireResponse>> PAYMENT_LOG_PAGE_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<PageResponse<OperationLogWireResponse>> OPERATION_LOG_PAGE_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<PaymentOrderDetailWireResponse>> PAYMENT_DETAIL_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<RefundOrderDetailWireResponse>> REFUND_DETAIL_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<OrderLifecycleWireResponse>> LIFECYCLE_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<PaymentOverviewWireResponse>> PAYMENT_OVERVIEW_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<OrderStatusDistributionWireResponse>> STATUS_DISTRIBUTION_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<GatewayHealthWireResponse>> GATEWAY_HEALTH_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<OperationsAuditWireResponse>> OPERATIONS_AUDIT_TYPEREF =
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
     * 分页查询通道交互日志（透传 payment）——PaymentLog：与银行/通道网关的机机交互留痕。
     *
     * @param filter wire 层过滤参数（由应用层从 {@code PaymentLogQuery} 映射而来）
     * @param page   页码，<strong>1-based</strong>（应用层由 Spring {@code Pageable} 的 0-based 页码 +1 传入；
     *               此处 {@code page - 1} 还原为 payment 端 Spring {@code Pageable} 的 0-based）
     * @param size   每页大小
     * @return payment 返回的分页结果
     */
    public PageResponse<PaymentLogWireResponse> listPaymentLogs(PaymentLogListWireRequest filter, int page, int size) {
        // 入参 page 为 1-based，payment 端用 Spring Pageable 的 0-based，故 -1（与 listPayments/listRefunds 一致）。
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/api/v1/payment-logs?page=").append(page - 1)
                .append("&size=").append(size);
        appendParam(url, "paymentOrderNo", filter.paymentOrderNo());
        appendParam(url, "refundOrderNo", filter.refundOrderNo());
        if (filter.logTypes() != null && !filter.logTypes().isEmpty()) {
            for (String logType : filter.logTypes()) {
                appendParam(url, "logType", logType);
            }
        }
        appendParam(url, "bankInterface", filter.bankInterface());
        appendParam(url, "success", filter.success());
        appendParam(url, "returnCode", filter.returnCode());
        appendParam(url, "createdAtFrom", filter.createdAtFrom());
        appendParam(url, "createdAtTo", filter.createdAtTo());
        log.debug("PaymentClient.listPaymentLogs: {}", url);
        return openApiClient.get(url.toString(), PAYMENT_LOG_PAGE_TYPEREF);
    }

    /**
     * 分页查询订单操作记录（透传 payment）——OperationLog：行为者对订单的操作留痕。
     *
     * @param filter wire 层过滤参数（由应用层从 {@code OperationLogQuery} 映射而来）
     * @param page   页码，<strong>1-based</strong>（应用层由 Spring {@code Pageable} 的 0-based 页码 +1 传入；
     *               此处 {@code page - 1} 还原为 payment 端 Spring {@code Pageable} 的 0-based）
     * @param size   每页大小
     * @return payment 返回的分页结果
     */
    public PageResponse<OperationLogWireResponse> listOperationLogs(OperationLogListWireRequest filter, int page, int size) {
        // 入参 page 为 1-based，payment 端用 Spring Pageable 的 0-based，故 -1（与 listPayments/listRefunds 一致）。
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/api/v1/operation-logs?page=").append(page - 1)
                .append("&size=").append(size);
        appendParam(url, "targetType", filter.targetType());
        appendParam(url, "targetNo", filter.targetNo());
        appendParam(url, "operation", filter.operation());
        appendParam(url, "operatorId", filter.operatorId());
        appendParam(url, "operatorSystem", filter.operatorSystem());
        appendParam(url, "result", filter.result());
        // payment OperationLogQuery 的时间区间参数名为 createdAtStart/End（非 From/To），按组件名绑定，须对齐
        appendParam(url, "createdAtStart", filter.createdAtStart());
        appendParam(url, "createdAtEnd", filter.createdAtEnd());
        log.debug("PaymentClient.listOperationLogs: {}", url);
        return openApiClient.get(url.toString(), OPERATION_LOG_PAGE_TYPEREF);
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
     * 审核退款（透传 payment）——首个写端点，打通操作者身份透传范式。
     *
     * <p>请求体（{@link AuditRefundWireRequest}）承载审核决策 + 操作者身份（auditorId/auditorName 由应用层从
     * {@code RequestContext} 注入）；payment 落 {@code OperationLog}（auditType=MANUAL），admin 不本地记账。
     * 响应为审核后的退款单聚合（与详情同形）。</p>
     *
     * @param refundOrderNo 退款订单号
     * @param request       wire 层审核载荷（含决策 + 操作者身份）
     * @return payment 返回的审核后退款单聚合
     * @throws com.cartisan.openapi.client.OpenApiClientException payment 404（退款单不存在）、
     *         400（退款单非待审核状态）等透传，由应用层翻译
     */
    public RefundOrderDetailWireResponse auditRefund(String refundOrderNo, AuditRefundWireRequest request) {
        String url = baseUrl + "/api/v1/refunds/" + encode(refundOrderNo) + "/audit";
        log.debug("PaymentClient.auditRefund: {}", url);
        ApiResponse<RefundOrderDetailWireResponse> resp = openApiClient.post(url, request, REFUND_DETAIL_TYPEREF);
        return resp.data();
    }

    /**
     * 主动查行（透传 payment）——触发 payment 向银行查询并把本地状态对齐银行真相。
     *
     * <p>payment {@code POST /api/v1/payments/{paymentOrderNo}/query} 仅取路径参数（无请求体、不接收操作者身份），
     * 返回查询（可能已同步）后的支付单聚合（与 {@link #getPayment} 详情同形 {@code PaymentOrderResponse}）。
     * 主动查行的频控/审计归属 payment 侧，admin 仅按 {@code admin:payment:bank:query} 权限放行（issue #46）。</p>
     *
     * @param paymentOrderNo 支付订单号
     * @return payment 返回的查询后支付单聚合（与详情同形）
     * @throws com.cartisan.openapi.client.OpenApiClientException payment 404（订单不存在）、
     *         5xx（银行/通道不可达）等透传，由应用层翻译
     */
    public PaymentOrderDetailWireResponse queryPayment(String paymentOrderNo) {
        String url = baseUrl + "/api/v1/payments/" + encode(paymentOrderNo) + "/query";
        log.debug("PaymentClient.queryPayment: {}", url);
        // 无请求体：框架 OpenApiClient.post 对 null body 发空 body（POST 仍带 application/json），payment 端只读路径参数
        ApiResponse<PaymentOrderDetailWireResponse> resp = openApiClient.post(url, null, PAYMENT_DETAIL_TYPEREF);
        return resp.data();
    }

    /**
     * 重发支付结果通知（透传 payment）——补发漏投的结果通知到业务系统，<strong>不改订单状态</strong>。
     *
     * <p>payment {@code POST /api/v1/payments/{paymentOrderNo}/notifications/resend} 取路径参数 + 操作者身份
     * （{@link ResendNotificationWireRequest}，由应用层从 {@code RequestContext} 注入），重发投递并落
     * {@code OperationLog}（{@code operation=NOTIFY_RESEND}）；返回当前支付单聚合（与 {@link #getPayment}
     * 详情同形 {@code PaymentOrderResponse}，状态未变）。频控/投递重试/审计归属 payment 侧。</p>
     *
     * @param paymentOrderNo 支付订单号
     * @param request        wire 层操作者身份载荷（operatorId/operatorName）
     * @return payment 返回的当前支付单聚合（与详情同形，状态未变）
     * @throws com.cartisan.openapi.client.OpenApiClientException payment 404（订单不存在）、5xx（业务系统不可达）等透传，由应用层翻译
     */
    public PaymentOrderDetailWireResponse resendPaymentNotification(
            String paymentOrderNo, ResendNotificationWireRequest request) {
        String url = baseUrl + "/api/v1/payments/" + encode(paymentOrderNo) + "/notifications/resend";
        log.debug("PaymentClient.resendPaymentNotification: {}", url);
        ApiResponse<PaymentOrderDetailWireResponse> resp = openApiClient.post(url, request, PAYMENT_DETAIL_TYPEREF);
        return resp.data();
    }

    /**
     * 重发退款结果通知（透传 payment）——补发漏投的结果通知到业务系统，<strong>不改订单状态</strong>。
     *
     * <p>payment {@code POST /api/v1/refunds/{refundOrderNo}/notifications/resend} 取路径参数 + 操作者身份
     * （{@link ResendNotificationWireRequest}，由应用层从 {@code RequestContext} 注入），重发投递并落
     * {@code OperationLog}（{@code operation=NOTIFY_RESEND}）；返回当前退款单聚合（与 {@link #getRefund}
     * 详情同形，状态未变）。频控/投递重试/审计归属 payment 侧。</p>
     *
     * @param refundOrderNo 退款订单号
     * @param request       wire 层操作者身份载荷（operatorId/operatorName）
     * @return payment 返回的当前退款单聚合（与详情同形，状态未变）
     * @throws com.cartisan.openapi.client.OpenApiClientException payment 404（订单不存在）、5xx（业务系统不可达）等透传，由应用层翻译
     */
    public RefundOrderDetailWireResponse resendRefundNotification(
            String refundOrderNo, ResendNotificationWireRequest request) {
        String url = baseUrl + "/api/v1/refunds/" + encode(refundOrderNo) + "/notifications/resend";
        log.debug("PaymentClient.resendRefundNotification: {}", url);
        ApiResponse<RefundOrderDetailWireResponse> resp = openApiClient.post(url, request, REFUND_DETAIL_TYPEREF);
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

    /**
     * 查询支付总览统计（透传 payment）——支付/退款笔数·金额·成功率·净额 + 按时间分桶趋势。
     *
     * <p>一档统计（issue #37），admin 纯透传——不做 admin 侧聚合/重算（spec「仪表盘」）。</p>
     *
     * @return payment 返回的支付总览统计
     * @throws com.cartisan.openapi.client.OpenApiClientException payment 5xx 等透传，由应用层翻译
     */
    public PaymentOverviewWireResponse getPaymentOverview() {
        String url = baseUrl + "/api/v1/stats/payments/overview";
        log.debug("PaymentClient.getPaymentOverview: {}", url);
        ApiResponse<PaymentOverviewWireResponse> resp = openApiClient.get(url, PAYMENT_OVERVIEW_TYPEREF);
        return resp.data();
    }

    /**
     * 查询订单状态分布统计（透传 payment）——各状态在途笔数·金额 + 退款待审核积压。
     *
     * <p>一档统计（issue #37），admin 纯透传——不做 admin 侧聚合/重算（spec「仪表盘」）。</p>
     *
     * @return payment 返回的订单状态分布统计
     * @throws com.cartisan.openapi.client.OpenApiClientException payment 5xx 等透传，由应用层翻译
     */
    public OrderStatusDistributionWireResponse getOrderStatusDistribution() {
        String url = baseUrl + "/api/v1/stats/orders/status-distribution";
        log.debug("PaymentClient.getOrderStatusDistribution: {}", url);
        ApiResponse<OrderStatusDistributionWireResponse> resp = openApiClient.get(url, STATUS_DISTRIBUTION_TYPEREF);
        return resp.data();
    }

    /**
     * 查询通道健康统计（透传 payment）——各银行接口调用次数·成功率·平均耗时·返回码分布。
     *
     * <p>一档统计（issue #37），admin 纯透传——不做 admin 侧聚合/重算（spec「仪表盘」）。</p>
     *
     * @return payment 返回的通道健康统计
     * @throws com.cartisan.openapi.client.OpenApiClientException payment 5xx 等透传，由应用层翻译
     */
    public GatewayHealthWireResponse getGatewayHealth() {
        String url = baseUrl + "/api/v1/stats/gateway/health";
        log.debug("PaymentClient.getGatewayHealth: {}", url);
        ApiResponse<GatewayHealthWireResponse> resp = openApiClient.get(url, GATEWAY_HEALTH_TYPEREF);
        return resp.data();
    }

    /**
     * 查询审核统计（透传 payment）——审核笔数·通过率·平均审核时长 + 按审核人聚合。
     *
     * <p>一档统计（issue #37），admin 纯透传——不做 admin 侧聚合/重算（spec「仪表盘」）。</p>
     *
     * @return payment 返回的审核统计
     * @throws com.cartisan.openapi.client.OpenApiClientException payment 5xx 等透传，由应用层翻译
     */
    public OperationsAuditWireResponse getOperationsAudit() {
        String url = baseUrl + "/api/v1/stats/operations/audit";
        log.debug("PaymentClient.getOperationsAudit: {}", url);
        ApiResponse<OperationsAuditWireResponse> resp = openApiClient.get(url, OPERATIONS_AUDIT_TYPEREF);
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
