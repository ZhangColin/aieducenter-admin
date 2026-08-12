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
import com.aieducenter.admin.payment.application.dto.query.PaymentOrderQuery;
import com.aieducenter.admin.payment.application.dto.query.RefundOrderQuery;
import com.aieducenter.admin.payment.application.dto.response.GatewayHealthResponse;
import com.aieducenter.admin.payment.application.dto.response.OperationsAuditResponse;
import com.aieducenter.admin.payment.application.dto.response.OrderLifecycleResponse;
import com.aieducenter.admin.payment.application.dto.response.OrderStatusDistributionResponse;
import com.aieducenter.admin.payment.application.dto.response.PaymentOrderDetailResponse;
import com.aieducenter.admin.payment.application.dto.response.PaymentOverviewResponse;
import com.aieducenter.admin.payment.application.dto.response.PaymentOrderSummaryResponse;
import com.aieducenter.admin.payment.application.dto.response.RefundOrderDetailResponse;
import com.aieducenter.admin.payment.application.dto.response.RefundOrderSummaryResponse;
import com.aieducenter.admin.payment.application.dto.wire.AuditRefundWireRequest;
import com.aieducenter.admin.payment.application.dto.wire.GatewayHealthWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.OperationsAuditWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.OrderLifecycleWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.OrderStatusDistributionWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.PaymentOrderDetailWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.PaymentOrderListWireRequest;
import com.aieducenter.admin.payment.application.dto.wire.PaymentOrderWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.PaymentOverviewWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.RefundOrderDetailWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.RefundOrderListWireRequest;
import com.aieducenter.admin.payment.application.dto.wire.RefundOrderWireResponse;
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
}
