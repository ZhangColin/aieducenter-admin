package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;

import com.aieducenter.admin.payment.application.PaymentManagementAppService;
import com.aieducenter.admin.payment.application.dto.command.RefundAuditCommand;
import com.aieducenter.admin.payment.application.dto.query.OperationLogQuery;
import com.aieducenter.admin.payment.application.dto.query.PaymentLogQuery;
import com.aieducenter.admin.payment.application.dto.query.PaymentOrderQuery;
import com.aieducenter.admin.payment.application.dto.query.RefundOrderQuery;
import com.aieducenter.admin.payment.application.dto.response.AnomaliesResponse;
import com.aieducenter.admin.payment.application.dto.response.BusinessSystemStatsResponse;
import com.aieducenter.admin.payment.application.dto.response.ChannelStatsResponse;
import com.aieducenter.admin.payment.application.dto.response.GatewayHealthResponse;
import com.aieducenter.admin.payment.application.dto.response.OperationLogSummaryResponse;
import com.aieducenter.admin.payment.application.dto.response.OperationsActivityResponse;
import com.aieducenter.admin.payment.application.dto.response.OperationsAuditResponse;
import com.aieducenter.admin.payment.application.dto.response.OrderLifecycleResponse;
import com.aieducenter.admin.payment.application.dto.response.OrderStatusDistributionResponse;
import com.aieducenter.admin.payment.application.dto.response.PaymentLogSummaryResponse;
import com.aieducenter.admin.payment.application.dto.response.PaymentOrderDetailResponse;
import com.aieducenter.admin.payment.application.dto.response.PaymentOverviewResponse;
import com.aieducenter.admin.payment.application.dto.response.PaymentOrderSummaryResponse;
import com.aieducenter.admin.payment.application.dto.response.RefundOrderDetailResponse;
import com.aieducenter.admin.payment.application.dto.response.RefundOrderSummaryResponse;
import com.aieducenter.admin.payment.application.dto.wire.AnomaliesWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.AuditRefundWireRequest;
import com.aieducenter.admin.payment.application.dto.wire.BusinessSystemStatsWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.ChannelStatsWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.GatewayHealthWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.OperationLogListWireRequest;
import com.aieducenter.admin.payment.application.dto.wire.OperationLogWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.OperationsActivityWireResponse;
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

