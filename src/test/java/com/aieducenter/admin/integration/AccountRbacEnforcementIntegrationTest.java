package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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

import com.aieducenter.admin.account.application.dto.wire.AccountReasonWireRequest;
import com.aieducenter.admin.account.application.dto.wire.AccountWireResponse;
import com.aieducenter.admin.account.infrastructure.AccountClient;
import com.aieducenter.admin.application.AdminUserManagementAppService;
import com.aieducenter.admin.application.RoleManagementAppService;
import com.aieducenter.admin.application.dto.command.AssignPermissionsCommand;
import com.aieducenter.admin.application.dto.command.AssignRolesCommand;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.application.dto.command.CreateRoleCommand;
import com.cartisan.core.context.RequestContext;
import com.cartisan.web.response.PageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;

/**
 * 账号管理端点 RBAC 强制执行集成测试——真实 Sa-Token 过滤链，断言各端点 {@code @RequirePermission}
 * 对未登录（401）/ 无权者（403）/ 有权者（200）的行为，并钉死操作者身份透传契约（issue #53 acceptance #3）。
 *
 * <p>覆盖端点：
 * <ul>
 *   <li>读（{@code admin:account:read}）：列表 {@code GET /accounts}、管理详情 {@code GET /accounts/{userId}/management}。</li>
 *   <li>写（{@code admin:account:write}）：封号 {@code POST /accounts/{userId}/disable}、解封 {@code /activate}、解锁 {@code /unlock}、强制下线 {@code /sessions/revoke}。</li>
 * </ul>
 *
 * <p>200 用例 mock {@link AccountClient}（空页 / 详情 / 写 no-op），证明权限放行后整条
 * controller→appservice→client 通路接通。登录/鉴权辅助沿用 {@code RbacEnforcementIntegrationTest}；
 * 超管 bypass 由框架级 {@code RbacEnforcementIntegrationTest} / {@code BreakGlassAccountProtectionIntegrationTest} 钉住，此处不重复。</p>
 *
 * <p><strong>操作者身份透传契约</strong>（acceptance #3）：identity 管理写端点从 {@code RequestContext} 读 operator 审计，
 * admin 不在 body 塞身份——框架 cartisan-openapi 自动从 {@code RequestContext} 带 {@code X-User-Id/X-User-Name} 出站 header。
 * 本测试在 mocked {@link AccountClient} 边界用 {@code doAnswer} 在调用瞬间抓取 {@link RequestContext#getUserId()} /
 * {@link RequestContext#getUserName()}，断言其 == 登录 operator 的 id / 昵称——这正是不经 body 时身份能抵达出站调用点
 * （供 OpenApiClient 续带 header）的端到端证据。SecurityFilter 从 session 注入 RequestContext 的链路由
 * {@code LoginWritesUserNameIntegrationTest} + payment 通知重发测试钉住。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountRbacEnforcementIntegrationTest {

    private static final String PASSWORD = "Test1234";
    private static final String READ_PERMISSION = "admin:account:read";
    private static final String WRITE_PERMISSION = "admin:account:write";
    private static final long TARGET_USER_ID = 1001L;
    private static final String LIST_ENDPOINT = "/api/admin/accounts";
    private static final String DETAIL_ENDPOINT = "/api/admin/accounts/" + TARGET_USER_ID + "/management";
    private static final String DISABLE_ENDPOINT = "/api/admin/accounts/" + TARGET_USER_ID + "/disable";
    private static final String ACTIVATE_ENDPOINT = "/api/admin/accounts/" + TARGET_USER_ID + "/activate";
    private static final String UNLOCK_ENDPOINT = "/api/admin/accounts/" + TARGET_USER_ID + "/unlock";
    private static final String REVOKE_ENDPOINT = "/api/admin/accounts/" + TARGET_USER_ID + "/sessions/revoke";

    private final AdminUserManagementAppService userAppService;
    private final RoleManagementAppService roleAppService;
    private final TestRestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int port;

    @MockBean
    private AccountClient accountClient;

    private String usernameWithReadPermission;
    private String usernameWithoutPermission;
    private String usernameWithWritePermission;
    private Long writeUserId;
    private String writeUserNickname;

    // 在 mocked client 调用瞬间抓取 RequestContext——证明 operator 身份抵达出站调用点（供 OpenApiClient 带 header）
    private final AtomicReference<Long> capturedOperatorId = new AtomicReference<>();
    private final AtomicReference<String> capturedOperatorName = new AtomicReference<>();

    @Autowired
    AccountRbacEnforcementIntegrationTest(
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
        capturedOperatorId.set(null);
        capturedOperatorName.set(null);

        String suffix = uuidSuffix();
        usernameWithReadPermission = "acctread" + suffix;
        usernameWithoutPermission = "acctnone" + suffix;
        usernameWithWritePermission = "acctwrite" + suffix;

        Long readRoleId = roleAppService.create(
                new CreateRoleCommand("账号查看_" + suffix, "ACCTREAD_" + suffix, "仅有账号查看权限", 60, null));
        roleAppService.assignPermissions(readRoleId, new AssignPermissionsCommand(List.of(READ_PERMISSION)));

        Long userWithReadId = userAppService.create(
                new CreateAdminUserCommand(usernameWithReadPermission, PASSWORD, "只读运营_" + suffix, null, null, null));
        userAppService.assignRoles(userWithReadId, new AssignRolesCommand(List.of(readRoleId)));

        userAppService.create(
                new CreateAdminUserCommand(usernameWithoutPermission, PASSWORD, "无权限运营_" + suffix, null, null, null));

        // 写操作专用运营（仅有 admin:account:write）——身份透传断言用其 id/昵称
        Long writeRoleId = roleAppService.create(
                new CreateRoleCommand("账号写操作_" + suffix, "ACCTWRITE_" + suffix, "仅有账号状态操作权限", 70, null));
        roleAppService.assignPermissions(writeRoleId, new AssignPermissionsCommand(List.of(WRITE_PERMISSION)));
        writeUserNickname = "写操作运营_" + suffix;
        writeUserId = userAppService.create(
                new CreateAdminUserCommand(usernameWithWritePermission, PASSWORD, writeUserNickname, null, null, null));
        userAppService.assignRoles(writeUserId, new AssignRolesCommand(List.of(writeRoleId)));

        // 200 用例：identity 下游 mock，证明通路接通（不依赖真实 identity 服务）
        when(accountClient.listAccounts(any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<AccountWireResponse>(List.of(), 0L, 0, 20));
        when(accountClient.getManagementDetail(eq(TARGET_USER_ID))).thenReturn(
                new AccountWireResponse(TARGET_USER_ID, "alice@example.com", "13800000001",
                        "爱丽丝", null, 1, false, true));
        // 写操作 mock：no-op + 抓取 RequestContext（身份透传契约证据）
        Runnable capture = () -> {
            capturedOperatorId.set(RequestContext.getUserId());
            capturedOperatorName.set(RequestContext.getUserName());
        };
        doAnswer(inv -> { capture.run(); return null; }).when(accountClient)
                .disable(eq(TARGET_USER_ID), any(AccountReasonWireRequest.class));
        doAnswer(inv -> { capture.run(); return null; }).when(accountClient)
                .activate(eq(TARGET_USER_ID), any(AccountReasonWireRequest.class));
        doAnswer(inv -> { capture.run(); return null; }).when(accountClient)
                .unlock(eq(TARGET_USER_ID), any(AccountReasonWireRequest.class));
        doAnswer(inv -> { capture.run(); return null; }).when(accountClient)
                .revokeSessions(eq(TARGET_USER_ID), any(AccountReasonWireRequest.class));
    }

    // ========== 列表（admin:account:read）· 权限三态 ==========

    @Test
    @DisplayName("非超管且拥有 admin:account:read → 列表 200")
    void given_nonSuperAdminWithRead_when_list_then_200() {
        String token = login(usernameWithReadPermission);
        ResponseEntity<String> response = getWithToken(LIST_ENDPOINT, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("非超管且缺少权限 → 列表 403")
    void given_nonSuperAdminWithoutPermission_when_list_then_403() {
        String token = login(usernameWithoutPermission);
        ResponseEntity<String> response = getWithToken(LIST_ENDPOINT, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("未登录访问账号列表返回 401")
    void given_unauthenticated_when_list_then_401() {
        ResponseEntity<String> response = getWithToken(LIST_ENDPOINT, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ========== 管理详情（admin:account:read）· 权限三态 ==========

    @Test
    @DisplayName("非超管且拥有 admin:account:read → 管理详情 200")
    void given_nonSuperAdminWithRead_when_getManagementDetail_then_200() {
        String token = login(usernameWithReadPermission);
        ResponseEntity<String> response = getWithToken(DETAIL_ENDPOINT, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(accountClient).getManagementDetail(TARGET_USER_ID);
    }

    @Test
    @DisplayName("非超管且缺少权限 → 管理详情 403")
    void given_nonSuperAdminWithoutPermission_when_getManagementDetail_then_403() {
        String token = login(usernameWithoutPermission);
        ResponseEntity<String> response = getWithToken(DETAIL_ENDPOINT, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("未登录访问管理详情返回 401")
    void given_unauthenticated_when_getManagementDetail_then_401() {
        ResponseEntity<String> response = getWithToken(DETAIL_ENDPOINT, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ========== 封号（admin:account:write）· 权限三态 + 操作者身份透传 ==========

    @Test
    @DisplayName("非超管且拥有 admin:account:write → 封号 200，且 RequestContext 透传登录 operator 身份")
    void given_nonSuperAdminWithWrite_when_disable_then_200_andRequestContextCarriesOperator() {
        String token = login(usernameWithWritePermission);
        ResponseEntity<String> response = postWithToken(
                DISABLE_ENDPOINT, token, "{\"reason\":\"违规刷单\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 操作者身份不经 body——经 RequestContext 抵达出站调用点（OpenApiClient 据此带 X-User-Id/X-User-Name）：
        // capturedOperatorId/Name == 登录写操作运营的 id/昵称
        assertThat(capturedOperatorId.get()).isEqualTo(writeUserId);
        assertThat(capturedOperatorName.get()).isEqualTo(writeUserNickname);
        // reason 原样透传到出站 wire（operator 不在 wire 里）
        verify(accountClient).disable(eq(TARGET_USER_ID), eq(new AccountReasonWireRequest("违规刷单")));
    }

    @Test
    @DisplayName("仅有 admin:account:read（无 write）→ 封号 403（read ≠ write）")
    void given_nonSuperAdminWithReadOnly_when_disable_then_403() {
        String token = login(usernameWithReadPermission);
        ResponseEntity<String> response = postWithToken(
                DISABLE_ENDPOINT, token, "{\"reason\":\"x\"}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("未登录封号返回 401")
    void given_unauthenticated_when_disable_then_401() {
        ResponseEntity<String> response = postWithToken(
                DISABLE_ENDPOINT, null, "{\"reason\":\"x\"}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("封号 reason 缺失/空白 → 400（@NotBlank 校验，写权限放行后由 validation 拦）")
    void given_writePermissionButBlankReason_when_disable_then_400() {
        String token = login(usernameWithWritePermission);
        // 空白 reason
        ResponseEntity<String> blank = postWithToken(DISABLE_ENDPOINT, token, "{\"reason\":\"\"}");
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // 缺省 reason
        ResponseEntity<String> missing = postWithToken(DISABLE_ENDPOINT, token, "{}");
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("activate/unlock/revoke reason 超长（>500）→ 400（@Valid 触发 @Size(max=500)，此前缺 @Valid 是死代码）")
    void given_writePermissionButOversizedReason_when_activateUnlockRevoke_then_400() {
        String token = login(usernameWithWritePermission);
        String tooLong = "{\"reason\":\"" + "x".repeat(501) + "\"}";
        // activate / unlock / revoke 均挂 @Valid → @Size(max=500) 生效；缺 @Valid 时会放行（200），此处钉死校验生效
        ResponseEntity<String> activateResp = postWithToken(ACTIVATE_ENDPOINT, token, tooLong);
        assertThat(activateResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<String> unlockResp = postWithToken(UNLOCK_ENDPOINT, token, tooLong);
        assertThat(unlockResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<String> revokeResp = postWithToken(REVOKE_ENDPOINT, token, tooLong);
        assertThat(revokeResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ========== 解封（admin:account:write）· 权限三态 + 操作者身份透传 ==========

    @Test
    @DisplayName("非超管且拥有 admin:account:write → 解封 200，且 RequestContext 透传登录 operator 身份")
    void given_nonSuperAdminWithWrite_when_activate_then_200_andRequestContextCarriesOperator() {
        String token = login(usernameWithWritePermission);
        ResponseEntity<String> response = postWithToken(
                ACTIVATE_ENDPOINT, token, "{\"reason\":\"申诉成功\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(capturedOperatorId.get()).isEqualTo(writeUserId);
        assertThat(capturedOperatorName.get()).isEqualTo(writeUserNickname);
        verify(accountClient).activate(eq(TARGET_USER_ID), eq(new AccountReasonWireRequest("申诉成功")));
    }

    @Test
    @DisplayName("仅有 admin:account:read（无 write）→ 解封 403")
    void given_nonSuperAdminWithReadOnly_when_activate_then_403() {
        String token = login(usernameWithReadPermission);
        ResponseEntity<String> response = postWithToken(
                ACTIVATE_ENDPOINT, token, "{\"reason\":\"x\"}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("未登录解封返回 401")
    void given_unauthenticated_when_activate_then_401() {
        ResponseEntity<String> response = postWithToken(
                ACTIVATE_ENDPOINT, null, "{\"reason\":\"x\"}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ========== 解锁（admin:account:write）· 权限三态 + 操作者身份透传 ==========

    @Test
    @DisplayName("非超管且拥有 admin:account:write → 解锁 200，且 RequestContext 透传登录 operator 身份")
    void given_nonSuperAdminWithWrite_when_unlock_then_200_andRequestContextCarriesOperator() {
        String token = login(usernameWithWritePermission);
        ResponseEntity<String> response = postWithToken(
                UNLOCK_ENDPOINT, token, "{\"reason\":\"风控误判\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(capturedOperatorId.get()).isEqualTo(writeUserId);
        assertThat(capturedOperatorName.get()).isEqualTo(writeUserNickname);
        verify(accountClient).unlock(eq(TARGET_USER_ID), eq(new AccountReasonWireRequest("风控误判")));
    }

    @Test
    @DisplayName("仅有 admin:account:read（无 write）→ 解锁 403")
    void given_nonSuperAdminWithReadOnly_when_unlock_then_403() {
        String token = login(usernameWithReadPermission);
        ResponseEntity<String> response = postWithToken(
                UNLOCK_ENDPOINT, token, "{\"reason\":\"x\"}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("未登录解锁返回 401")
    void given_unauthenticated_when_unlock_then_401() {
        ResponseEntity<String> response = postWithToken(
                UNLOCK_ENDPOINT, null, "{\"reason\":\"x\"}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ========== 强制下线（admin:account:write）· 权限三态 + 操作者身份透传 ==========

    @Test
    @DisplayName("非超管且拥有 admin:account:write → 强制下线 200，且 RequestContext 透传登录 operator 身份")
    void given_nonSuperAdminWithWrite_when_revokeSessions_then_200_andRequestContextCarriesOperator() {
        String token = login(usernameWithWritePermission);
        ResponseEntity<String> response = postWithToken(
                REVOKE_ENDPOINT, token, "{\"reason\":\"排查异常登录\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 操作者身份不经 body——经 RequestContext 抵达出站调用点（OpenApiClient 据此带 X-User-Id/X-User-Name）
        assertThat(capturedOperatorId.get()).isEqualTo(writeUserId);
        assertThat(capturedOperatorName.get()).isEqualTo(writeUserNickname);
        // reason 原样透传到出站 wire（operator 不在 wire 里）；revoke 不触发任何状态变更调用（不改状态）
        verify(accountClient).revokeSessions(eq(TARGET_USER_ID), eq(new AccountReasonWireRequest("排查异常登录")));
    }

    @Test
    @DisplayName("仅有 admin:account:read（无 write）→ 强制下线 403")
    void given_nonSuperAdminWithReadOnly_when_revokeSessions_then_403() {
        String token = login(usernameWithReadPermission);
        ResponseEntity<String> response = postWithToken(
                REVOKE_ENDPOINT, token, "{\"reason\":\"x\"}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("未登录强制下线返回 401")
    void given_unauthenticated_when_revokeSessions_then_401() {
        ResponseEntity<String> response = postWithToken(
                REVOKE_ENDPOINT, null, "{\"reason\":\"x\"}");
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
        return requestWithToken(path, token, null, HttpMethod.GET);
    }

    private ResponseEntity<String> postWithToken(String path, String token, String body) {
        return requestWithToken(path, token, body, HttpMethod.POST);
    }

    private ResponseEntity<String> requestWithToken(String path, String token, String body, HttpMethod method) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            SaTokenConfig cfg = SaManager.getConfig();
            String value = (cfg.getTokenPrefix() == null || cfg.getTokenPrefix().isEmpty())
                    ? token
                    : cfg.getTokenPrefix() + " " + token;
            headers.set(cfg.getTokenName(), value);
        }
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String uuidSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
