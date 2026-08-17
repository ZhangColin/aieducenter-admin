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
 *
 * <p>fixture 枚举值为 payment {@code BaseEnum} 的 Integer code（非 enum name），与 payment 真实序列化形状一致：
 * PaymentStatus PENDING=1/PAID=2、RefundStatus PENDING=1/APPROVED=3/SUCCESS=5/REJECTED=2、PayMode WECHAT=9/ALIPAY=10、
 * AccessType H5=4/APP=5、PaymentChannel ICBC=1、AuditType AUTO=1/MANUAL=2、OperationLogTargetType PAYMENT=1/REFUND=2、
 * OperationType AUDIT_APPROVE=1/AUDIT_REJECT=2/NOTIFY_RESEND=3。每个 code 在构造处紧邻其 {@code *Name} 中文名。</p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentBffIntegrationTest {

    @Autowired
    private PaymentManagementAppService paymentAppService;

    @MockBean
    private PaymentClient paymentClient;

    // 统计端点时间窗口（issue #51）：payment 要求 from/to 必填，admin 如实接收转发。所有统计透传测试共用此窗口。
    private static final LocalDateTime STATS_FROM = LocalDateTime.of(2026, 7, 14, 0, 0);
    private static final LocalDateTime STATS_TO = LocalDateTime.of(2026, 8, 13, 23, 59, 59);
    private static final String STATS_GRANULARITY = "DAY";

    // ========== list · DTO 映射 + 分页契约 ==========

    @Test
    void given_paymentOrders_when_list_then_returnMappedPage() {
        LocalDateTime now = LocalDateTime.now();
        when(paymentClient.listPayments(any(PaymentOrderListWireRequest.class), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(
                        new PaymentOrderWireResponse("PAY-1", "BIZ-1", "course-svc", 2, "已支付",
                                9900L, 9, "微信", 4, "H5", 1, "工商银行",
                                now, now.minusMinutes(5)),
                        new PaymentOrderWireResponse("PAY-2", "BIZ-2", "course-svc", 1, "待支付",
                                19900L, 10, "支付宝", 5, "APP", 1, "工商银行",
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
        assertThat(first.status()).isEqualTo(2);
        assertThat(first.amount()).isEqualTo(9900L);
        assertThat(first.payMode()).isEqualTo(9);
        assertThat(first.paidAt()).isEqualTo(now);
        PaymentOrderSummaryResponse second = page.items().get(1);
        assertThat(second.paymentOrderNo()).isEqualTo("PAY-2");
        assertThat(second.status()).isEqualTo(1);
        assertThat(second.paidAt()).isNull();
    }

    // ========== list · 筛选映射 + 页码换算 ==========

    @Test
    void given_filtersAndPageable_when_list_then_passQueryAndConvertPage() {
        when(paymentClient.listPayments(any(PaymentOrderListWireRequest.class), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0L, 2, 20));

        PaymentOrderQuery query = new PaymentOrderQuery(
                "PAY-1", "BIZ-1", "course-svc",
                List.of(2, 1), 9, 4, 1,
                1000L, 50000L,
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59),
                null, null);

        paymentAppService.list(query, PageRequest.of(2, 20));

        // query → wire 映射：筛选原样透传；Spring Pageable 0-based(page=2) → 客户端 1-based(page=3)
        PaymentOrderListWireRequest expectedWire = new PaymentOrderListWireRequest(
                "PAY-1", "BIZ-1", "course-svc",
                List.of(2, 1), 9, 4, 1,
                1000L, 50000L,
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
                        new RefundOrderWireResponse("RF-1", "PAY-1", "BIZ-1", "course-svc", 5, "退款成功",
                                9900L, 2, "人工审核", "alice", now.minusMinutes(10)),
                        new RefundOrderWireResponse("RF-2", "PAY-2", "BIZ-2", "course-svc", 1, "待审核",
                                19900L, 1, "免审", null, now.minusMinutes(1))
                ), 9L, 0, 20));

        var page = paymentAppService.listRefunds(
                new RefundOrderQuery(null, null, null, null, null, null, null,
                        null, null, null, null),
                PageRequest.of(0, 20));

        // 分页契约：total/page/size 沿用 payment 回显
        assertThat(page.total()).isEqualTo(9L);
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(20);
        // DTO 映射：wire → response 逐字段（金额 Long 分透传 ADR-0011；无 auditorId/auditedAt——payment ghost）
        assertThat(page.items()).hasSize(2);
        RefundOrderSummaryResponse first = page.items().get(0);
        assertThat(first.refundOrderNo()).isEqualTo("RF-1");
        assertThat(first.paymentOrderNo()).isEqualTo("PAY-1");
        assertThat(first.businessOrderNo()).isEqualTo("BIZ-1");
        assertThat(first.status()).isEqualTo(5);
        assertThat(first.refundAmount()).isEqualTo(9900L);
        assertThat(first.auditType()).isEqualTo(2);
        assertThat(first.auditorName()).isEqualTo("alice");
        RefundOrderSummaryResponse second = page.items().get(1);
        assertThat(second.refundOrderNo()).isEqualTo("RF-2");
        assertThat(second.status()).isEqualTo(1);
        assertThat(second.auditType()).isEqualTo(1);
        assertThat(second.auditorName()).isNull();
    }

    // ========== listRefunds · 筛选映射 + 页码换算 ==========

    @Test
    void given_refundFiltersAndPageable_when_listRefunds_then_passQueryAndConvertPage() {
        when(paymentClient.listRefunds(any(RefundOrderListWireRequest.class), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0L, 2, 20));

        RefundOrderQuery query = new RefundOrderQuery(
                "RF-1", "PAY-1", "BIZ-1", "course-svc",
                List.of(1, 3), 2, 1001L,
                1000L, 50000L,
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59));

        paymentAppService.listRefunds(query, PageRequest.of(2, 20));

        // query → wire 映射：筛选原样透传（auditorId 保留——payment 支持按审核人筛选；金额区间 Long 分）；
        // Spring Pageable 0-based(page=2) → 客户端 1-based(page=3)
        RefundOrderListWireRequest expectedWire = new RefundOrderListWireRequest(
                "RF-1", "PAY-1", "BIZ-1", "course-svc",
                List.of(1, 3), 2, 1001L,
                1000L, 50000L,
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
                        new OperationLogWireResponse(8001L, 1, "支付订单", "PAY-1", 3, "通知重发",
                                1001L, "alice", "admin-console", "SUCCESS", "manual resend", now.minusMinutes(3)),
                        new OperationLogWireResponse(8002L, 2, "退款订单", "RF-1", 2, "审核拒绝",
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
        assertThat(first.targetType()).isEqualTo(1);
        assertThat(first.targetNo()).isEqualTo("PAY-1");
        assertThat(first.operation()).isEqualTo(3);
        assertThat(first.operatorId()).isEqualTo(1001L);
        assertThat(first.operatorName()).isEqualTo("alice");
        assertThat(first.operatorSystem()).isEqualTo("admin-console");
        assertThat(first.result()).isEqualTo("SUCCESS");
        assertThat(first.remark()).isEqualTo("manual resend");
        assertThat(first.createdAt()).isEqualTo(now.minusMinutes(3));
        OperationLogSummaryResponse second = page.items().get(1);
        assertThat(second.targetType()).isEqualTo(2);
        assertThat(second.operation()).isEqualTo(2);
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
                1, "PAY-1", 3, 1001L, "admin-console", "SUCCESS",
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59));

        paymentAppService.listOperationLogs(query, PageRequest.of(2, 20));

        // query → wire 映射：筛选原样透传；Spring Pageable 0-based(page=2) → 客户端 1-based(page=3)
        OperationLogListWireRequest expectedWire = new OperationLogListWireRequest(
                1, "PAY-1", 3, 1001L, "admin-console", "SUCCESS",
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
                new PaymentOrderDetailWireResponse("PAY-1", "BIZ-1", "course-svc", 2, "已支付",
                        9900L, 9, "微信", 4, "H5", 1, "工商银行",
                        now, now.minusMinutes(5)));

        PaymentOrderDetailResponse detail = paymentAppService.getPaymentDetail("PAY-1");

        // 详情聚合逐字段映射
        assertThat(detail.paymentOrderNo()).isEqualTo("PAY-1");
        assertThat(detail.businessOrderNo()).isEqualTo("BIZ-1");
        assertThat(detail.businessSystemName()).isEqualTo("course-svc");
        assertThat(detail.status()).isEqualTo(2);
        assertThat(detail.amount()).isEqualTo(9900L);
        assertThat(detail.payMode()).isEqualTo(9);
        assertThat(detail.accessType()).isEqualTo(4);
        assertThat(detail.paymentChannel()).isEqualTo(1);
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
                new RefundOrderDetailWireResponse("RF-1", "PAY-1", "BIZ-1", "course-svc", 5, "退款成功",
                        9900L, 2, "人工审核", "alice", now.minusMinutes(10)));

        RefundOrderDetailResponse detail = paymentAppService.getRefundDetail("RF-1");

        // 详情聚合逐字段映射（金额 Long 分；无 auditorId/auditedAt——payment ghost）
        assertThat(detail.refundOrderNo()).isEqualTo("RF-1");
        assertThat(detail.paymentOrderNo()).isEqualTo("PAY-1");
        assertThat(detail.businessOrderNo()).isEqualTo("BIZ-1");
        assertThat(detail.businessSystemName()).isEqualTo("course-svc");
        assertThat(detail.status()).isEqualTo(5);
        assertThat(detail.refundAmount()).isEqualTo(9900L);
        assertThat(detail.auditType()).isEqualTo(2);
        assertThat(detail.auditorName()).isEqualTo("alice");
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
    void given_mergedTimeline_when_getLifecycle_then_preserveOrderAndMapSemanticEvents() {
        LocalDateTime t1 = LocalDateTime.of(2026, 8, 12, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 8, 12, 10, 5);
        // 故意让 payment 回传的顺序与时间序相反（t2 在前、t1 在后）——证明 admin 是透传、不重排：
        // 若 admin 偷偷按 createdAt 排序，事件顺序会变成 t1/t2（与输入相反），断言即失败。
        // 合并与排序归 payment（ADR-0002），admin 只 map 不 sort。
        when(paymentClient.getLifecycle("PAY-1")).thenReturn(List.of(
                new OrderLifecycleWireResponse(
                        1001L, "OPERATION", t2,
                        "AUDIT_APPROVE", "审核通过", "SUCCESS",
                        "alice", "admin-console", "同意退款"),
                new OrderLifecycleWireResponse(
                        501L, "GATEWAY", t1,
                        "PAYMENT_REQUEST", "PAYMENT_REQUEST", "SUCCESS",
                        "ICBC_PAY", "ICBC", "交易成功")
        ));

        List<OrderLifecycleResponse> lifecycle = paymentAppService.getLifecycle("PAY-1");

        // 扁平事件列表，无 orderNo 包装（契约对齐 issue #57：payment 实返 ApiResponse<List<事件>>）
        assertThat(lifecycle).hasSize(2);
        // 顺序原样保留（payment 给的 t2→t1，admin 不重排为 t1→t2）
        OrderLifecycleResponse first = lifecycle.get(0);
        assertThat(first.id()).isEqualTo(1001L);
        assertThat(first.source()).isEqualTo("OPERATION");
        assertThat(first.createdAt()).isEqualTo(t2);
        // 语义 9 字段映射（action=operation 枚举名、actionName=中文名、performer=操作人、detail=备注）
        assertThat(first.action()).isEqualTo("AUDIT_APPROVE");
        assertThat(first.actionName()).isEqualTo("审核通过");
        assertThat(first.outcome()).isEqualTo("SUCCESS");
        assertThat(first.performer()).isEqualTo("alice");
        assertThat(first.performerSystem()).isEqualTo("admin-console");
        assertThat(first.detail()).isEqualTo("同意退款");

        OrderLifecycleResponse second = lifecycle.get(1);
        assertThat(second.id()).isEqualTo(501L);
        assertThat(second.source()).isEqualTo("GATEWAY");
        assertThat(second.createdAt()).isEqualTo(t1);
        // 网关事件：action=logType、performer=bankInterface、performerSystem=bankCode、detail=returnMsg
        assertThat(second.action()).isEqualTo("PAYMENT_REQUEST");
        assertThat(second.actionName()).isEqualTo("PAYMENT_REQUEST");
        assertThat(second.outcome()).isEqualTo("SUCCESS");
        assertThat(second.performer()).isEqualTo("ICBC_PAY");
        assertThat(second.performerSystem()).isEqualTo("ICBC");
        assertThat(second.detail()).isEqualTo("交易成功");
    }

    @Test
    void given_lifecycleNullWireList_when_getLifecycle_then_emptyList() {
        when(paymentClient.getLifecycle("PAY-1")).thenReturn(null);

        assertThat(paymentAppService.getLifecycle("PAY-1")).isEmpty();
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
                new RefundOrderDetailWireResponse("RF-1", "PAY-1", "BIZ-1", "course-svc", 3, "已批准",
                        9900L, 2, "人工审核", "alice", now.minusMinutes(10)));

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

        // 响应映射：审核后退款单聚合（与详情同形，无 auditorId/auditedAt——payment ghost）
        assertThat(detail.refundOrderNo()).isEqualTo("RF-1");
        assertThat(detail.status()).isEqualTo(3);
        assertThat(detail.auditType()).isEqualTo(2);
        assertThat(detail.auditorName()).isEqualTo("alice");
        assertThat(detail.refundAmount()).isEqualTo(9900L);
    }

    @Test
    void given_auditReject_when_auditRefund_then_wireRequestCarriesAgreedFalse() {
        when(paymentClient.auditRefund(eq("RF-2"), any(AuditRefundWireRequest.class))).thenReturn(
                new RefundOrderDetailWireResponse("RF-2", null, null, null, 2, "已拒绝",
                        null, 2, "人工审核", "bob", null));

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
                new RefundOrderDetailWireResponse("RF-3", null, null, null, 3, "已批准",
                        null, 2, "人工审核", "carol", null));

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
                new PaymentOrderDetailWireResponse("PAY-1", "BIZ-1", "course-svc", 2, "已支付",
                        9900L, 9, "微信", 4, "H5", 1, "工商银行",
                        now, now.minusMinutes(5)));

        PaymentOrderDetailResponse detail = paymentAppService.queryPayment("PAY-1");

        // 复用 toPaymentDetail 映射：查行结果与详情同形、逐字段映射
        assertThat(detail.paymentOrderNo()).isEqualTo("PAY-1");
        assertThat(detail.businessOrderNo()).isEqualTo("BIZ-1");
        assertThat(detail.businessSystemName()).isEqualTo("course-svc");
        assertThat(detail.status()).isEqualTo(2);
        assertThat(detail.amount()).isEqualTo(9900L);
        assertThat(detail.payMode()).isEqualTo(9);
        assertThat(detail.paymentChannel()).isEqualTo(1);
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
                .thenReturn(new PaymentOrderDetailWireResponse("PAY-1", "BIZ-1", "course-svc", 2, "已支付",
                        9900L, 9, "微信", 4, "H5", 1, "工商银行",
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
        assertThat(detail.status()).isEqualTo(2);
        assertThat(detail.amount()).isEqualTo(9900L);
        assertThat(detail.payMode()).isEqualTo(9);
        assertThat(detail.paidAt()).isEqualTo(now);
    }

    // ========== resendRefundNotification · 操作者身份透传 + 不改订单状态 ==========

    @Test
    void given_refundNotificationResent_when_resendRefundNotification_then_wireRequestCarriesOperatorAndReturnsMappedDetail() {
        LocalDateTime now = LocalDateTime.now();
        // payment 返回当前退款单聚合（状态未变——SUCCESS），证明通知重发不改订单状态
        when(paymentClient.resendRefundNotification(eq("RF-1"), any(ResendNotificationWireRequest.class)))
                .thenReturn(new RefundOrderDetailWireResponse("RF-1", "PAY-1", "BIZ-1", "course-svc", 5, "退款成功",
                        9900L, 2, "人工审核", "alice", now.minusMinutes(10)));

        RefundOrderDetailResponse detail = paymentAppService.resendRefundNotification("RF-1", 2002L, "bob");

        ArgumentCaptor<ResendNotificationWireRequest> captor =
                ArgumentCaptor.forClass(ResendNotificationWireRequest.class);
        verify(paymentClient).resendRefundNotification(eq("RF-1"), captor.capture());
        ResendNotificationWireRequest wire = captor.getValue();
        assertThat(wire.operatorId()).isEqualTo(2002L);
        assertThat(wire.operatorName()).isEqualTo("bob");

        // 响应映射：当前退款单聚合（状态未变 SUCCESS），复用 toRefundDetail
        assertThat(detail.refundOrderNo()).isEqualTo("RF-1");
        assertThat(detail.status()).isEqualTo(5);
        assertThat(detail.refundAmount()).isEqualTo(9900L);
        assertThat(detail.auditType()).isEqualTo(2);
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
    void given_overviewWithTrend_when_getPaymentOverview_then_mapSummariesNetAmountAndBucketsPreserveOrder() {
        LocalDateTime b1 = LocalDateTime.of(2026, 8, 11, 0, 0);
        LocalDateTime b2 = LocalDateTime.of(2026, 8, 12, 0, 0);
        // 故意让 payment 回传顺序与时间序相反（b2 在前、b1 在后）——证明 admin 透传不重排：
        // 聚合/排序归 payment（spec「仪表盘」），admin 只 map 不 sort。
        when(paymentClient.getPaymentOverview(STATS_FROM, STATS_TO, STATS_GRANULARITY)).thenReturn(new PaymentOverviewWireResponse(
                new PaymentOverviewWireResponse.SummaryWireResponse(
                        1200L, 9800000L, 1182L, 9650000L, new BigDecimal("0.9850")),
                new PaymentOverviewWireResponse.SummaryWireResponse(
                        30L, 240000L, 25L, 200000L, new BigDecimal("0.8333")),
                9450000L,
                List.of(
                        new PaymentOverviewWireResponse.TrendBucketWireResponse(
                                b2, 20L, 160000L, 19L, 152000L, 1L, 8000L, 1L, 8000L),
                        new PaymentOverviewWireResponse.TrendBucketWireResponse(
                                b1, 18L, 144000L, 18L, 144000L, 0L, 0L, 0L, 0L))));

        PaymentOverviewResponse overview = paymentAppService.getPaymentOverview(STATS_FROM, STATS_TO, STATS_GRANULARITY);

        // 嵌套支付/退款摘要逐字段映射（金额 Long 分、比率 BigDecimal——provider 契约分型）
        assertThat(overview.payment().count()).isEqualTo(1200L);
        assertThat(overview.payment().amount()).isEqualTo(9800000L);
        assertThat(overview.payment().successCount()).isEqualTo(1182L);
        assertThat(overview.payment().successAmount()).isEqualTo(9650000L);
        assertThat(overview.payment().successRate()).isEqualByComparingTo("0.9850");
        assertThat(overview.refund().count()).isEqualTo(30L);
        assertThat(overview.refund().amount()).isEqualTo(240000L);
        assertThat(overview.netAmount()).isEqualTo(9450000L);
        // 趋势分桶（9 字段，含成功子集）顺序原样保留（payment 给 b2→b1，admin 不重排为 b1→b2）
        assertThat(overview.trend()).hasSize(2);
        assertThat(overview.trend().get(0).bucket()).isEqualTo(b2);
        assertThat(overview.trend().get(0).paymentCount()).isEqualTo(20L);
        assertThat(overview.trend().get(0).paidAmount()).isEqualTo(152000L);
        assertThat(overview.trend().get(0).refundAmount()).isEqualTo(8000L);
        assertThat(overview.trend().get(1).bucket()).isEqualTo(b1);
        assertThat(overview.trend().get(1).refundCount()).isZero();
    }

    @Test
    void given_overviewDownstream500_when_getPaymentOverview_then_throwThirdPartyError() {
        // payment 5xx（内部错误）→ 统一对外 THIRD_PARTY_ERROR（运营侧已认证，下游故障为第三方错误）
        when(paymentClient.getPaymentOverview(STATS_FROM, STATS_TO, STATS_GRANULARITY))
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> paymentAppService.getPaymentOverview(STATS_FROM, STATS_TO, STATS_GRANULARITY))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }

    // ========== getOrderStatusDistribution · tier-1 统计透传契约 ==========

    @Test
    void given_statusDistribution_when_getOrderStatusDistribution_then_mapBucketsAndBacklog() {
        when(paymentClient.getOrderStatusDistribution()).thenReturn(new OrderStatusDistributionWireResponse(
                List.of(new OrderStatusDistributionWireResponse.PaymentStatusBucketWireResponse(
                                2, "已支付", 800L, 6400000L),
                        new OrderStatusDistributionWireResponse.PaymentStatusBucketWireResponse(
                                1, "待支付", 50L, 400000L)),
                List.of(new OrderStatusDistributionWireResponse.RefundStatusBucketWireResponse(
                                5, "退款成功", 25L, 200000L),
                        new OrderStatusDistributionWireResponse.RefundStatusBucketWireResponse(
                                1, "待审核", 5L, 40000L)),
                new OrderStatusDistributionWireResponse.BacklogWireResponse(5L, 40000L)));

        OrderStatusDistributionResponse dist = paymentAppService.getOrderStatusDistribution();

        // 支付状态分布映射 + 顺序保留
        assertThat(dist.paymentStatuses()).hasSize(2);
        assertThat(dist.paymentStatuses().get(0).status()).isEqualTo(2);
        assertThat(dist.paymentStatuses().get(0).count()).isEqualTo(800L);
        assertThat(dist.paymentStatuses().get(0).amount()).isEqualTo(6400000L);
        assertThat(dist.paymentStatuses().get(1).status()).isEqualTo(1);
        // 退款状态分布映射
        assertThat(dist.refundStatuses()).hasSize(2);
        assertThat(dist.refundStatuses().get(0).status()).isEqualTo(5);
        assertThat(dist.refundStatuses().get(1).status()).isEqualTo(1);
        assertThat(dist.refundStatuses().get(1).amount()).isEqualTo(40000L);
        // 退款待审核积压：嵌套 refundBacklog（笔数 + 金额分）——积压金额首次透出（#60）
        assertThat(dist.refundBacklog().pendingCount()).isEqualTo(5L);
        assertThat(dist.refundBacklog().pendingAmount()).isEqualTo(40000L);
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
    void given_gatewayHealth_when_getGatewayHealth_then_mapInterfacesAndReturnCodes() {
        when(paymentClient.getGatewayHealth(STATS_FROM, STATS_TO)).thenReturn(new GatewayHealthWireResponse(List.of(
                new GatewayHealthWireResponse.InterfaceHealthWireResponse(
                        "ICBC", "ICBC_PAY", 1000L, 980L, new BigDecimal("0.9800"), new BigDecimal("120.50"),
                        List.of(
                                new GatewayHealthWireResponse.InterfaceHealthWireResponse.ReturnCodeCountWireResponse(
                                        "000000", 980L),
                                new GatewayHealthWireResponse.InterfaceHealthWireResponse.ReturnCodeCountWireResponse(
                                        "9999", 20L))),
                new GatewayHealthWireResponse.InterfaceHealthWireResponse(
                        "WECHAT", "WECHAT_QUERY", 500L, 495L, new BigDecimal("0.9900"), new BigDecimal("80.00"),
                        List.of()))));

        GatewayHealthResponse health = paymentAppService.getGatewayHealth(STATS_FROM, STATS_TO);

        assertThat(health.interfaces()).hasSize(2);
        GatewayHealthResponse.InterfaceHealth first = health.interfaces().get(0);
        assertThat(first.bankCode()).isEqualTo("ICBC");
        assertThat(first.bankInterface()).isEqualTo("ICBC_PAY");
        assertThat(first.totalCount()).isEqualTo(1000L);
        assertThat(first.successCount()).isEqualTo(980L);
        assertThat(first.successRate()).isEqualByComparingTo("0.9800");
        assertThat(first.avgExecutionTimeMs()).isEqualByComparingTo("120.50");
        // 返回码分布映射 + 顺序保留
        assertThat(first.returnCodes()).hasSize(2);
        assertThat(first.returnCodes().get(0).returnCode()).isEqualTo("000000");
        assertThat(first.returnCodes().get(0).count()).isEqualTo(980L);
        assertThat(first.returnCodes().get(1).returnCode()).isEqualTo("9999");
        // 第二个接口返回码为空列表（admin 透传空、非 null）
        assertThat(health.interfaces().get(1).bankInterface()).isEqualTo("WECHAT_QUERY");
        assertThat(health.interfaces().get(1).returnCodes()).isEmpty();
    }

    @Test
    void given_gatewayHealthDownstream500_when_getGatewayHealth_then_throwThirdPartyError() {
        when(paymentClient.getGatewayHealth(STATS_FROM, STATS_TO))
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> paymentAppService.getGatewayHealth(STATS_FROM, STATS_TO))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }

    // ========== getOperationsAudit · tier-1 统计透传契约（顶层 + 按审核人聚合） ==========

    @Test
    void given_operationsAudit_when_getOperationsAudit_then_mapTopLevelAndAuditorBreakdowns() {
        when(paymentClient.getOperationsAudit(STATS_FROM, STATS_TO)).thenReturn(new OperationsAuditWireResponse(
                60L, 54L, 6L, new BigDecimal("0.9000"), new BigDecimal("30.50"),
                List.of(
                        new OperationsAuditWireResponse.AuditorBreakdownWireResponse(
                                1001L, "alice", 40L, 38L, 2L, new BigDecimal("0.9500")),
                        new OperationsAuditWireResponse.AuditorBreakdownWireResponse(
                                2002L, "bob", 20L, 16L, 4L, new BigDecimal("0.8000")))));

        OperationsAuditResponse audit = paymentAppService.getOperationsAudit(STATS_FROM, STATS_TO);

        // 顶层映射（均值单位分钟、BigDecimal——provider 契约）
        assertThat(audit.totalAudits()).isEqualTo(60L);
        assertThat(audit.approvedCount()).isEqualTo(54L);
        assertThat(audit.rejectedCount()).isEqualTo(6L);
        assertThat(audit.approvalRate()).isEqualByComparingTo("0.9000");
        assertThat(audit.avgAuditDurationMinutes()).isEqualByComparingTo("30.50");
        // 按审核人聚合映射 + 顺序保留
        assertThat(audit.byAuditor()).hasSize(2);
        assertThat(audit.byAuditor().get(0).auditorId()).isEqualTo(1001L);
        assertThat(audit.byAuditor().get(0).auditorName()).isEqualTo("alice");
        assertThat(audit.byAuditor().get(0).approvedCount()).isEqualTo(38L);
        assertThat(audit.byAuditor().get(0).approvalRate()).isEqualByComparingTo("0.9500");
        assertThat(audit.byAuditor().get(1).auditorName()).isEqualTo("bob");
        assertThat(audit.byAuditor().get(1).approvalRate()).isEqualByComparingTo("0.8000");
    }

    @Test
    void given_operationsAuditDownstream500_when_getOperationsAudit_then_throwThirdPartyError() {
        when(paymentClient.getOperationsAudit(STATS_FROM, STATS_TO))
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> paymentAppService.getOperationsAudit(STATS_FROM, STATS_TO))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }

    // ========== getByBusinessSystem · tier-2 统计透传契约（按业务系统聚合·不改序） ==========

    @Test
    void given_businessSystemStats_when_getByBusinessSystem_then_mapBreakdownsAndPreserveOrder() {
        when(paymentClient.getByBusinessSystem(STATS_FROM, STATS_TO)).thenReturn(new BusinessSystemStatsWireResponse(List.of(
                new BusinessSystemStatsWireResponse.BusinessSystemBreakdownWireResponse(
                        "course-svc",
                        new BusinessSystemStatsWireResponse.SummaryWireResponse(
                                800L, 6400000L, 784L, 6272000L, new BigDecimal("0.9800")),
                        new BusinessSystemStatsWireResponse.SummaryWireResponse(
                                20L, 160000L, 16L, 128000L, new BigDecimal("0.8000")),
                        new BigDecimal("0.0204")),
                new BusinessSystemStatsWireResponse.BusinessSystemBreakdownWireResponse(
                        "membership-svc",
                        new BusinessSystemStatsWireResponse.SummaryWireResponse(
                                400L, 3200000L, 380L, 3040000L, new BigDecimal("0.9500")),
                        new BusinessSystemStatsWireResponse.SummaryWireResponse(
                                10L, 80000L, 10L, 80000L, new BigDecimal("1.0000")),
                        new BigDecimal("0.0263")))));

        BusinessSystemStatsResponse stats = paymentAppService.getByBusinessSystem(STATS_FROM, STATS_TO);

        // 按业务系统聚合映射（嵌套支付/退款摘要）+ 顺序保留
        assertThat(stats.businessSystems()).hasSize(2);
        BusinessSystemStatsResponse.BusinessSystemBreakdown first = stats.businessSystems().get(0);
        assertThat(first.businessSystemName()).isEqualTo("course-svc");
        assertThat(first.payment().count()).isEqualTo(800L);
        assertThat(first.payment().amount()).isEqualTo(6400000L);
        assertThat(first.payment().successRate()).isEqualByComparingTo("0.9800");
        assertThat(first.refund().count()).isEqualTo(20L);
        assertThat(first.refund().amount()).isEqualTo(160000L);
        assertThat(first.refundRate()).isEqualByComparingTo("0.0204");
        assertThat(stats.businessSystems().get(1).businessSystemName()).isEqualTo("membership-svc");
    }

    @Test
    void given_businessSystemStatsDownstream500_when_getByBusinessSystem_then_throwThirdPartyError() {
        // payment 5xx（内部错误）→ 统一对外 THIRD_PARTY_ERROR（运营侧已认证，下游故障为第三方错误）
        when(paymentClient.getByBusinessSystem(STATS_FROM, STATS_TO))
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> paymentAppService.getByBusinessSystem(STATS_FROM, STATS_TO))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }

    // ========== getByChannel · tier-2 统计透传契约（payMode / accessType 双维度·不改序） ==========

    @Test
    void given_channelStats_when_getByChannel_then_mapBothDimensionsAndPreserveOrder() {
        when(paymentClient.getByChannel(STATS_FROM, STATS_TO)).thenReturn(new ChannelStatsWireResponse(
                List.of(new ChannelStatsWireResponse.ChannelBreakdownWireResponse(
                                9, "微信", 600L, 4800000L, 588L, 4704000L, new BigDecimal("0.9800")),
                        new ChannelStatsWireResponse.ChannelBreakdownWireResponse(
                                10, "支付宝", 400L, 3200000L, 384L, 3072000L, new BigDecimal("0.9600"))),
                List.of(new ChannelStatsWireResponse.ChannelBreakdownWireResponse(
                        4, "H5", 700L, 5600000L, 679L, 5432000L, new BigDecimal("0.9700")))));

        ChannelStatsResponse stats = paymentAppService.getByChannel(STATS_FROM, STATS_TO);

        // payMode 维度映射（枚举 code + 中文名）+ 顺序保留
        assertThat(stats.byPayMode()).hasSize(2);
        assertThat(stats.byPayMode().get(0).channelCode()).isEqualTo(9);
        assertThat(stats.byPayMode().get(0).channelName()).isEqualTo("微信");
        assertThat(stats.byPayMode().get(0).count()).isEqualTo(600L);
        assertThat(stats.byPayMode().get(0).amount()).isEqualTo(4800000L);
        assertThat(stats.byPayMode().get(0).successRate()).isEqualByComparingTo("0.9800");
        assertThat(stats.byPayMode().get(1).channelName()).isEqualTo("支付宝");
        // accessType 维度映射
        assertThat(stats.byAccessType()).hasSize(1);
        assertThat(stats.byAccessType().get(0).channelCode()).isEqualTo(4);
        assertThat(stats.byAccessType().get(0).channelName()).isEqualTo("H5");
        assertThat(stats.byAccessType().get(0).amount()).isEqualTo(5600000L);
        assertThat(stats.byAccessType().get(0).successRate()).isEqualByComparingTo("0.9700");
    }

    @Test
    void given_channelStatsDownstream400_when_getByChannel_then_throwBadRequest() {
        // payment 400（统计窗口参数非法）→ admin 400（BAD_REQUEST）
        when(paymentClient.getByChannel(STATS_FROM, STATS_TO))
                .thenThrow(new OpenApiClientException(400, "{\"message\":\"bad window\"}"));

        assertThatThrownBy(() -> paymentAppService.getByChannel(STATS_FROM, STATS_TO))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.BAD_REQUEST);
    }

    // ========== getAnomalies · tier-2 统计透传契约（异常计数） ==========

    @Test
    void given_anomalies_when_getAnomalies_then_mapNestedStuckOrdersAndFailures() {
        when(paymentClient.getAnomalies()).thenReturn(new AnomaliesWireResponse(
                new AnomaliesWireResponse.StuckOrdersWireResponse(12L, 96000L),
                new AnomaliesWireResponse.StuckOrdersWireResponse(3L, 24000L),
                new AnomaliesWireResponse.RecentFailuresWireResponse(8L, List.of(
                        new AnomaliesWireResponse.FailureCountWireResponse("PAYMENT_QUERY", 5L),
                        new AnomaliesWireResponse.FailureCountWireResponse("REFUND_QUERY", 3L)))));

        AnomaliesResponse anomalies = paymentAppService.getAnomalies();

        // 长时滞留（笔数 + 金额分）+ 近期失败（总数 + 按日志类型）嵌套逐字段映射
        assertThat(anomalies.longPendingPayments().count()).isEqualTo(12L);
        assertThat(anomalies.longPendingPayments().amount()).isEqualTo(96000L);
        assertThat(anomalies.longRefundingRefunds().count()).isEqualTo(3L);
        assertThat(anomalies.longRefundingRefunds().amount()).isEqualTo(24000L);
        assertThat(anomalies.recentFailures().totalCount()).isEqualTo(8L);
        assertThat(anomalies.recentFailures().byType()).hasSize(2);
        assertThat(anomalies.recentFailures().byType().get(0).logType()).isEqualTo("PAYMENT_QUERY");
        assertThat(anomalies.recentFailures().byType().get(0).failureCount()).isEqualTo(5L);
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
    void given_operationsActivity_when_getOperationsActivity_then_mapByOperatorAndNotifyResendPreserveOrder() {
        when(paymentClient.getOperationsActivity(STATS_FROM, STATS_TO)).thenReturn(new OperationsActivityWireResponse(
                List.of(
                        new OperationsActivityWireResponse.OperatorActivityWireResponse(
                                1001L, "alice", 40L,
                                List.of(new OperationsActivityWireResponse.OperationCountWireResponse(1, "审核通过", 38L),
                                        new OperationsActivityWireResponse.OperationCountWireResponse(2, "审核拒绝", 2L))),
                        new OperationsActivityWireResponse.OperatorActivityWireResponse(
                                2002L, "bob", 3L,
                                List.of(new OperationsActivityWireResponse.OperationCountWireResponse(3, "通知重发", 3L)))),
                new OperationsActivityWireResponse.NotifyResendActivityWireResponse(3L, List.of(
                        new OperationsActivityWireResponse.SystemResendCountWireResponse("course-svc", 2L),
                        new OperationsActivityWireResponse.SystemResendCountWireResponse(null, 1L)))));

        OperationsActivityResponse activity = paymentAppService.getOperationsActivity(STATS_FROM, STATS_TO);

        // 按操作员聚合映射 + 顺序保留
        assertThat(activity.byOperator()).hasSize(2);
        OperationsActivityResponse.OperatorActivity first = activity.byOperator().get(0);
        assertThat(first.operatorId()).isEqualTo(1001L);
        assertThat(first.operatorName()).isEqualTo("alice");
        assertThat(first.totalCount()).isEqualTo(40L);
        // 操作类型·笔数明细（枚举 code + 中文名）映射 + 顺序保留
        assertThat(first.operations()).hasSize(2);
        assertThat(first.operations().get(0).operation()).isEqualTo(1);
        assertThat(first.operations().get(0).operationName()).isEqualTo("审核通过");
        assertThat(first.operations().get(0).count()).isEqualTo(38L);
        assertThat(first.operations().get(1).operation()).isEqualTo(2);
        OperationsActivityResponse.OperatorActivity second = activity.byOperator().get(1);
        assertThat(second.operatorName()).isEqualTo("bob");
        assertThat(second.operations()).hasSize(1);
        assertThat(second.operations().get(0).operation()).isEqualTo(3);
        // 通知重发汇总：总数 + 按来源业务系统（含 null 系统组原样透传）
        assertThat(activity.notifyResend().totalCount()).isEqualTo(3L);
        assertThat(activity.notifyResend().byBusinessSystem()).hasSize(2);
        assertThat(activity.notifyResend().byBusinessSystem().get(0).businessSystem()).isEqualTo("course-svc");
        assertThat(activity.notifyResend().byBusinessSystem().get(1).businessSystem()).isNull();
    }

    @Test
    void given_operationsActivityDownstream500_when_getOperationsActivity_then_throwThirdPartyError() {
        when(paymentClient.getOperationsActivity(STATS_FROM, STATS_TO))
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> paymentAppService.getOperationsActivity(STATS_FROM, STATS_TO))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }
}
