package com.aieducenter.admin.payment.endpoints.controller;

import com.aieducenter.admin.constants.AdminScopes;
import com.aieducenter.admin.payment.application.PaymentManagementAppService;
import com.aieducenter.admin.payment.application.dto.query.PaymentOrderQuery;
import com.aieducenter.admin.payment.application.dto.query.RefundOrderQuery;
import com.aieducenter.admin.payment.application.dto.response.OrderLifecycleResponse;
import com.aieducenter.admin.payment.application.dto.response.PaymentOrderDetailResponse;
import com.aieducenter.admin.payment.application.dto.response.PaymentOrderSummaryResponse;
import com.aieducenter.admin.payment.application.dto.response.RefundOrderDetailResponse;
import com.aieducenter.admin.payment.application.dto.response.RefundOrderSummaryResponse;
import com.cartisan.security.annotation.RequireAuth;
import com.cartisan.security.annotation.RequirePermission;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
