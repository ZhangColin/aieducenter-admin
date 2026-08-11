package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import com.aieducenter.admin.payment.application.dto.wire.PaymentOrderWireResponse;
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
 * <p>200 用例 mock {@link PaymentClient}（返回空页），证明权限放行后整条 controller→appservice→client 通路接通。
 * 覆盖 401/403/200 三态（登录/鉴权辅助沿用 {@code RbacEnforcementIntegrationTest}）；超管 bypass 行为由框架级
 * {@code RbacEnforcementIntegrationTest} / {@code BreakGlassAccountProtectionIntegrationTest} 钉住，此处不重复。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentRbacEnforcementIntegrationTest {

    private static final String PASSWORD = "Test1234";
    private static final String PERMISSION_CODE = "admin:payment:read";
    private static final String ENDPOINT = "/api/admin/payment/payments";

    private final AdminUserManagementAppService userAppService;
    private final RoleManagementAppService roleAppService;
    private final TestRestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int port;

    @MockBean
    private PaymentClient paymentClient;

    private String usernameWithPermission;
    private String usernameWithoutPermission;

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

        Long roleId = roleAppService.create(
                new CreateRoleCommand("支付运营_" + suffix, "PAYOP_" + suffix, "仅有支付查看权限", 20, null));
        roleAppService.assignPermissions(roleId, new AssignPermissionsCommand(List.of(PERMISSION_CODE)));

        Long userWithPermissionId = userAppService.create(
                new CreateAdminUserCommand(usernameWithPermission, PASSWORD, "有权限运营", null, null, null));
        userAppService.assignRoles(userWithPermissionId, new AssignRolesCommand(List.of(roleId)));

        userAppService.create(
                new CreateAdminUserCommand(usernameWithoutPermission, PASSWORD, "无权限运营", null, null, null));

        // 200 用例：payment 下游 mock 为空页，证明通路接通（不依赖真实 payment 服务）
        when(paymentClient.listPayments(any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<PaymentOrderWireResponse>(List.of(), 0L, 0, 20));
    }

    @Test
    @DisplayName("非超管且拥有 admin:payment:read：访问支付订单列表返回 200")
    void given_nonSuperAdminWithPermission_when_listPayments_then_200() {
        String token = login(usernameWithPermission);
        ResponseEntity<String> response = getWithToken(ENDPOINT, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("非超管且缺少权限：访问支付订单列表返回 403")
    void given_nonSuperAdminWithoutPermission_when_listPayments_then_403() {
        String token = login(usernameWithoutPermission);
        ResponseEntity<String> response = getWithToken(ENDPOINT, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("未登录访问支付订单列表返回 401")
    void given_unauthenticated_when_listPayments_then_401() {
        ResponseEntity<String> response = getWithToken(ENDPOINT, null);
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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String uuidSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}