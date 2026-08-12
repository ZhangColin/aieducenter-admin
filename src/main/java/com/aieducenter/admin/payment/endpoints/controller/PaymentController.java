package com.aieducenter.admin.payment.endpoints.controller;

import com.aieducenter.admin.constants.AdminScopes;
import com.aieducenter.admin.payment.application.PaymentManagementAppService;
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
import com.cartisan.core.context.RequestContext;
import com.cartisan.security.annotation.RequireAuth;
import com.cartisan.security.annotation.RequirePermission;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付管理控制器——BFF 读路径，聚合 payment 能力域。
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/admin/payment")
@Validated
@Tag(name = "Admin Payment", description = "支付管理")
public class PaymentController {

    private final PaymentManagementAppService paymentAppService;

    public PaymentController(PaymentManagementAppService paymentAppService) {
        this.paymentAppService = paymentAppService;
    }

    @GetMapping("/payments")
    @RequireAuth
    @RequirePermission(
            value = "admin:payment:read",
            name = "支付管理 / 查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "分页查询支付订单列表")
    public ApiResponse<PageResponse<PaymentOrderSummaryResponse>> listPayments(
            PaymentOrderQuery query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(paymentAppService.list(query, pageable));
    }

    @GetMapping("/refunds")
    @RequireAuth
    @RequirePermission(
            value = "admin:payment:read",
            name = "支付管理 / 查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "分页查询退款订单列表")
    public ApiResponse<PageResponse<RefundOrderSummaryResponse>> listRefunds(
            RefundOrderQuery query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(paymentAppService.listRefunds(query, pageable));
    }

    @GetMapping("/payment-logs")
    @RequireAuth
    @RequirePermission(
            value = "admin:payment:read",
            name = "支付管理 / 查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "分页查询通道交互日志（PaymentLog：与银行/通道网关的机机交互留痕）")
    public ApiResponse<PageResponse<PaymentLogSummaryResponse>> listPaymentLogs(
            PaymentLogQuery query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(paymentAppService.listPaymentLogs(query, pageable));
    }

    @GetMapping("/operation-logs")
    @RequireAuth
    @RequirePermission(
            value = "admin:payment:read",
            name = "支付管理 / 查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "分页查询订单操作记录（OperationLog：行为者对订单的操作留痕）")
    public ApiResponse<PageResponse<OperationLogSummaryResponse>> listOperationLogs(
            OperationLogQuery query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(paymentAppService.listOperationLogs(query, pageable));
    }

    @GetMapping("/payments/{paymentOrderNo}")
    @RequireAuth
    @RequirePermission(
            value = "admin:payment:read",
            name = "支付管理 / 查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "查询支付订单详情")
    public ApiResponse<PaymentOrderDetailResponse> getPaymentDetail(
            @PathVariable String paymentOrderNo) {
        return ApiResponse.ok(paymentAppService.getPaymentDetail(paymentOrderNo));
    }

    @GetMapping("/refunds/{refundOrderNo}")
    @RequireAuth
    @RequirePermission(
            value = "admin:payment:read",
            name = "支付管理 / 查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "查询退款订单详情")
    public ApiResponse<RefundOrderDetailResponse> getRefundDetail(
            @PathVariable String refundOrderNo) {
        return ApiResponse.ok(paymentAppService.getRefundDetail(refundOrderNo));
    }

    @GetMapping("/orders/{orderNo}/lifecycle")
    @RequireAuth
    @RequirePermission(
            value = "admin:payment:read",
            name = "支付管理 / 查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "查询订单生命周期（按时间合并的 PaymentLog + OperationLog 时间线）")
    public ApiResponse<OrderLifecycleResponse> getLifecycle(
            @PathVariable String orderNo) {
        return ApiResponse.ok(paymentAppService.getLifecycle(orderNo));
    }

    @GetMapping("/stats/payments/overview")
    @RequireAuth
    @RequirePermission(
            value = "admin:payment:read",
            name = "支付管理 / 查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "支付总览统计（tier-1）——透传 payment：笔数·金额·成功率·净额 + 趋势")
    public ApiResponse<PaymentOverviewResponse> getPaymentOverview() {
        return ApiResponse.ok(paymentAppService.getPaymentOverview());
    }

    @GetMapping("/stats/orders/status-distribution")
    @RequireAuth
    @RequirePermission(
            value = "admin:payment:read",
            name = "支付管理 / 查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "订单状态分布统计（tier-1）——透传 payment：各状态在途笔数·金额 + 退款待审核积压")
    public ApiResponse<OrderStatusDistributionResponse> getOrderStatusDistribution() {
        return ApiResponse.ok(paymentAppService.getOrderStatusDistribution());
    }

    @GetMapping("/stats/gateway/health")
    @RequireAuth
    @RequirePermission(
            value = "admin:payment:read",
            name = "支付管理 / 查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "通道健康统计（tier-1）——透传 payment：各银行接口调用次数·成功率·平均耗时·返回码分布")
    public ApiResponse<GatewayHealthResponse> getGatewayHealth() {
        return ApiResponse.ok(paymentAppService.getGatewayHealth());
    }

    @GetMapping("/stats/operations/audit")
    @RequireAuth
    @RequirePermission(
            value = "admin:payment:read",
            name = "支付管理 / 查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "审核统计（tier-1）——透传 payment：审核笔数·通过率·平均审核时长 + 按审核人聚合")
    public ApiResponse<OperationsAuditResponse> getOperationsAudit() {
        return ApiResponse.ok(paymentAppService.getOperationsAudit());
    }

    @PostMapping("/refunds/{refundOrderNo}/audit")
    @RequireAuth
    @RequirePermission(
            value = "admin:payment:refund:audit",
            name = "支付管理 / 退款审核",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "审核退款（approve/reject）——操作者身份从 RequestContext 透传，审计归 payment")
    public ApiResponse<RefundOrderDetailResponse> auditRefund(
            @PathVariable String refundOrderNo,
            @Valid @RequestBody RefundAuditCommand command) {
        // 操作者身份从 RequestContext 透传到 payment 请求体（零 Sa-Token/零 DB/零新注解）：
        // auditorId/auditorName 不来自前端、不可伪造；payment 落 OperationLog（auditType=MANUAL）。
        return ApiResponse.ok(paymentAppService.auditRefund(
                refundOrderNo, command, RequestContext.getUserId(), RequestContext.getUserName()));
    }
}
