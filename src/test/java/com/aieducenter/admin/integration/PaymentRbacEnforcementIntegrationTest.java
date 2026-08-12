package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import com.aieducenter.admin.application.AdminUserManagementAppService;
import com.aieducenter.admin.application.RoleManagementAppService;
import com.aieducenter.admin.application.dto.command.AssignPermissionsCommand;
import com.aieducenter.admin.application.dto.command.AssignRolesCommand;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.application.dto.command.CreateRoleCommand;
import com.aieducenter.admin.payment.application.dto.wire.AuditRefundWireRequest;
import com.aieducenter.admin.payment.application.dto.wire.OrderLifecycleWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.OperationLogWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.PaymentLogWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.PaymentOrderDetailWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.PaymentOrderWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.RefundOrderDetailWireResponse;
import com.aieducenter.admin.payment.application.dto.wire.RefundOrderWireResponse;
import com.aieducenter.admin.payment.infrastructure.PaymentClient;
import com.cartisan.web.response.PageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;

/**
 * 支付管理端点 RBAC 强制执行集成测试——真实 Sa-Token 过滤链，断言
 * {@code @RequirePermission("admin:payment:read")} 对未登录（401）/ 无权者（403）/ 有权者（200）的行为。
 *
 * <p>对挂 {@code admin:payment:read} 的全部列表端点（{@code /payments}、{@code /refunds}、
 * {@code /payment-logs}、{@code /operation-logs}）逐一验证三态。
 * 200 用例 mock {@link PaymentClient}（返回空页），证明权限放行后整条 controller→appservice→client 通路接通。
 * 登录/鉴权辅助沿用 {@code RbacEnforcementIntegrationTest}；超管 bypass 行为由框架级
 * {@code RbacEnforcementIntegrationTest} / {@code BreakGlassAccountProtectionIntegrationTest} 钉住，此处不重复。</p>
 *
 * <p>退款审核写端点（{@code POST /refunds/{no}/audit}、挂 {@code admin:payment:refund:audit}）在此一并覆盖三态，
 * 并在 200 用例用 {@link ArgumentCaptor} 抓取出站 {@link AuditRefundWireRequest}，断言
 * {@code auditorId}/{@code auditorName} == 登录用户的 id/昵称——这是「操作者身份从 RequestContext 透传到
 * payment 请求体（零 Sa-Token/零 DB/零新注解）」契约的端到端证据（SecurityFilter 从 session 注入 RequestContext）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentRbacEnforcementIntegrationTest {

    private static final String PASSWORD = "Test1234";
    private static final String PERMISSION_CODE = "admin:payment:read";
    private static final String AUDIT_PERMISSION_CODE = "admin:payment:refund:audit";
    private static final String BANK_QUERY_PERMISSION_CODE = "admin:payment:bank:query";

    /** 挂 {@code admin:payment:read} 的全部端点（列表 + 详情 + 生命周期 + 日志）——逐一验证三态。 */
    private static final List<String> ENDPOINTS = List.of(
            "/api/admin/payment/payments",
            "/api/admin/payment/refunds",
            "/api/admin/payment/payment-logs",
            "/api/admin/payment/operation-logs",
            "/api/admin/payment/payments/PAY-1",
            "/api/admin/payment/refunds/RF-1",
            "/api/admin/payment/orders/PAY-1/lifecycle");

    static Stream<String> endpoints() {
        return ENDPOINTS.stream();
    }

    private final AdminUserManagementAppService userAppService;
    private final RoleManagementAppService roleAppService;
    private final TestRestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int port;

    @MockBean
    private PaymentClient paymentClient;

    private String usernameWithPermission;
    private String usernameWithoutPermission;
    private String usernameWithAuditPermission;
    private String usernameWithBankQueryPermission;
    private Long auditUserId;
    private String auditUserNickname;

    @Autowired
    PaymentRbacEnforcementIntegrationTest(
            AdminUserManagementAppService userAppService,
            RoleManagementAppService roleAppService,
            TestRestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${local.server.port}") int port) {
        this.userAppService = userAppService;
        this.roleAppService = roleAppService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.port = port;
    }

    @BeforeEach
    void setUp() {
        String suffix = uuidSuffix();
        usernameWithPermission = "payop" + suffix;
        usernameWithoutPermission = "paynone" + suffix;
        usernameWithAuditPermission = "payauditor" + suffix;
        usernameWithBankQueryPermission = "paybankq" + suffix;

        Long roleId = roleAppService.create(
                new CreateRoleCommand("支付运营_" + suffix, "PAYOP_" + suffix, "仅有支付查看权限", 20, null));
        roleAppService.assignPermissions(roleId, new AssignPermissionsCommand(List.of(PERMISSION_CODE)));

        Long userWithPermissionId = userAppService.create(
                new CreateAdminUserCommand(usernameWithPermission, PASSWORD, "有权限运营", null, null, null));
        userAppService.assignRoles(userWithPermissionId, new AssignRolesCommand(List.of(roleId)));

        userAppService.create(
                new CreateAdminUserCommand(usernameWithoutPermission, PASSWORD, "无权限运营", null, null, null));

        // 退款审核专用运营（仅有 admin:payment:refund:audit）——身份透传 200 用例用其 id/昵称做断言
        Long auditRoleId = roleAppService.create(
                new CreateRoleCommand("退款审核_" + suffix, "PAYAUDIT_" + suffix, "仅有退款审核权限", 30, null));
        roleAppService.assignPermissions(auditRoleId, new AssignPermissionsCommand(List.of(AUDIT_PERMISSION_CODE)));
        auditUserNickname = "退款审核员_" + suffix;
        auditUserId = userAppService.create(
                new CreateAdminUserCommand(usernameWithAuditPermission, PASSWORD, auditUserNickname, null, null, null));
        userAppService.assignRoles(auditUserId, new AssignRolesCommand(List.of(auditRoleId)));

        // 主动查行专用运营（仅有 admin:payment:bank:query）——证明该写操作按独立权限码放行
        Long bankQueryRoleId = roleAppService.create(
                new CreateRoleCommand("主动查行_" + suffix, "PAYBANKQ_" + suffix, "仅有主动查行权限", 40, null));
        roleAppService.assignPermissions(bankQueryRoleId, new AssignPermissionsCommand(List.of(BANK_QUERY_PERMISSION_CODE)));
        Long bankQueryUserId = userAppService.create(
                new CreateAdminUserCommand(usernameWithBankQueryPermission, PASSWORD, "查行运营_" + suffix, null, null, null));
        userAppService.assignRoles(bankQueryUserId, new AssignRolesCommand(List.of(bankQueryRoleId)));

        // 200 用例：payment 下游 mock 为空页/空时间线，证明通路接通（不依赖真实 payment 服务）
        when(paymentClient.listPayments(any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<PaymentOrderWireResponse>(List.of(), 0L, 0, 20));
        when(paymentClient.listRefunds(any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<RefundOrderWireResponse>(List.of(), 0L, 0, 20));
        when(paymentClient.listPaymentLogs(any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<PaymentLogWireResponse>(List.of(), 0L, 0, 20));
        when(paymentClient.listOperationLogs(any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<OperationLogWireResponse>(List.of(), 0L, 0, 20));
        when(paymentClient.getPayment("PAY-1")).thenReturn(
                new PaymentOrderDetailWireResponse("PAY-1", null, null, "PAID",
                        null, null, null, null, null, null));
        when(paymentClient.getRefund("RF-1")).thenReturn(
                new RefundOrderDetailWireResponse("RF-1", null, null, null, "PENDING",
                        null, null, null, null, null, null));
        when(paymentClient.getLifecycle("PAY-1")).thenReturn(
                new OrderLifecycleWireResponse("PAY-1", List.of()));
        // 退款审核 200 用例：mock 返回审核后退款单，证明写通路接通；出站 wire 请求体由 ArgumentCaptor 抓取
        when(paymentClient.auditRefund(eq("RF-1"), any(AuditRefundWireRequest.class))).thenReturn(
                new RefundOrderDetailWireResponse("RF-1", null, null, null, "APPROVED",
                        null, "MANUAL", null, null, null, null));
        // 主动查行 200 用例：mock 返回查询后支付单（与详情同形），证明写通路接通
        when(paymentClient.queryPayment("PAY-1")).thenReturn(
                new PaymentOrderDetailWireResponse("PAY-1", null, null, "PAID",
                        null, null, null, null, null, null));
    }

    @ParameterizedTest(name = "[{0}] 非超管且拥有 admin:payment:read → 200")
    @MethodSource("endpoints")
    @DisplayName("非超管且拥有 admin:payment:read：访问支付管理列表端点返回 200")
    void given_nonSuperAdminWithPermission_when_list_then_200(String endpoint) {
        String token = login(usernameWithPermission);
        ResponseEntity<String> response = getWithToken(endpoint, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @ParameterizedTest(name = "[{0}] 非超管且缺少权限 → 403")
    @MethodSource("endpoints")
    @DisplayName("非超管且缺少权限：访问支付管理列表端点返回 403")
    void given_nonSuperAdminWithoutPermission_when_list_then_403(String endpoint) {
        String token = login(usernameWithoutPermission);
        ResponseEntity<String> response = getWithToken(endpoint, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest(name = "[{0}] 未登录 → 401")
    @MethodSource("endpoints")
    @DisplayName("未登录访问支付管理列表端点返回 401")
    void given_unauthenticated_when_list_then_401(String endpoint) {
        ResponseEntity<String> response = getWithToken(endpoint, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ========== 退款审核（POST /refunds/{no}/audit）· 权限三态 + 操作者身份透传 ==========

    @Test
    @DisplayName("非超管且拥有 admin:payment:refund:audit：审核退款返回 200，且出站请求体含登录用户身份")
    void given_nonSuperAdminWithAuditPermission_when_audit_then_200_andWireRequestCarriesRequestContextIdentity() {
        String token = login(usernameWithAuditPermission);
        ResponseEntity<String> response = postWithToken(
                "/api/admin/payment/refunds/RF-1/audit", token,
                "{\"agreed\":true,\"remark\":\"同意退款\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 操作者身份从 RequestContext 透传到 payment 请求体（零 Sa-Token/零 DB/零新注解）：
        // auditorId == 登录用户 id，auditorName == 登录用户昵称
        // （HTTP 登录 → 框架写 session.userName → SecurityFilter 注入 RequestContext → controller → 出站请求体）。
        ArgumentCaptor<AuditRefundWireRequest> captor = ArgumentCaptor.forClass(AuditRefundWireRequest.class);
        verify(paymentClient).auditRefund(eq("RF-1"), captor.capture());
        AuditRefundWireRequest wire = captor.getValue();
        assertThat(wire.auditorId()).isEqualTo(auditUserId);
        assertThat(wire.auditorName()).isEqualTo(auditUserNickname);
        assertThat(wire.agreed()).isTrue();
        assertThat(wire.remark()).isEqualTo("同意退款");
    }

    @Test
    @DisplayName("仅有 admin:payment:read（无 refund:audit）→ 审核退款 403（read ≠ audit）")
    void given_nonSuperAdminWithReadOnly_when_audit_then_403() {
        String token = login(usernameWithPermission);
        ResponseEntity<String> response = postWithToken(
                "/api/admin/payment/refunds/RF-1/audit", token,
                "{\"agreed\":true,\"remark\":\"同意\"}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("未登录审核退款返回 401")
    void given_unauthenticated_when_audit_then_401() {
        ResponseEntity<String> response = postWithToken(
                "/api/admin/payment/refunds/RF-1/audit", null,
                "{\"agreed\":true,\"remark\":\"同意\"}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ========== 主动查行（POST /payments/{no}/query）· 权限三态 ==========

    @Test
    @DisplayName("非超管且拥有 admin:payment:bank:query：主动查行返回 200，且透传 paymentOrderNo")
    void given_nonSuperAdminWithBankQueryPermission_when_query_then_200_andPassesPaymentOrderNo() {
        String token = login(usernameWithBankQueryPermission);
        ResponseEntity<String> response = postWithToken(
                "/api/admin/payment/payments/PAY-1/query", token, "");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 无请求体、无身份透传：仅验证 paymentOrderNo 原样透传给下游
        verify(paymentClient).queryPayment("PAY-1");
    }

    @Test
    @DisplayName("仅有 admin:payment:read（无 bank:query）→ 主动查行 403（read ≠ bank:query）")
    void given_nonSuperAdminWithReadOnly_when_query_then_403() {
        String token = login(usernameWithPermission);
        ResponseEntity<String> response = postWithToken(
                "/api/admin/payment/payments/PAY-1/query", token, "");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("未登录主动查行返回 401")
    void given_unauthenticated_when_query_then_401() {
        ResponseEntity<String> response = postWithToken(
                "/api/admin/payment/payments/PAY-1/query", null, "");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String login(String username) {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"rememberMe\":false}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/admin/auth/login"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            String token = root.path("data").path("token").asText();
            assertThat(token).as("登录应返回 token：%s", response.getBody()).isNotBlank();
            return token;
        } catch (Exception e) {
            throw new AssertionError("解析登录响应失败：" + response.getBody(), e);
        }
    }

    private ResponseEntity<String> getWithToken(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            SaTokenConfig cfg = SaManager.getConfig();
            String value = (cfg.getTokenPrefix() == null || cfg.getTokenPrefix().isEmpty())
                    ? token
                    : cfg.getTokenPrefix() + " " + token;
            headers.set(cfg.getTokenName(), value);
        }
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> postWithToken(String path, String token, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            SaTokenConfig cfg = SaManager.getConfig();
            String value = (cfg.getTokenPrefix() == null || cfg.getTokenPrefix().isEmpty())
                    ? token
                    : cfg.getTokenPrefix() + " " + token;
            headers.set(cfg.getTokenName(), value);
        }
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String uuidSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
