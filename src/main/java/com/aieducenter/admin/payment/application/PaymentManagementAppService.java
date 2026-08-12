package com.aieducenter.admin.payment.application;

import com.aieducenter.admin.payment.application.dto.command.RefundAuditCommand;
import com.aieducenter.admin.payment.application.dto.query.OperationLogQuery;
import com.aieducenter.admin.payment.application.dto.query.PaymentLogQuery;
import com.aieducenter.admin.payment.application.dto.query.PaymentOrderQuery;
import com.aieducenter.admin.payment.application.dto.query.RefundOrderQuery;
import com.aieducenter.admin.payment.application.dto.response.GatewayHealthResponse;
import com.aieducenter.admin.payment.application.dto.response.OperationLogSummaryResponse;
import com.aieducenter.admin.payment.application.dto.response.OperationsAuditResponse;
import com.aieducenter.admin.payment.application.dto.response.OrderLifecycleResponse;
import com.aieducenter.admin.payment.application.dto.response.OrderStatusDistributionResponse;
import com.aieducenter.admin.payment.application.dto.response.PaymentLogSummaryResponse;
import com.aieducenter.admin.payment.application.dto.response.PaymentOrderDetailResponse;
import com.aieducenter.admin.payment.application.dto.response.PaymentOverviewResponse;
import com.aieducenter.admin.payment.application.dto.response.PaymentOrderSummaryResponse;
import com.aieducenter.admin.payment.application.dto.response.RefundOrderDetailResponse;
import com.aieducenter.admin.payment.application.dto.response.RefundOrderSummaryResponse;
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
import com.aieducenter.admin.payment.infrastructure.PaymentClient;
import com.cartisan.core.exception.BaseCodeMessage;
import com.cartisan.core.exception.DomainException;
import com.cartisan.openapi.client.OpenApiClientException;
import com.cartisan.web.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

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
     * 分页查询通道交互日志（透传 payment）——PaymentLog：与银行/通道网关的机机交互留痕。
     *
     * <p>分页形状对齐 admin 现有列表端点（与 {@code /apps}、{@code /payments}、{@code /refunds} 同形）：
     * {@code Pageable} 0-based 页码 +1 传入客户端（客户端约定 1-based），响应沿用 payment 回显的
     * {@code total/page/size}。payment 的 {@code PaymentLog} 全字段为基础类型（无枚举语义），admin 原值透传。</p>
     */
    public PageResponse<PaymentLogSummaryResponse> listPaymentLogs(PaymentLogQuery query, Pageable pageable) {
        // query（北向 controller 绑定）→ wire（出站载荷），与 list/listRefunds 把 query 拆成 wire 参数同位
        var filter = new PaymentLogListWireRequest(
                query.paymentOrderNo(), query.refundOrderNo(), query.logTypes(),
                query.bankInterface(), query.success(), query.returnCode(),
                query.createdAtFrom(), query.createdAtTo());
        PageResponse<PaymentLogWireResponse> page;
        try {
            page = paymentClient.listPaymentLogs(
                    filter,
                    pageable.getPageNumber() + 1,   // Spring Pageable 0-based → 客户端 1-based
                    pageable.getPageSize());
        } catch (OpenApiClientException e) {
            throw translatePaymentError(e);
        }

        var items = page.items().stream()
                .map(PaymentManagementAppService::toPaymentLogSummary)
                .toList();

        return new PageResponse<>(items, page.total(), page.page(), page.size());
    }

    private static PaymentLogSummaryResponse toPaymentLogSummary(PaymentLogWireResponse wire) {
        return new PaymentLogSummaryResponse(
                wire.id(), wire.paymentOrderNo(), wire.refundOrderNo(),
                wire.logType(), wire.bankCode(), wire.bankInterface(),
                wire.httpStatus(), wire.returnCode(), wire.returnMsg(),
                wire.executionTime(), wire.success(), wire.errorMessage(),
                wire.createdAt());
    }

    /**
     * 分页查询订单操作记录（透传 payment）——OperationLog：行为者对订单的操作留痕。
     *
     * <p>分页形状对齐 admin 现有列表端点（与 {@code /apps}、{@code /payments}、{@code /refunds} 同形）：
     * {@code Pageable} 0-based 页码 +1 传入客户端（客户端约定 1-based），响应沿用 payment 回显的
     * {@code total/page/size}。</p>
     */
    public PageResponse<OperationLogSummaryResponse> listOperationLogs(OperationLogQuery query, Pageable pageable) {
        // query（北向 controller 绑定）→ wire（出站载荷），与 list/listRefunds 把 query 拆成 wire 参数同位。
        // 注意时间区间字段名映射：北向 query 用 createdAtFrom/To（admin 统一命名），wire 用 createdAtStart/End
        // （对齐 payment OperationLogQuery 的参数名特例，见 OperationLogListWireRequest javadoc）。
        var filter = new OperationLogListWireRequest(
                query.targetType(), query.targetNo(), query.operation(),
                query.operatorId(), query.operatorSystem(), query.result(),
                query.createdAtFrom(), query.createdAtTo());
        PageResponse<OperationLogWireResponse> page;
        try {
            page = paymentClient.listOperationLogs(
                    filter,
                    pageable.getPageNumber() + 1,   // Spring Pageable 0-based → 客户端 1-based
                    pageable.getPageSize());
        } catch (OpenApiClientException e) {
            throw translatePaymentError(e);
        }

        var items = page.items().stream()
                .map(PaymentManagementAppService::toOperationLogSummary)
                .toList();

        return new PageResponse<>(items, page.total(), page.page(), page.size());
    }

    private static OperationLogSummaryResponse toOperationLogSummary(OperationLogWireResponse wire) {
        return new OperationLogSummaryResponse(
                wire.id(), wire.targetType(), wire.targetNo(),
                wire.operation(), wire.operatorId(), wire.operatorName(),
                wire.operatorSystem(), wire.result(), wire.remark(),
                wire.createdAt());
    }

    /**
     * 查询支付订单详情（透传 payment）——完整聚合投影。
     *
     * <p>payment 404（订单不存在）翻译为 {@link BaseCodeMessage#NOT_FOUND}（404）。</p>
     */
    public PaymentOrderDetailResponse getPaymentDetail(String paymentOrderNo) {
        PaymentOrderDetailWireResponse wire;
        try {
            wire = paymentClient.getPayment(paymentOrderNo);
        } catch (OpenApiClientException e) {
            throw translatePaymentError(e);
        }
        return toPaymentDetail(wire);
    }

    private static PaymentOrderDetailResponse toPaymentDetail(PaymentOrderDetailWireResponse wire) {
        return new PaymentOrderDetailResponse(
                wire.paymentOrderNo(), wire.businessOrderNo(), wire.businessSystemName(),
                wire.status(), wire.amount(), wire.payMode(), wire.accessType(),
                wire.paymentChannel(), wire.paidAt(), wire.createdAt());
    }

    /**
     * 查询退款订单详情（透传 payment）——完整聚合投影。
     *
     * <p>payment 404（订单不存在）翻译为 {@link BaseCodeMessage#NOT_FOUND}（404）。</p>
     */
    public RefundOrderDetailResponse getRefundDetail(String refundOrderNo) {
        RefundOrderDetailWireResponse wire;
        try {
            wire = paymentClient.getRefund(refundOrderNo);
        } catch (OpenApiClientException e) {
            throw translatePaymentError(e);
        }
        return toRefundDetail(wire);
    }

    private static RefundOrderDetailResponse toRefundDetail(RefundOrderDetailWireResponse wire) {
        return new RefundOrderDetailResponse(
                wire.refundOrderNo(), wire.paymentOrderNo(), wire.businessOrderNo(),
                wire.businessSystemName(), wire.status(), wire.refundAmount(),
                wire.auditType(), wire.auditorId(), wire.auditorName(),
                wire.auditedAt(), wire.createdAt());
    }

    /**
     * 审核退款（透传 payment）——首个写端点，打通操作者身份透传范式。
     *
     * <p>运营人员的审核决策（{@code agreed}）来自前端 {@link RefundAuditCommand}，审核人身份
     * （{@code auditorId} / {@code auditorName}）由 controller 从 {@code RequestContext} 读取后
     * 显式传入——admin 应用层<strong>不</strong>读 {@code RequestContext}、不触 Sa-Token/DB，
     * 身份来源对方法签名可见、可测（零 Sa-Token/零 DB/零新注解）。payment 落 {@code OperationLog}
     * （auditType=MANUAL），admin 不本地记账。</p>
     *
     * <p>错误翻译（复用 {@link #translatePaymentError}）：payment 404（退款单不存在）⟹
     * {@link BaseCodeMessage#NOT_FOUND}；payment 400（退款单非待审核状态）⟹
     * {@link BaseCodeMessage#BAD_REQUEST}。</p>
     *
     * @param refundOrderNo 退款订单号
     * @param command       前端审核决策（agreed + remark，不含审核人身份）
     * @param auditorId     审核人 ID（RequestContext.getUserId()）
     * @param auditorName   审核人姓名（RequestContext.getUserName()）
     * @return payment 返回的审核后退款单聚合（与详情同形）
     */
    public RefundOrderDetailResponse auditRefund(String refundOrderNo, RefundAuditCommand command,
                                                 Long auditorId, String auditorName) {
        // 决策来自前端、身份来自 RequestContext——二者拼成 payment 的完整 wire 载荷
        var wireRequest = new AuditRefundWireRequest(
                auditorId, auditorName, command.agreed(), command.remark());
        RefundOrderDetailWireResponse wire;
        try {
            wire = paymentClient.auditRefund(refundOrderNo, wireRequest);
        } catch (OpenApiClientException e) {
            throw translatePaymentError(e);
        }
        return toRefundDetail(wire);
    }

    /**
     * 主动查行（透传 payment）——运营人员触发 payment 向银行查询并把本地状态对齐银行真相。
     *
     * <p>无请求体、无操作者身份透传（payment {@code POST /payments/{no}/query} 仅取路径参数，不接收 auditor）；
     * admin 仅按 {@code admin:payment:bank:query} 权限放行该写操作（issue #46）。返回查询后的支付单聚合
     * （与 {@link #getPaymentDetail} 详情同形），复用 {@link #toPaymentDetail} 映射。</p>
     *
     * <p>错误翻译（复用 {@link #translatePaymentError}）：payment 404（订单不存在）⟹
     * {@link BaseCodeMessage#NOT_FOUND}；payment 5xx（银行/通道不可达）⟹
     * {@link BaseCodeMessage#THIRD_PARTY_ERROR}。</p>
     *
     * @param paymentOrderNo 支付订单号
     * @return payment 返回的查询后支付单聚合（与详情同形）
     */
    public PaymentOrderDetailResponse queryPayment(String paymentOrderNo) {
        PaymentOrderDetailWireResponse wire;
        try {
            wire = paymentClient.queryPayment(paymentOrderNo);
        } catch (OpenApiClientException e) {
            throw translatePaymentError(e);
        }
        return toPaymentDetail(wire);
    }

    /**
     * 重发支付结果通知（透传 payment）——补发漏投的结果通知到业务系统，<strong>不改订单状态</strong>（payment ADR-0001）。
     *
     * <p>复用 T4 退款审核的操作者身份透传范式：操作者身份（{@code operatorId} / {@code operatorName}）由 controller
     * 从 {@code RequestContext} 读取后显式传入——admin 应用层<strong>不</strong>读 {@code RequestContext}、不触 Sa-Token/DB，
     * 身份来源对方法签名可见、可测（零 Sa-Token/零 DB/零新注解）。前端<strong>不</strong>发送请求体（重发无决策意图，
     * 不像退款审核带 {@code agreed}），仅服务端把身份拼成 payment 的 wire 载荷。payment 据此落 {@code OperationLog}
     * （{@code operation=NOTIFY_RESEND}），admin 不本地记账。</p>
     *
     * <p>错误翻译（复用 {@link #translatePaymentError}）：payment 404（订单不存在）⟹
     * {@link BaseCodeMessage#NOT_FOUND}；payment 5xx（业务系统不可达）⟹
     * {@link BaseCodeMessage#THIRD_PARTY_ERROR}。</p>
     *
     * @param paymentOrderNo 支付订单号
     * @param operatorId     操作者 ID（RequestContext.getUserId()）
     * @param operatorName   操作者姓名（RequestContext.getUserName()）
     * @return payment 返回的当前支付单聚合（与详情同形，状态未变）
     */
    public PaymentOrderDetailResponse resendPaymentNotification(String paymentOrderNo,
                                                                 Long operatorId, String operatorName) {
        // 操作者身份来自 RequestContext——拼成 payment 的 wire 载荷（无前端决策，body 仅承载身份）
        var wireRequest = new ResendNotificationWireRequest(operatorId, operatorName);
        PaymentOrderDetailWireResponse wire;
        try {
            wire = paymentClient.resendPaymentNotification(paymentOrderNo, wireRequest);
        } catch (OpenApiClientException e) {
            throw translatePaymentError(e);
        }
        return toPaymentDetail(wire);
    }

    /**
     * 重发退款结果通知（透传 payment）——补发漏投的结果通知到业务系统，<strong>不改订单状态</strong>（payment ADR-0001）。
     *
     * <p>复用 T4 退款审核的操作者身份透传范式：操作者身份（{@code operatorId} / {@code operatorName}）由 controller
     * 从 {@code RequestContext} 读取后显式传入（零 Sa-Token/零 DB/零新注解）。前端不发送请求体，仅服务端把身份拼成
     * payment 的 wire 载荷。payment 据此落 {@code OperationLog}（{@code operation=NOTIFY_RESEND}），admin 不本地记账。</p>
     *
     * <p>错误翻译（复用 {@link #translatePaymentError}）：payment 404（订单不存在）⟹
     * {@link BaseCodeMessage#NOT_FOUND}；payment 5xx（业务系统不可达）⟹
     * {@link BaseCodeMessage#THIRD_PARTY_ERROR}。</p>
     *
     * @param refundOrderNo 退款订单号
     * @param operatorId    操作者 ID（RequestContext.getUserId()）
     * @param operatorName  操作者姓名（RequestContext.getUserName()）
     * @return payment 返回的当前退款单聚合（与详情同形，状态未变）
     */
    public RefundOrderDetailResponse resendRefundNotification(String refundOrderNo,
                                                               Long operatorId, String operatorName) {
        var wireRequest = new ResendNotificationWireRequest(operatorId, operatorName);
        RefundOrderDetailWireResponse wire;
        try {
            wire = paymentClient.resendRefundNotification(refundOrderNo, wireRequest);
        } catch (OpenApiClientException e) {
            throw translatePaymentError(e);
        }
        return toRefundDetail(wire);
    }

    /**
     * 查询订单生命周期（透传 payment）——payment 已合并（PaymentLog + OperationLog 按时间排序）的时间线。
     *
     * <p>合并在 payment 完成（ADR-0002 读模型），admin 透传不改序、不本地合并——避免双逻辑不一致。
     * payment 404（订单不存在）翻译为 {@link BaseCodeMessage#NOT_FOUND}（404）。</p>
     */
    public OrderLifecycleResponse getLifecycle(String orderNo) {
        OrderLifecycleWireResponse wire;
        try {
            wire = paymentClient.getLifecycle(orderNo);
        } catch (OpenApiClientException e) {
            throw translatePaymentError(e);
        }
        List<OrderLifecycleResponse.LifecycleEvent> events =
                wire.events() == null ? List.of()
                        : wire.events().stream().map(PaymentManagementAppService::toLifecycleEvent).toList();
        return new OrderLifecycleResponse(wire.orderNo(), events);
    }

    private static OrderLifecycleResponse.LifecycleEvent toLifecycleEvent(
            OrderLifecycleWireResponse.LifecycleEventWireResponse wire) {
        return new OrderLifecycleResponse.LifecycleEvent(
                wire.source(), wire.createdAt(),
                wire.logType(), wire.paymentOrderNo(), wire.refundOrderNo(),
                wire.bankInterface(), wire.returnCode(), wire.returnMsg(),
                wire.executionTime(), wire.success(),
                wire.targetType(), wire.targetNo(), wire.operation(),
                wire.operatorId(), wire.operatorName(), wire.operatorSystem(),
                wire.result(), wire.remark());
    }

    /**
     * 查询支付总览统计（透传 payment）——运营看板 tier-1。
     *
     * <p>支付/退款笔数·金额·成功率·净额 + 按时间分桶趋势，聚合归 payment（一档统计，issue #37）。
     * admin 纯透传——不做 admin 侧聚合/重算（spec「仪表盘」）。</p>
     */
    public PaymentOverviewResponse getPaymentOverview() {
        PaymentOverviewWireResponse wire;
        try {
            wire = paymentClient.getPaymentOverview();
        } catch (OpenApiClientException e) {
            throw translatePaymentError(e);
        }
        return toPaymentOverview(wire);
    }

    private static PaymentOverviewResponse toPaymentOverview(PaymentOverviewWireResponse wire) {
        List<PaymentOverviewResponse.TrendBucket> trend =
                wire.trend() == null ? List.of()
                        : wire.trend().stream().map(PaymentManagementAppService::toTrendBucket).toList();
        return new PaymentOverviewResponse(
                wire.paymentCount(), wire.paymentAmount(),
                wire.refundCount(), wire.refundAmount(),
                wire.successRate(), wire.netAmount(), trend);
    }

    private static PaymentOverviewResponse.TrendBucket toTrendBucket(PaymentOverviewWireResponse.TrendBucketWireResponse wire) {
        return new PaymentOverviewResponse.TrendBucket(
                wire.bucket(), wire.paymentCount(), wire.paymentAmount(),
                wire.refundCount(), wire.refundAmount());
    }

    /**
     * 查询订单状态分布统计（透传 payment）——运营看板 tier-1。
     *
     * <p>各状态在途笔数·金额（支付 + 退款两列）+ 退款待审核积压，聚合归 payment（一档统计，issue #37）。
     * admin 纯透传——不做 admin 侧聚合/重算（spec「仪表盘」）。</p>
     */
    public OrderStatusDistributionResponse getOrderStatusDistribution() {
        OrderStatusDistributionWireResponse wire;
        try {
            wire = paymentClient.getOrderStatusDistribution();
        } catch (OpenApiClientException e) {
            throw translatePaymentError(e);
        }
        return toOrderStatusDistribution(wire);
    }

    private static OrderStatusDistributionResponse toOrderStatusDistribution(OrderStatusDistributionWireResponse wire) {
        List<OrderStatusDistributionResponse.StatusBucket> paymentStatuses =
                wire.paymentStatuses() == null ? List.of()
                        : wire.paymentStatuses().stream().map(PaymentManagementAppService::toStatusBucket).toList();
        List<OrderStatusDistributionResponse.StatusBucket> refundStatuses =
                wire.refundStatuses() == null ? List.of()
                        : wire.refundStatuses().stream().map(PaymentManagementAppService::toStatusBucket).toList();
        return new OrderStatusDistributionResponse(
                paymentStatuses, refundStatuses, wire.refundPendingAuditCount());
    }

    private static OrderStatusDistributionResponse.StatusBucket toStatusBucket(
            OrderStatusDistributionWireResponse.StatusBucketWireResponse wire) {
        return new OrderStatusDistributionResponse.StatusBucket(wire.status(), wire.count(), wire.amount());
    }

    /**
     * 查询通道健康统计（透传 payment）——运营看板 tier-1。
     *
     * <p>各银行接口调用次数·成功率·平均耗时·返回码分布，聚合归 payment（一档统计，issue #37）。
     * admin 纯透传——不做 admin 侧聚合/重算（spec「仪表盘」）。</p>
     */
    public GatewayHealthResponse getGatewayHealth() {
        GatewayHealthWireResponse wire;
        try {
            wire = paymentClient.getGatewayHealth();
        } catch (OpenApiClientException e) {
            throw translatePaymentError(e);
        }
        return toGatewayHealth(wire);
    }

    private static GatewayHealthResponse toGatewayHealth(GatewayHealthWireResponse wire) {
        List<GatewayHealthResponse.BankInterfaceStat> bankInterfaces =
                wire.bankInterfaces() == null ? List.of()
                        : wire.bankInterfaces().stream().map(PaymentManagementAppService::toBankInterfaceStat).toList();
        return new GatewayHealthResponse(bankInterfaces);
    }

    private static GatewayHealthResponse.BankInterfaceStat toBankInterfaceStat(
            GatewayHealthWireResponse.BankInterfaceStatWireResponse wire) {
        List<GatewayHealthResponse.BankInterfaceStat.ReturnCodeStat> returnCodes =
                wire.returnCodes() == null ? List.of()
                        : wire.returnCodes().stream().map(PaymentManagementAppService::toReturnCodeStat).toList();
        return new GatewayHealthResponse.BankInterfaceStat(
                wire.bankInterface(), wire.callCount(), wire.successCount(),
                wire.successRate(), wire.avgExecutionTime(), returnCodes);
    }

    private static GatewayHealthResponse.BankInterfaceStat.ReturnCodeStat toReturnCodeStat(
            GatewayHealthWireResponse.BankInterfaceStatWireResponse.ReturnCodeStatWireResponse wire) {
        return new GatewayHealthResponse.BankInterfaceStat.ReturnCodeStat(wire.returnCode(), wire.count());
    }

    /**
     * 查询审核统计（透传 payment）——运营看板 tier-1。
     *
     * <p>审核笔数·通过率·平均审核时长 + 按审核人聚合，聚合归 payment（一档统计，issue #37）。
     * admin 纯透传——不做 admin 侧聚合/重算（spec「仪表盘」）。</p>
     */
    public OperationsAuditResponse getOperationsAudit() {
        OperationsAuditWireResponse wire;
        try {
            wire = paymentClient.getOperationsAudit();
        } catch (OpenApiClientException e) {
            throw translatePaymentError(e);
        }
        return toOperationsAudit(wire);
    }

    private static OperationsAuditResponse toOperationsAudit(OperationsAuditWireResponse wire) {
        List<OperationsAuditResponse.AuditorStat> auditors =
                wire.auditors() == null ? List.of()
                        : wire.auditors().stream().map(PaymentManagementAppService::toAuditorStat).toList();
        return new OperationsAuditResponse(
                wire.auditCount(), wire.approvalRate(), wire.avgAuditDurationSeconds(), auditors);
    }

    private static OperationsAuditResponse.AuditorStat toAuditorStat(
            OperationsAuditWireResponse.AuditorStatWireResponse wire) {
        return new OperationsAuditResponse.AuditorStat(
                wire.auditorId(), wire.auditorName(), wire.auditCount(),
                wire.approvedCount(), wire.approvalRate(), wire.avgAuditDurationSeconds());
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
