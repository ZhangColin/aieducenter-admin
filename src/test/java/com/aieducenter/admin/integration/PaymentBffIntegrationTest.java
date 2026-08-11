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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;

import com.aieducenter.admin.payment.application.PaymentManagementAppService;
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
}