/**
 * 支付管理 BFF 集成测试——mock {@link PaymentClient}，验证 {@link PaymentManagementAppService}
 * 在 Spring 上下文中的完整接线（DI、wire→response DTO 映射、筛选/分页透传、异常转译）。
 *
 * <p>不模拟安全层（权限在 {@code PaymentRbacEnforcementIntegrationTest} 覆盖）；
 * 不直测 {@link PaymentClient}（与 {@code AppRegistryClient} 一致，client bean 直接 mock）。</p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentBffIntegrationTest {

    @Autowired
    private PaymentManagementAppService paymentAppService;

    @MockBean
    private PaymentClient paymentClient;

    // ========== list · DTO 映射 + 分页契约 ==========

    @Test
    void given_paymentOrders_when_list_then_returnMappedPage() {
        LocalDateTime now = LocalDateTime.now();
        when(paymentClient.listPayments(any(PaymentOrderListWireRequest.class), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(
                        new PaymentOrderWireResponse("PAY-1", "BIZ-1", "course-svc", "PAID",
                                new BigDecimal("99.00"), "WECHAT", "WEB", "WECHAT_NATIVE",
                                now, now.minusMinutes(5)),
                        new PaymentOrderWireResponse("PAY-2", "BIZ-2", "course-svc", "PENDING",
                                new BigDecimal("199.00"), "ALIPAY", "APP", "ALIPAY_APP",
                                null, now.minusMinutes(1))
                ), 28L, 0, 20));

        var page = paymentAppService.list(
                new PaymentOrderQuery(null, null, null, null, null, null, null,
                        null, null, null, null, null, null),
                PageRequest.of(0, 20));

        // 分页契约：total/page/size 沿用 payment 回显
        assertThat(page.total()).isEqualTo(28L);
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(20);
        // DTO 映射：wire → response 逐字段
        assertThat(page.items()).hasSize(2);
        PaymentOrderSummaryResponse first = page.items().get(0);
        assertThat(first.paymentOrderNo()).isEqualTo("PAY-1");
        assertThat(first.status()).isEqualTo("PAID");
        assertThat(first.amount()).isEqualByComparingTo("99.00");
        assertThat(first.payMode()).isEqualTo("WECHAT");
        assertThat(first.paidAt()).isEqualTo(now);
        PaymentOrderSummaryResponse second = page.items().get(1);
        assertThat(second.paymentOrderNo()).isEqualTo("PAY-2");
        assertThat(second.status()).isEqualTo("PENDING");
        assertThat(second.paidAt()).isNull();
    }

    // ========== list · 筛选映射 + 页码换算 ==========

    @Test
    void given_filtersAndPageable_when_list_then_passQueryAndConvertPage() {
        when(paymentClient.listPayments(any(PaymentOrderListWireRequest.class), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0L, 2, 20));

        PaymentOrderQuery query = new PaymentOrderQuery(
                "PAY-1", "BIZ-1", "course-svc",
                List.of("PAID", "PENDING"), "WECHAT", "WEB", "WECHAT_NATIVE",
                new BigDecimal("10.00"), new BigDecimal("500.00"),
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59),
                null, null);

        paymentAppService.list(query, PageRequest.of(2, 20));

        // query → wire 映射：筛选原样透传；Spring Pageable 0-based(page=2) → 客户端 1-based(page=3)
        PaymentOrderListWireRequest expectedWire = new PaymentOrderListWireRequest(
                "PAY-1", "BIZ-1", "course-svc",
                List.of("PAID", "PENDING"), "WECHAT", "WEB", "WECHAT_NATIVE",
                new BigDecimal("10.00"), new BigDecimal("500.00"),
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59),
                null, null);
        verify(paymentClient).listPayments(eq(expectedWire), eq(3), eq(20));
    }

    // ========== list · 错误翻译 ==========

    @Test
    void given_downstream500_when_list_then_throwThirdPartyError() {
        // payment 内部错误（5xx）→ 统一对外 THIRD_PARTY_ERROR（运营侧已认证，下游故障为第三方错误）
        when(paymentClient.listPayments(any(PaymentOrderListWireRequest.class), anyInt(), anyInt()))
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> paymentAppService.list(
                new PaymentOrderQuery(null, null, null, null, null, null, null,
                        null, null, null, null, null, null),
                PageRequest.of(0, 20)))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }

    @Test
    void given_downstream409_when_list_then_throwConflict() {
        when(paymentClient.listPayments(any(PaymentOrderListWireRequest.class), anyInt(), anyInt()))
                .thenThrow(new OpenApiClientException(409, "{\"message\":\"conflict\"}"));

        assertThatThrownBy(() -> paymentAppService.list(
                new PaymentOrderQuery(null, null, null, null, null, null, null,
                        null, null, null, null, null, null),
                PageRequest.of(0, 20)))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.CONFLICT);
    }

    // ========== listRefunds · DTO 映射 + 分页契约 ==========

    @Test
    void given_refundOrders_when_listRefunds_then_returnMappedPage() {
        LocalDateTime now = LocalDateTime.now();
        when(paymentClient.listRefunds(any(RefundOrderListWireRequest.class), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(
                        new RefundOrderWireResponse("RF-1", "PAY-1", "BIZ-1", "course-svc", "SUCCESS",
                                new BigDecimal("99.00"), "MANUAL", 1001L, "alice",
                                now.minusMinutes(3), now.minusMinutes(10)),
                        new RefundOrderWireResponse("RF-2", "PAY-2", "BIZ-2", "course-svc", "PENDING",
                                new BigDecimal("199.00"), "AUTO", null, null,
                                null, now.minusMinutes(1))
                ), 9L, 0, 20));

        var page = paymentAppService.listRefunds(
                new RefundOrderQuery(null, null, null, null, null, null, null,
                        null, null, null, null),
                PageRequest.of(0, 20));

        // 分页契约：total/page/size 沿用 payment 回显
        assertThat(page.total()).isEqualTo(9L);
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(20);
        // DTO 映射：wire → response 逐字段
        assertThat(page.items()).hasSize(2);
        RefundOrderSummaryResponse first = page.items().get(0);
        assertThat(first.refundOrderNo()).isEqualTo("RF-1");
        assertThat(first.paymentOrderNo()).isEqualTo("PAY-1");
        assertThat(first.businessOrderNo()).isEqualTo("BIZ-1");
        assertThat(first.status()).isEqualTo("SUCCESS");
        assertThat(first.refundAmount()).isEqualByComparingTo("99.00");
        assertThat(first.auditType()).isEqualTo("MANUAL");
        assertThat(first.auditorId()).isEqualTo(1001L);
        assertThat(first.auditorName()).isEqualTo("alice");
        assertThat(first.auditedAt()).isEqualTo(now.minusMinutes(3));
        RefundOrderSummaryResponse second = page.items().get(1);
        assertThat(second.refundOrderNo()).isEqualTo("RF-2");
        assertThat(second.status()).isEqualTo("PENDING");
        assertThat(second.auditType()).isEqualTo("AUTO");
        assertThat(second.auditorId()).isNull();
        assertThat(second.auditorName()).isNull();
        assertThat(second.auditedAt()).isNull();
    }

    // ========== listRefunds · 筛选映射 + 页码换算 ==========

    @Test
    void given_refundFiltersAndPageable_when_listRefunds_then_passQueryAndConvertPage() {
        when(paymentClient.listRefunds(any(RefundOrderListWireRequest.class), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0L, 2, 20));

        RefundOrderQuery query = new RefundOrderQuery(
                "RF-1", "PAY-1", "BIZ-1", "course-svc",
                List.of("PENDING", "APPROVED"), "MANUAL", 1001L,
                new BigDecimal("10.00"), new BigDecimal("500.00"),
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59));

        paymentAppService.listRefunds(query, PageRequest.of(2, 20));

        // query → wire 映射：筛选原样透传；Spring Pageable 0-based(page=2) → 客户端 1-based(page=3)
        RefundOrderListWireRequest expectedWire = new RefundOrderListWireRequest(
                "RF-1", "PAY-1", "BIZ-1", "course-svc",
                List.of("PENDING", "APPROVED"), "MANUAL", 1001L,
                new BigDecimal("10.00"), new BigDecimal("500.00"),
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59));
        verify(paymentClient).listRefunds(eq(expectedWire), eq(3), eq(20));
    }

    // ========== listRefunds · 错误翻译 ==========

    @Test
    void given_downstream500_when_listRefunds_then_throwThirdPartyError() {
        when(paymentClient.listRefunds(any(RefundOrderListWireRequest.class), anyInt(), anyInt()))
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> paymentAppService.listRefunds(
                new RefundOrderQuery(null, null, null, null, null, null, null,
                        null, null, null, null),
                PageRequest.of(0, 20)))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }

    @Test
    void given_downstream404_when_listRefunds_then_throwNotFound() {
        when(paymentClient.listRefunds(any(RefundOrderListWireRequest.class), anyInt(), anyInt()))
                .thenThrow(new OpenApiClientException(404, "{\"message\":\"not found\"}"));

        assertThatThrownBy(() -> paymentAppService.listRefunds(
                new RefundOrderQuery(null, null, null, null, null, null, null,
                        null, null, null, null),
                PageRequest.of(0, 20)))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.NOT_FOUND);
    }

    // ========== listPaymentLogs · 通道交互日志（PaymentLog）· DTO 映射 + 分页契约 ==========

    @Test
    void given_paymentLogs_when_listPaymentLogs_then_returnMappedPage() {
        LocalDateTime now = LocalDateTime.now();
        when(paymentClient.listPaymentLogs(any(PaymentLogListWireRequest.class), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(
                        new PaymentLogWireResponse(7001L, "PAY-1", null, "PAYMENT_REQUEST", "ICBC", "ICBC_PAY",
                                200, "000000", "success", 120L, true, null, now.minusMinutes(5)),
                        new PaymentLogWireResponse(7002L, null, "RF-1", "REFUND_QUERY", "ICBC", "ICBC_REFUNDQ",
                                200, "9999", "bank error", 90L, false, "timeout", now.minusMinutes(1))
                ), 17L, 0, 20));

        var page = paymentAppService.listPaymentLogs(
                new PaymentLogQuery(null, null, null, null, null, null, null, null),
                PageRequest.of(0, 20));

        // 分页契约：total/page/size 沿用 payment 回显
        assertThat(page.total()).isEqualTo(17L);
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(20);
        // DTO 映射：wire → response 逐字段（含网关诊断字段组）
        assertThat(page.items()).hasSize(2);
        PaymentLogSummaryResponse first = page.items().get(0);
        assertThat(first.id()).isEqualTo(7001L);
        assertThat(first.paymentOrderNo()).isEqualTo("PAY-1");
        assertThat(first.refundOrderNo()).isNull();
        assertThat(first.logType()).isEqualTo("PAYMENT_REQUEST");
        assertThat(first.bankCode()).isEqualTo("ICBC");
        assertThat(first.bankInterface()).isEqualTo("ICBC_PAY");
        assertThat(first.httpStatus()).isEqualTo(200);
        assertThat(first.returnCode()).isEqualTo("000000");
        assertThat(first.returnMsg()).isEqualTo("success");
        assertThat(first.executionTime()).isEqualTo(120L);
        assertThat(first.success()).isTrue();
        assertThat(first.errorMessage()).isNull();
        assertThat(first.createdAt()).isEqualTo(now.minusMinutes(5));
        PaymentLogSummaryResponse second = page.items().get(1);
        assertThat(second.id()).isEqualTo(7002L);
        assertThat(second.refundOrderNo()).isEqualTo("RF-1");
        assertThat(second.logType()).isEqualTo("REFUND_QUERY");
        assertThat(second.returnCode()).isEqualTo("9999");
        assertThat(second.success()).isFalse();
        assertThat(second.errorMessage()).isEqualTo("timeout");
    }

    // ========== listPaymentLogs · 筛选映射（含 logType 多选）+ 页码换算 ==========

    @Test
    void given_paymentLogFiltersAndPageable_when_listPaymentLogs_then_passQueryAndConvertPage() {
        when(paymentClient.listPaymentLogs(any(PaymentLogListWireRequest.class), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0L, 2, 20));

        // logTypes 多选 + bankInterface/success/returnCode/createdAt 区间
        PaymentLogQuery query = new PaymentLogQuery(
                "PAY-1", "RF-1", List.of("PAYMENT_REQUEST", "PAYMENT_QUERY"),
                "ICBC_PAY", true, "000000",
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59));

        paymentAppService.listPaymentLogs(query, PageRequest.of(2, 20));

        // query → wire 映射：筛选原样透传（含多选 logTypes）；Spring Pageable 0-based(page=2) → 客户端 1-based(page=3)
        PaymentLogListWireRequest expectedWire = new PaymentLogListWireRequest(
                "PAY-1", "RF-1", List.of("PAYMENT_REQUEST", "PAYMENT_QUERY"),
                "ICBC_PAY", true, "000000",
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59));
        verify(paymentClient).listPaymentLogs(eq(expectedWire), eq(3), eq(20));
    }

    // ========== listPaymentLogs · 错误翻译 ==========

    @Test
    void given_downstream500_when_listPaymentLogs_then_throwThirdPartyError() {
        when(paymentClient.listPaymentLogs(any(PaymentLogListWireRequest.class), anyInt(), anyInt()))
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> paymentAppService.listPaymentLogs(
                new PaymentLogQuery(null, null, null, null, null, null, null, null),
                PageRequest.of(0, 20)))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }

    @Test
    void given_downstream400_when_listPaymentLogs_then_throwBadRequest() {
        // payment 400（筛选参数非法，如时间区间倒置）→ admin 400（BAD_REQUEST）
        when(paymentClient.listPaymentLogs(any(PaymentLogListWireRequest.class), anyInt(), anyInt()))
                .thenThrow(new OpenApiClientException(400, "{\"message\":\"bad range\"}"));

        assertThatThrownBy(() -> paymentAppService.listPaymentLogs(
                new PaymentLogQuery(null, null, null, null, null, null, null, null),
                PageRequest.of(0, 20)))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.BAD_REQUEST);
    }

    // ========== listOperationLogs · 订单操作记录（OperationLog）· DTO 映射 + 分页契约 ==========

    @Test
    void given_operationLogs_when_listOperationLogs_then_returnMappedPage() {
        LocalDateTime now = LocalDateTime.now();
        when(paymentClient.listOperationLogs(any(OperationLogListWireRequest.class), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(
                        new OperationLogWireResponse(8001L, "PAYMENT", "PAY-1", "NOTIFY_RESEND",
                                1001L, "alice", "admin-console", "SUCCESS", "manual resend", now.minusMinutes(3)),
                        new OperationLogWireResponse(8002L, "REFUND", "RF-1", "AUDIT_REJECT",
                                null, null, "course-svc", "FAILED", null, now.minusMinutes(1))
                ), 4L, 0, 20));

        var page = paymentAppService.listOperationLogs(
                new OperationLogQuery(null, null, null, null, null, null, null, null),
                PageRequest.of(0, 20));

        // 分页契约：total/page/size 沿用 payment 回显
        assertThat(page.total()).isEqualTo(4L);
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(20);
        // DTO 映射：wire → response 逐字段（含操作者/结果字段组）
        assertThat(page.items()).hasSize(2);
        OperationLogSummaryResponse first = page.items().get(0);
        assertThat(first.id()).isEqualTo(8001L);
        assertThat(first.targetType()).isEqualTo("PAYMENT");
        assertThat(first.targetNo()).isEqualTo("PAY-1");
        assertThat(first.operation()).isEqualTo("NOTIFY_RESEND");
        assertThat(first.operatorId()).isEqualTo(1001L);
        assertThat(first.operatorName()).isEqualTo("alice");
        assertThat(first.operatorSystem()).isEqualTo("admin-console");
        assertThat(first.result()).isEqualTo("SUCCESS");
        assertThat(first.remark()).isEqualTo("manual resend");
        assertThat(first.createdAt()).isEqualTo(now.minusMinutes(3));
        OperationLogSummaryResponse second = page.items().get(1);
        assertThat(second.targetType()).isEqualTo("REFUND");
        assertThat(second.operation()).isEqualTo("AUDIT_REJECT");
        // 系统发起的动作：操作者字段为 null（union 另一半）
        assertThat(second.operatorId()).isNull();
        assertThat(second.operatorName()).isNull();
        assertThat(second.operatorSystem()).isEqualTo("course-svc");
        assertThat(second.result()).isEqualTo("FAILED");
        assertThat(second.remark()).isNull();
    }

    // ========== listOperationLogs · 筛选映射 + 页码换算 ==========

    @Test
    void given_operationLogFiltersAndPageable_when_listOperationLogs_then_passQueryAndConvertPage() {
        when(paymentClient.listOperationLogs(any(OperationLogListWireRequest.class), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0L, 2, 20));

        OperationLogQuery query = new OperationLogQuery(
                "PAYMENT", "PAY-1", "NOTIFY_RESEND", 1001L, "admin-console", "SUCCESS",
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59));

        paymentAppService.listOperationLogs(query, PageRequest.of(2, 20));

        // query → wire 映射：筛选原样透传；Spring Pageable 0-based(page=2) → 客户端 1-based(page=3)
        OperationLogListWireRequest expectedWire = new OperationLogListWireRequest(
                "PAYMENT", "PAY-1", "NOTIFY_RESEND", 1001L, "admin-console", "SUCCESS",
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59));
        verify(paymentClient).listOperationLogs(eq(expectedWire), eq(3), eq(20));
    }

    // ========== listOperationLogs · 错误翻译 ==========

    @Test
    void given_downstream500_when_listOperationLogs_then_throwThirdPartyError() {
        when(paymentClient.listOperationLogs(any(OperationLogListWireRequest.class), anyInt(), anyInt()))
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> paymentAppService.listOperationLogs(
                new OperationLogQuery(null, null, null, null, null, null, null, null),
                PageRequest.of(0, 20)))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }

    @Test
    void given_downstream400_when_listOperationLogs_then_throwBadRequest() {
        // payment 400（筛选参数非法，如时间区间倒置）→ admin 400（BAD_REQUEST）
        when(paymentClient.listOperationLogs(any(OperationLogListWireRequest.class), anyInt(), anyInt()))
                .thenThrow(new OpenApiClientException(400, "{\"message\":\"bad range\"}"));

        assertThatThrownBy(() -> paymentAppService.listOperationLogs(
                new OperationLogQuery(null, null, null, null, null, null, null, null),
                PageRequest.of(0, 20)))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.BAD_REQUEST);
    }

    // ========== getPaymentDetail · DTO 映射 ==========

    @Test
    void given_paymentOrder_when_getPaymentDetail_then_returnMappedDetail() {
        LocalDateTime now = LocalDateTime.now();
        when(paymentClient.getPayment("PAY-1")).thenReturn(
                new PaymentOrderDetailWireResponse("PAY-1", "BIZ-1", "course-svc", "PAID",
                        new BigDecimal("99.00"), "WECHAT", "WEB", "WECHAT_NATIVE",
                        now, now.minusMinutes(5)));

        PaymentOrderDetailResponse detail = paymentAppService.getPaymentDetail("PAY-1");

        // 详情聚合逐字段映射
        assertThat(detail.paymentOrderNo()).isEqualTo("PAY-1");
        assertThat(detail.businessOrderNo()).isEqualTo("BIZ-1");
        assertThat(detail.businessSystemName()).isEqualTo("course-svc");
        assertThat(detail.status()).isEqualTo("PAID");
        assertThat(detail.amount()).isEqualByComparingTo("99.00");
        assertThat(detail.payMode()).isEqualTo("WECHAT");
        assertThat(detail.accessType()).isEqualTo("WEB");
        assertThat(detail.paymentChannel()).isEqualTo("WECHAT_NATIVE");
        assertThat(detail.paidAt()).isEqualTo(now);
        assertThat(detail.createdAt()).isEqualTo(now.minusMinutes(5));
    }

    @Test
    void given_payment404_when_getPaymentDetail_then_throwNotFound() {
        // payment 404（订单不存在）→ admin 404
        when(paymentClient.getPayment("NOPE"))
                .thenThrow(new OpenApiClientException(404, "{\"message\":\"not found\"}"));

        assertThatThrownBy(() -> paymentAppService.getPaymentDetail("NOPE"))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage().httpStatus() == 404);
    }

    // ========== getRefundDetail · DTO 映射 ==========

    @Test
    void given_refundOrder_when_getRefundDetail_then_returnMappedDetail() {
        LocalDateTime now = LocalDateTime.now();
        when(paymentClient.getRefund("RF-1")).thenReturn(
                new RefundOrderDetailWireResponse("RF-1", "PAY-1", "BIZ-1", "course-svc", "SUCCESS",
                        new BigDecimal("99.00"), "MANUAL", 1001L, "alice",
                        now.minusMinutes(3), now.minusMinutes(10)));

        RefundOrderDetailResponse detail = paymentAppService.getRefundDetail("RF-1");

        // 详情聚合逐字段映射
        assertThat(detail.refundOrderNo()).isEqualTo("RF-1");
        assertThat(detail.paymentOrderNo()).isEqualTo("PAY-1");
        assertThat(detail.businessOrderNo()).isEqualTo("BIZ-1");
        assertThat(detail.businessSystemName()).isEqualTo("course-svc");
        assertThat(detail.status()).isEqualTo("SUCCESS");
        assertThat(detail.refundAmount()).isEqualByComparingTo("99.00");
        assertThat(detail.auditType()).isEqualTo("MANUAL");
        assertThat(detail.auditorId()).isEqualTo(1001L);
        assertThat(detail.auditorName()).isEqualTo("alice");
        assertThat(detail.auditedAt()).isEqualTo(now.minusMinutes(3));
        assertThat(detail.createdAt()).isEqualTo(now.minusMinutes(10));
    }

    @Test
    void given_refund404_when_getRefundDetail_then_throwNotFound() {
        when(paymentClient.getRefund("NOPE"))
                .thenThrow(new OpenApiClientException(404, "{\"message\":\"not found\"}"));

        assertThatThrownBy(() -> paymentAppService.getRefundDetail("NOPE"))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage().httpStatus() == 404);
    }

    // ========== getLifecycle · 已合并时间线透传（payment 合并，admin 不改序） ==========

    @Test
    void given_mergedTimeline_when_getLifecycle_then_preserveOrderAndMapBothSources() {
        LocalDateTime t1 = LocalDateTime.of(2026, 8, 12, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 8, 12, 10, 5);
        // 故意让 payment 回传的顺序与时间序相反（t2 在前、t1 在后）——证明 admin 是透传、不重排：
        // 若 admin 偷偷按 createdAt 排序，事件顺序会变成 t1/t2（与输入相反），断言即失败。
        // 合并与排序归 payment（ADR-0002），admin 只 map 不 sort。
        when(paymentClient.getLifecycle("PAY-1")).thenReturn(new OrderLifecycleWireResponse("PAY-1", List.of(
                new OrderLifecycleWireResponse.LifecycleEventWireResponse(
                        "OPERATION_LOG", t2,
                        null, null, null, null, null, null, null, null,
                        "PAYMENT", "PAY-1", "NOTIFY_RESEND", 1001L, "alice", "admin-console", "SUCCESS", "manual resend"),
                new OrderLifecycleWireResponse.LifecycleEventWireResponse(
                        "PAYMENT_LOG", t1,
                        "PAYMENT_REQUEST", "PAY-1", null, "ICBC_PAY", "000000", "success", 120L, true,
                        null, null, null, null, null, null, null, null)
        )));

        OrderLifecycleResponse lifecycle = paymentAppService.getLifecycle("PAY-1");

        assertThat(lifecycle.orderNo()).isEqualTo("PAY-1");
        assertThat(lifecycle.events()).hasSize(2);
        // 顺序原样保留（payment 给的 t2→t1，admin 不重排为 t1→t2）
        OrderLifecycleResponse.LifecycleEvent first = lifecycle.events().get(0);
        assertThat(first.source()).isEqualTo("OPERATION_LOG");
        assertThat(first.createdAt()).isEqualTo(t2);
        // 操作字段组映射
        assertThat(first.operation()).isEqualTo("NOTIFY_RESEND");
        assertThat(first.operatorId()).isEqualTo(1001L);
        assertThat(first.operatorName()).isEqualTo("alice");
        assertThat(first.operatorSystem()).isEqualTo("admin-console");
        assertThat(first.result()).isEqualTo("SUCCESS");
        assertThat(first.remark()).isEqualTo("manual resend");
        // 网关字段组为 null（union 另一半）
        assertThat(first.logType()).isNull();
        assertThat(first.bankInterface()).isNull();

        OrderLifecycleResponse.LifecycleEvent second = lifecycle.events().get(1);
        assertThat(second.source()).isEqualTo("PAYMENT_LOG");
        assertThat(second.createdAt()).isEqualTo(t1);
        // 网关字段组映射
        assertThat(second.logType()).isEqualTo("PAYMENT_REQUEST");
        assertThat(second.bankInterface()).isEqualTo("ICBC_PAY");
        assertThat(second.returnCode()).isEqualTo("000000");
        assertThat(second.executionTime()).isEqualTo(120L);
        assertThat(second.success()).isTrue();
        // 操作字段组为 null
        assertThat(second.operation()).isNull();
        assertThat(second.operatorId()).isNull();
    }

    @Test
    void given_lifecycle404_when_getLifecycle_then_throwNotFound() {
        when(paymentClient.getLifecycle("NOPE"))
                .thenThrow(new OpenApiClientException(404, "{\"message\":\"not found\"}"));

        assertThatThrownBy(() -> paymentAppService.getLifecycle("NOPE"))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage().httpStatus() == 404);
    }

    // ========== auditRefund · 操作者身份 + 决策透传（首个写端点） ==========

    @Test
    void given_auditApprove_when_auditRefund_then_wireRequestCarriesAuditorAndDecision_returnsMappedDetail() {
        LocalDateTime now = LocalDateTime.now();
        when(paymentClient.auditRefund(eq("RF-1"), any(AuditRefundWireRequest.class))).thenReturn(
                new RefundOrderDetailWireResponse("RF-1", "PAY-1", "BIZ-1", "course-svc", "APPROVED",
                        new BigDecimal("99.00"), "MANUAL", 1001L, "alice",
                        now, now.minusMinutes(10)));

        RefundOrderDetailResponse detail = paymentAppService.auditRefund(
                "RF-1", new RefundAuditCommand(true, "同意退款"), 1001L, "alice");

        // 出站 wire 请求体：决策（agreed=true）+ 操作者身份（auditorId/auditorName）+ 备注 均正确透传
        ArgumentCaptor<AuditRefundWireRequest> captor = ArgumentCaptor.forClass(AuditRefundWireRequest.class);
        verify(paymentClient).auditRefund(eq("RF-1"), captor.capture());
        AuditRefundWireRequest wire = captor.getValue();
        assertThat(wire.auditorId()).isEqualTo(1001L);
        assertThat(wire.auditorName()).isEqualTo("alice");
        assertThat(wire.agreed()).isTrue();
        assertThat(wire.remark()).isEqualTo("同意退款");

        // 响应映射：审核后退款单聚合（与详情同形）
        assertThat(detail.refundOrderNo()).isEqualTo("RF-1");
        assertThat(detail.status()).isEqualTo("APPROVED");
        assertThat(detail.auditType()).isEqualTo("MANUAL");
        assertThat(detail.auditorId()).isEqualTo(1001L);
        assertThat(detail.auditorName()).isEqualTo("alice");
        assertThat(detail.auditedAt()).isEqualTo(now);
    }

    @Test
    void given_auditReject_when_auditRefund_then_wireRequestCarriesAgreedFalse() {
        when(paymentClient.auditRefund(eq("RF-2"), any(AuditRefundWireRequest.class))).thenReturn(
                new RefundOrderDetailWireResponse("RF-2", null, null, null, "REJECTED",
                        null, "MANUAL", 2002L, "bob", null, null));

        paymentAppService.auditRefund("RF-2", new RefundAuditCommand(false, "金额不符"), 2002L, "bob");

        // 拒绝：agreed=false 透传（payment 据此落 AUDIT_REJECT 操作日志）
        ArgumentCaptor<AuditRefundWireRequest> captor = ArgumentCaptor.forClass(AuditRefundWireRequest.class);
        verify(paymentClient).auditRefund(eq("RF-2"), captor.capture());
        assertThat(captor.getValue().agreed()).isFalse();
        assertThat(captor.getValue().auditorId()).isEqualTo(2002L);
        assertThat(captor.getValue().auditorName()).isEqualTo("bob");
        assertThat(captor.getValue().remark()).isEqualTo("金额不符");
    }

    @Test
    void given_auditRemarkNull_when_auditRefund_then_wireRequestCarriesNullRemark() {
        when(paymentClient.auditRefund(eq("RF-3"), any(AuditRefundWireRequest.class))).thenReturn(
                new RefundOrderDetailWireResponse("RF-3", null, null, null, "APPROVED",
                        null, "MANUAL", 3003L, "carol", null, null));

        // remark 选填——前端不传时透传 null（payment 侧 @Size 仅约束非空长度）
        paymentAppService.auditRefund("RF-3", new RefundAuditCommand(true, null), 3003L, "carol");

        ArgumentCaptor<AuditRefundWireRequest> captor = ArgumentCaptor.forClass(AuditRefundWireRequest.class);
        verify(paymentClient).auditRefund(eq("RF-3"), captor.capture());
        assertThat(captor.getValue().remark()).isNull();
        assertThat(captor.getValue().agreed()).isTrue();
    }

    // ========== auditRefund · 错误翻译 ==========

    @Test
    void given_downstream404_when_auditRefund_then_throwNotFound() {
        // payment 404（退款单不存在）→ admin 404
        when(paymentClient.auditRefund(eq("NOPE"), any(AuditRefundWireRequest.class)))
                .thenThrow(new OpenApiClientException(404, "{\"message\":\"not found\"}"));

        assertThatThrownBy(() -> paymentAppService.auditRefund(
                "NOPE", new RefundAuditCommand(true, null), 1001L, "alice"))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.NOT_FOUND);
    }

    @Test
    void given_downstream400NotPending_when_auditRefund_then_throwBadRequest() {
        // payment 400（退款单非待审核状态、无法审核）→ admin 400（BAD_REQUEST）
        when(paymentClient.auditRefund(eq("RF-DONE"), any(AuditRefundWireRequest.class)))
                .thenThrow(new OpenApiClientException(400, "{\"message\":\"退款订单不是待审核状态\"}"));

        assertThatThrownBy(() -> paymentAppService.auditRefund(
                "RF-DONE", new RefundAuditCommand(true, null), 1001L, "alice"))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.BAD_REQUEST);
    }

    // ========== queryPayment · 主动查行透传（无请求体、无身份透传，返回与详情同形） ==========

    @Test
    void given_bankQueriedPayment_when_queryPayment_then_returnMappedDetail_andPassPaymentOrderNo() {
        LocalDateTime now = LocalDateTime.now();
        // payment POST /payments/{no}/query 仅取路径参数、返回查询后聚合（与 GET 详情同形 PaymentOrderResponse）
        when(paymentClient.queryPayment("PAY-1")).thenReturn(
                new PaymentOrderDetailWireResponse("PAY-1", "BIZ-1", "course-svc", "PAID",
                        new BigDecimal("99.00"), "WECHAT", "WEB", "WECHAT_NATIVE",
                        now, now.minusMinutes(5)));

        PaymentOrderDetailResponse detail = paymentAppService.queryPayment("PAY-1");

        // 复用 toPaymentDetail 映射：查行结果与详情同形、逐字段映射
        assertThat(detail.paymentOrderNo()).isEqualTo("PAY-1");
        assertThat(detail.businessOrderNo()).isEqualTo("BIZ-1");
        assertThat(detail.businessSystemName()).isEqualTo("course-svc");
        assertThat(detail.status()).isEqualTo("PAID");
        assertThat(detail.amount()).isEqualByComparingTo("99.00");
        assertThat(detail.payMode()).isEqualTo("WECHAT");
        assertThat(detail.paymentChannel()).isEqualTo("WECHAT_NATIVE");
        assertThat(detail.paidAt()).isEqualTo(now);
        // paymentOrderNo 原样透传给下游（无请求体，仅路径参数）
        verify(paymentClient).queryPayment("PAY-1");
    }

    // ========== queryPayment · 错误翻译 ==========

    @Test
    void given_downstream404_when_queryPayment_then_throwNotFound() {
        // payment 404（订单不存在）→ admin 404
        when(paymentClient.queryPayment("NOPE"))
                .thenThrow(new OpenApiClientException(404, "{\"message\":\"not found\"}"));

        assertThatThrownBy(() -> paymentAppService.queryPayment("NOPE"))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.NOT_FOUND);
    }

    @Test
    void given_downstream500_when_queryPayment_then_throwThirdPartyError() {
        // payment 5xx（银行/通道不可达）→ 统一对外 THIRD_PARTY_ERROR（运营侧已认证，下游故障为第三方错误）
        when(paymentClient.queryPayment("PAY-1"))
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"bank unreachable\"}"));

        assertThatThrownBy(() -> paymentAppService.queryPayment("PAY-1"))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }

    // ========== resendPaymentNotification · 操作者身份透传 + 不改订单状态（复用 T4 范式） ==========

    @Test
    void given_paymentNotificationResent_when_resendPaymentNotification_then_wireRequestCarriesOperatorAndReturnsMappedDetail() {
        LocalDateTime now = LocalDateTime.now();
        // payment 返回当前支付单聚合（状态未变——PAID），证明通知重发不改订单状态（payment ADR-0001）：
        // admin 只透传 payment 的回显，admin 侧无从、也无需施加状态。
        when(paymentClient.resendPaymentNotification(eq("PAY-1"), any(ResendNotificationWireRequest.class)))
                .thenReturn(new PaymentOrderDetailWireResponse("PAY-1", "BIZ-1", "course-svc", "PAID",
                        new BigDecimal("99.00"), "WECHAT", "WEB", "WECHAT_NATIVE",
                        now, now.minusMinutes(5)));

        PaymentOrderDetailResponse detail = paymentAppService.resendPaymentNotification("PAY-1", 1001L, "alice");

        // 出站 wire 请求体：仅承载操作者身份（operatorId/operatorName），无前端决策字段（重发无 agreed）
        ArgumentCaptor<ResendNotificationWireRequest> captor =
                ArgumentCaptor.forClass(ResendNotificationWireRequest.class);
        verify(paymentClient).resendPaymentNotification(eq("PAY-1"), captor.capture());
        ResendNotificationWireRequest wire = captor.getValue();
        assertThat(wire.operatorId()).isEqualTo(1001L);
        assertThat(wire.operatorName()).isEqualTo("alice");

        // 响应映射：当前支付单聚合（状态未变 PAID），复用 toPaymentDetail
        assertThat(detail.paymentOrderNo()).isEqualTo("PAY-1");
        assertThat(detail.status()).isEqualTo("PAID");
        assertThat(detail.amount()).isEqualByComparingTo("99.00");
        assertThat(detail.payMode()).isEqualTo("WECHAT");
        assertThat(detail.paidAt()).isEqualTo(now);
    }

    // ========== resendRefundNotification · 操作者身份透传 + 不改订单状态 ==========

    @Test
    void given_refundNotificationResent_when_resendRefundNotification_then_wireRequestCarriesOperatorAndReturnsMappedDetail() {
        LocalDateTime now = LocalDateTime.now();
        // payment 返回当前退款单聚合（状态未变——SUCCESS），证明通知重发不改订单状态
        when(paymentClient.resendRefundNotification(eq("RF-1"), any(ResendNotificationWireRequest.class)))
                .thenReturn(new RefundOrderDetailWireResponse("RF-1", "PAY-1", "BIZ-1", "course-svc", "SUCCESS",
                        new BigDecimal("99.00"), "MANUAL", 1001L, "alice",
                        now.minusMinutes(3), now.minusMinutes(10)));

        RefundOrderDetailResponse detail = paymentAppService.resendRefundNotification("RF-1", 2002L, "bob");

        ArgumentCaptor<ResendNotificationWireRequest> captor =
                ArgumentCaptor.forClass(ResendNotificationWireRequest.class);
        verify(paymentClient).resendRefundNotification(eq("RF-1"), captor.capture());
        ResendNotificationWireRequest wire = captor.getValue();
        assertThat(wire.operatorId()).isEqualTo(2002L);
        assertThat(wire.operatorName()).isEqualTo("bob");

        // 响应映射：当前退款单聚合（状态未变 SUCCESS），复用 toRefundDetail
        assertThat(detail.refundOrderNo()).isEqualTo("RF-1");
        assertThat(detail.status()).isEqualTo("SUCCESS");
        assertThat(detail.refundAmount()).isEqualByComparingTo("99.00");
        assertThat(detail.auditType()).isEqualTo("MANUAL");
    }

    // ========== 通知重发 · 错误翻译 ==========

    @Test
    void given_downstream404_when_resendPaymentNotification_then_throwNotFound() {
        // payment 404（订单不存在）→ admin 404
        when(paymentClient.resendPaymentNotification(eq("NOPE"), any(ResendNotificationWireRequest.class)))
                .thenThrow(new OpenApiClientException(404, "{\"message\":\"not found\"}"));

        assertThatThrownBy(() -> paymentAppService.resendPaymentNotification("NOPE", 1001L, "alice"))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.NOT_FOUND);
    }

    @Test
    void given_downstream500_when_resendRefundNotification_then_throwThirdPartyError() {
        // payment 5xx（业务系统不可达）→ 统一对外 THIRD_PARTY_ERROR（运营侧已认证，下游故障为第三方错误）
        when(paymentClient.resendRefundNotification(eq("RF-1"), any(ResendNotificationWireRequest.class)))
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"biz system unreachable\"}"));

        assertThatThrownBy(() -> paymentAppService.resendRefundNotification("RF-1", 1001L, "alice"))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }

    // ========== getPaymentOverview · tier-1 统计透传契约（顶层 + 趋势分桶映射、不改序） ==========

    @Test
    void given_overviewWithTrend_when_getPaymentOverview_then_mapTopLevelAndBucketsPreserveOrder() {
        LocalDateTime b1 = LocalDateTime.of(2026, 8, 11, 0, 0);
        LocalDateTime b2 = LocalDateTime.of(2026, 8, 12, 0, 0);
        // 故意让 payment 回传顺序与时间序相反（b2 在前、b1 在后）——证明 admin 透传不重排：
        // 聚合/排序归 payment（spec「仪表盘」），admin 只 map 不 sort。
        when(paymentClient.getPaymentOverview()).thenReturn(new PaymentOverviewWireResponse(
                1200L, new BigDecimal("98000.00"),
                30L, new BigDecimal("2400.00"),
                new BigDecimal("0.9850"), new BigDecimal("95600.00"),
                List.of(
                        new PaymentOverviewWireResponse.TrendBucketWireResponse(
                                b2, 20L, new BigDecimal("1600.00"), 1L, new BigDecimal("80.00")),
                        new PaymentOverviewWireResponse.TrendBucketWireResponse(
                                b1, 18L, new BigDecimal("1440.00"), 0L, new BigDecimal("0.00")))));

        PaymentOverviewResponse overview = paymentAppService.getPaymentOverview();

        // 顶层快照逐字段映射
        assertThat(overview.paymentCount()).isEqualTo(1200L);
        assertThat(overview.paymentAmount()).isEqualByComparingTo("98000.00");
        assertThat(overview.refundCount()).isEqualTo(30L);
        assertThat(overview.refundAmount()).isEqualByComparingTo("2400.00");
        assertThat(overview.successRate()).isEqualByComparingTo("0.9850");
        assertThat(overview.netAmount()).isEqualByComparingTo("95600.00");
        // 趋势分桶顺序原样保留（payment 给 b2→b1，admin 不重排为 b1→b2）
        assertThat(overview.trend()).hasSize(2);
        assertThat(overview.trend().get(0).bucket()).isEqualTo(b2);
        assertThat(overview.trend().get(0).paymentCount()).isEqualTo(20L);
        assertThat(overview.trend().get(0).refundAmount()).isEqualByComparingTo("80.00");
        assertThat(overview.trend().get(1).bucket()).isEqualTo(b1);
        assertThat(overview.trend().get(1).refundCount()).isZero();
    }

    @Test
    void given_overviewDownstream500_when_getPaymentOverview_then_throwThirdPartyError() {
        // payment 5xx（内部错误）→ 统一对外 THIRD_PARTY_ERROR（运营侧已认证，下游故障为第三方错误）
        when(paymentClient.getPaymentOverview())
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> paymentAppService.getPaymentOverview())
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }

    // ========== getOrderStatusDistribution · tier-1 统计透传契约 ==========

    @Test
    void given_statusDistribution_when_getOrderStatusDistribution_then_mapBucketsAndBacklog() {
        when(paymentClient.getOrderStatusDistribution()).thenReturn(new OrderStatusDistributionWireResponse(
                List.of(new OrderStatusDistributionWireResponse.StatusBucketWireResponse(
                                "PAID", 800L, new BigDecimal("64000.00")),
                        new OrderStatusDistributionWireResponse.StatusBucketWireResponse(
                                "PENDING", 50L, new BigDecimal("4000.00"))),
                List.of(new OrderStatusDistributionWireResponse.StatusBucketWireResponse(
                                "SUCCESS", 25L, new BigDecimal("2000.00")),
                        new OrderStatusDistributionWireResponse.StatusBucketWireResponse(
                                "PENDING", 5L, new BigDecimal("400.00"))),
                5L));

        OrderStatusDistributionResponse dist = paymentAppService.getOrderStatusDistribution();

        // 支付状态分布映射 + 顺序保留
        assertThat(dist.paymentStatuses()).hasSize(2);
        assertThat(dist.paymentStatuses().get(0).status()).isEqualTo("PAID");
        assertThat(dist.paymentStatuses().get(0).count()).isEqualTo(800L);
        assertThat(dist.paymentStatuses().get(0).amount()).isEqualByComparingTo("64000.00");
        assertThat(dist.paymentStatuses().get(1).status()).isEqualTo("PENDING");
        // 退款状态分布映射
        assertThat(dist.refundStatuses()).hasSize(2);
        assertThat(dist.refundStatuses().get(0).status()).isEqualTo("SUCCESS");
        assertThat(dist.refundStatuses().get(1).status()).isEqualTo("PENDING");
        assertThat(dist.refundStatuses().get(1).amount()).isEqualByComparingTo("400.00");
        // 退款待审核积压（运营关注的积压 KPI，单独 roll-up）
        assertThat(dist.refundPendingAuditCount()).isEqualTo(5L);
    }

    @Test
    void given_statusDistributionDownstream400_when_getOrderStatusDistribution_then_throwBadRequest() {
        // payment 400（统计窗口参数非法）→ admin 400（BAD_REQUEST）
        when(paymentClient.getOrderStatusDistribution())
                .thenThrow(new OpenApiClientException(400, "{\"message\":\"bad window\"}"));

        assertThatThrownBy(() -> paymentAppService.getOrderStatusDistribution())
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.BAD_REQUEST);
    }

    // ========== getGatewayHealth · tier-1 统计透传契约（银行接口 + 返回码分布） ==========

    @Test
    void given_gatewayHealth_when_getGatewayHealth_then_mapBankInterfacesAndReturnCodes() {
        when(paymentClient.getGatewayHealth()).thenReturn(new GatewayHealthWireResponse(List.of(
                new GatewayHealthWireResponse.BankInterfaceStatWireResponse(
                        "ICBC_PAY", 1000L, 980L, new BigDecimal("0.98"), 120L,
                        List.of(
                                new GatewayHealthWireResponse.BankInterfaceStatWireResponse.ReturnCodeStatWireResponse(
                                        "000000", 980L),
                                new GatewayHealthWireResponse.BankInterfaceStatWireResponse.ReturnCodeStatWireResponse(
                                        "9999", 20L))),
                new GatewayHealthWireResponse.BankInterfaceStatWireResponse(
                        "WECHAT_QUERY", 500L, 495L, new BigDecimal("0.99"), 80L,
                        List.of()))));

        GatewayHealthResponse health = paymentAppService.getGatewayHealth();

        assertThat(health.bankInterfaces()).hasSize(2);
        GatewayHealthResponse.BankInterfaceStat first = health.bankInterfaces().get(0);
        assertThat(first.bankInterface()).isEqualTo("ICBC_PAY");
        assertThat(first.callCount()).isEqualTo(1000L);
        assertThat(first.successCount()).isEqualTo(980L);
        assertThat(first.successRate()).isEqualByComparingTo("0.98");
        assertThat(first.avgExecutionTime()).isEqualTo(120L);
        // 返回码分布映射 + 顺序保留
        assertThat(first.returnCodes()).hasSize(2);
        assertThat(first.returnCodes().get(0).returnCode()).isEqualTo("000000");
        assertThat(first.returnCodes().get(0).count()).isEqualTo(980L);
        assertThat(first.returnCodes().get(1).returnCode()).isEqualTo("9999");
        // 第二个接口返回码为空列表（admin 透传空、非 null）
        assertThat(health.bankInterfaces().get(1).bankInterface()).isEqualTo("WECHAT_QUERY");
        assertThat(health.bankInterfaces().get(1).returnCodes()).isEmpty();
    }

    @Test
    void given_gatewayHealthDownstream500_when_getGatewayHealth_then_throwThirdPartyError() {
        when(paymentClient.getGatewayHealth())
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> paymentAppService.getGatewayHealth())
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }

    // ========== getOperationsAudit · tier-1 统计透传契约（顶层 + 按审核人聚合） ==========

    @Test
    void given_operationsAudit_when_getOperationsAudit_then_mapTopLevelAndAuditors() {
        when(paymentClient.getOperationsAudit()).thenReturn(new OperationsAuditWireResponse(
                60L, new BigDecimal("0.90"), 1800L,
                List.of(
                        new OperationsAuditWireResponse.AuditorStatWireResponse(
                                1001L, "alice", 40L, 38L, new BigDecimal("0.95"), 1500L),
                        new OperationsAuditWireResponse.AuditorStatWireResponse(
                                2002L, "bob", 20L, 16L, new BigDecimal("0.80"), 2100L))));

        OperationsAuditResponse audit = paymentAppService.getOperationsAudit();

        // 顶层映射
        assertThat(audit.auditCount()).isEqualTo(60L);
        assertThat(audit.approvalRate()).isEqualByComparingTo("0.90");
        assertThat(audit.avgAuditDurationSeconds()).isEqualTo(1800L);
        // 按审核人聚合映射 + 顺序保留
        assertThat(audit.auditors()).hasSize(2);
        assertThat(audit.auditors().get(0).auditorId()).isEqualTo(1001L);
        assertThat(audit.auditors().get(0).auditorName()).isEqualTo("alice");
        assertThat(audit.auditors().get(0).approvedCount()).isEqualTo(38L);
        assertThat(audit.auditors().get(0).approvalRate()).isEqualByComparingTo("0.95");
        assertThat(audit.auditors().get(1).auditorName()).isEqualTo("bob");
        assertThat(audit.auditors().get(1).approvalRate()).isEqualByComparingTo("0.80");
    }

    @Test
    void given_operationsAuditDownstream500_when_getOperationsAudit_then_throwThirdPartyError() {
        when(paymentClient.getOperationsAudit())
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> paymentAppService.getOperationsAudit())
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }

    // ========== getByBusinessSystem · tier-2 统计透传契约（按业务系统聚合·不改序） ==========

    @Test
    void given_businessSystemStats_when_getByBusinessSystem_then_mapSystemsAndPreserveOrder() {
        when(paymentClient.getByBusinessSystem()).thenReturn(new BusinessSystemStatsWireResponse(List.of(
                new BusinessSystemStatsWireResponse.BusinessSystemStatWireResponse(
                        "course-svc", 800L, new BigDecimal("64000.00"),
                        20L, new BigDecimal("1600.00"),
                        new BigDecimal("0.98"), new BigDecimal("0.025")),
                new BusinessSystemStatsWireResponse.BusinessSystemStatWireResponse(
                        "membership-svc", 400L, new BigDecimal("32000.00"),
                        10L, new BigDecimal("800.00"),
                        new BigDecimal("0.95"), new BigDecimal("0.025")))));

        BusinessSystemStatsResponse stats = paymentAppService.getByBusinessSystem();

        // 按业务系统聚合映射 + 顺序保留
        assertThat(stats.systems()).hasSize(2);
        BusinessSystemStatsResponse.BusinessSystemStat first = stats.systems().get(0);
        assertThat(first.businessSystemName()).isEqualTo("course-svc");
        assertThat(first.paymentCount()).isEqualTo(800L);
        assertThat(first.paymentAmount()).isEqualByComparingTo("64000.00");
        assertThat(first.refundCount()).isEqualTo(20L);
        assertThat(first.refundAmount()).isEqualByComparingTo("1600.00");
        assertThat(first.successRate()).isEqualByComparingTo("0.98");
        assertThat(first.refundRate()).isEqualByComparingTo("0.025");
        assertThat(stats.systems().get(1).businessSystemName()).isEqualTo("membership-svc");
    }

    @Test
    void given_businessSystemStatsDownstream500_when_getByBusinessSystem_then_throwThirdPartyError() {
        // payment 5xx（内部错误）→ 统一对外 THIRD_PARTY_ERROR（运营侧已认证，下游故障为第三方错误）
        when(paymentClient.getByBusinessSystem())
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> paymentAppService.getByBusinessSystem())
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }

    // ========== getByChannel · tier-2 统计透传契约（payMode / accessType 双维度·不改序） ==========

    @Test
    void given_channelStats_when_getByChannel_then_mapBothDimensionsAndPreserveOrder() {
        when(paymentClient.getByChannel()).thenReturn(new ChannelStatsWireResponse(
                List.of(new ChannelStatsWireResponse.PayModeStatWireResponse(
                                "WECHAT", 600L, new BigDecimal("48000.00"), new BigDecimal("0.98")),
                        new ChannelStatsWireResponse.PayModeStatWireResponse(
                                "ALIPAY", 400L, new BigDecimal("32000.00"), new BigDecimal("0.96"))),
                List.of(new ChannelStatsWireResponse.AccessTypeStatWireResponse(
                        "WEB", 700L, new BigDecimal("56000.00"), new BigDecimal("0.97")))));

        ChannelStatsResponse stats = paymentAppService.getByChannel();

        // payMode 维度映射 + 顺序保留
        assertThat(stats.byPayMode()).hasSize(2);
        assertThat(stats.byPayMode().get(0).payMode()).isEqualTo("WECHAT");
        assertThat(stats.byPayMode().get(0).paymentCount()).isEqualTo(600L);
        assertThat(stats.byPayMode().get(0).paymentAmount()).isEqualByComparingTo("48000.00");
        assertThat(stats.byPayMode().get(0).successRate()).isEqualByComparingTo("0.98");
        assertThat(stats.byPayMode().get(1).payMode()).isEqualTo("ALIPAY");
        // accessType 维度映射
        assertThat(stats.byAccessType()).hasSize(1);
        assertThat(stats.byAccessType().get(0).accessType()).isEqualTo("WEB");
        assertThat(stats.byAccessType().get(0).paymentAmount()).isEqualByComparingTo("56000.00");
        assertThat(stats.byAccessType().get(0).successRate()).isEqualByComparingTo("0.97");
    }

    @Test
    void given_channelStatsDownstream400_when_getByChannel_then_throwBadRequest() {
        // payment 400（统计窗口参数非法）→ admin 400（BAD_REQUEST）
        when(paymentClient.getByChannel())
                .thenThrow(new OpenApiClientException(400, "{\"message\":\"bad window\"}"));

        assertThatThrownBy(() -> paymentAppService.getByChannel())
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.BAD_REQUEST);
    }

    // ========== getAnomalies · tier-2 统计透传契约（异常计数） ==========

    @Test
    void given_anomalies_when_getAnomalies_then_mapCounts() {
        when(paymentClient.getAnomalies()).thenReturn(
                new AnomaliesWireResponse(12L, 3L, 8L));

        AnomaliesResponse anomalies = paymentAppService.getAnomalies();

        // 长时滞留 + 近期失败计数逐字段映射
        assertThat(anomalies.longPendingCount()).isEqualTo(12L);
        assertThat(anomalies.longRefundingCount()).isEqualTo(3L);
        assertThat(anomalies.recentFailureCount()).isEqualTo(8L);
    }

    @Test
    void given_anomaliesDownstream500_when_getAnomalies_then_throwThirdPartyError() {
        when(paymentClient.getAnomalies())
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> paymentAppService.getAnomalies())
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }

    // ========== getOperationsActivity · tier-2 统计透传契约（操作员活动 + 操作类型明细·不改序） ==========

    @Test
    void given_operationsActivity_when_getOperationsActivity_then_mapOperatorsAndOperationCountsPreserveOrder() {
        when(paymentClient.getOperationsActivity()).thenReturn(new OperationsActivityWireResponse(List.of(
                new OperationsActivityWireResponse.OperatorActivityStatWireResponse(
                        1001L, "alice",
                        List.of(new OperationsActivityWireResponse.OperationCountWireResponse("AUDIT_APPROVE", 38L),
                                new OperationsActivityWireResponse.OperationCountWireResponse("AUDIT_REJECT", 2L)),
                        5L),
                new OperationsActivityWireResponse.OperatorActivityStatWireResponse(
                        2002L, "bob",
                        List.of(new OperationsActivityWireResponse.OperationCountWireResponse("NOTIFY_RESEND", 3L)),
                        3L))));

        OperationsActivityResponse activity = paymentAppService.getOperationsActivity();

        // 按操作员聚合映射 + 顺序保留
        assertThat(activity.operators()).hasSize(2);
        OperationsActivityResponse.OperatorActivityStat first = activity.operators().get(0);
        assertThat(first.operatorId()).isEqualTo(1001L);
        assertThat(first.operatorName()).isEqualTo("alice");
        assertThat(first.notificationResendCount()).isEqualTo(5L);
        // 操作类型·笔数明细映射 + 顺序保留
        assertThat(first.operations()).hasSize(2);
        assertThat(first.operations().get(0).operation()).isEqualTo("AUDIT_APPROVE");
        assertThat(first.operations().get(0).count()).isEqualTo(38L);
        assertThat(first.operations().get(1).operation()).isEqualTo("AUDIT_REJECT");
        OperationsActivityResponse.OperatorActivityStat second = activity.operators().get(1);
        assertThat(second.operatorName()).isEqualTo("bob");
        assertThat(second.operations()).hasSize(1);
        assertThat(second.operations().get(0).operation()).isEqualTo("NOTIFY_RESEND");
        assertThat(second.notificationResendCount()).isEqualTo(3L);
    }

    @Test
    void given_operationsActivityDownstream500_when_getOperationsActivity_then_throwThirdPartyError() {
        when(paymentClient.getOperationsActivity())
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> paymentAppService.getOperationsActivity())
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }
}
