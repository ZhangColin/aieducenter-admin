package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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

import com.aieducenter.admin.aiplatform.application.AiplatformUpstreamException;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformAccountProfileWireResponse;
import com.aieducenter.admin.aiplatform.infrastructure.AiplatformClient;
import com.aieducenter.admin.application.AdminUserManagementAppService;
import com.aieducenter.admin.application.RoleManagementAppService;
import com.aieducenter.admin.application.dto.command.AssignPermissionsCommand;
import com.aieducenter.admin.application.dto.command.AssignRolesCommand;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.application.dto.command.CreateRoleCommand;
import com.cartisan.openapi.client.OpenApiClientException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;

/**
 * aiplatform BFF 端点 RBAC 强制执行集成测试（issue #63 T1 首批用例）——真实 Sa-Token 过滤链，
 * 断言 {@code admin:aiplatform:account:read} 独立权限码对未登录（401）/ 无权者（403）/ 有权者（200）
 * 的行为，并钉死北向出口形状与 provider 错误信封透传。
 *
 * <p>镜像 {@code AccountRbacEnforcementIntegrationTest}（登录/鉴权辅助沿用
 * {@code RbacEnforcementIntegrationTest}）。200 用例 mock {@link AiplatformClient}，证明权限放行后
 * 整条 controller→appservice→client 通路接通。超管 bypass 由框架级测试钉住，此处不重复。</p>
 *
 * <p><strong>错误信封透传（spec #62 定稿）</strong>：下游 IDN_004（HTTP 404 + 数字业务码 6004）经
 * {@link AiplatformUpstreamException} 抵达 {@code AiplatformUpstreamErrorAdvice}，北向还原 provider
 * 形状——HTTP 404 + 信封 {@code code}=6004 + {@code message} 原文，<strong>不</strong>翻译成
 * {@code BaseCodeMessage.NOT_FOUND}（404/404）。这是该 advice 在真实 MVC 层的端到端证据
 * （{@code AiplatformBffIntegrationTest} 为 MOCK 环境，不经 HTTP）。</p>
 *
 * @since 0.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AiplatformRbacEnforcementIntegrationTest {

    private static final String PASSWORD = "Test1234";
    private static final String READ_PERMISSION = "admin:aiplatform:account:read";
    private static final String EXTERNAL_ID = "auth0|65f2c8a1b3d4e5f6a7b8c9d0";
    private static final String PROFILE_ENDPOINT = "/api/admin/aiplatform/accounts/" + EXTERNAL_ID;

    private final AdminUserManagementAppService userAppService;
    private final RoleManagementAppService roleAppService;
    private final TestRestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int port;

    @MockBean
    private AiplatformClient aiplatformClient;

    private String usernameWithReadPermission;
    private String usernameWithoutPermission;

    @Autowired
    AiplatformRbacEnforcementIntegrationTest(
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
        usernameWithReadPermission = "aiplaread" + suffix;
        usernameWithoutPermission = "aiplanone" + suffix;

        Long readRoleId = roleAppService.create(
                new CreateRoleCommand("AI平台账号查看_" + suffix, "AIPLAREAD_" + suffix,
                        "仅有 AI 平台账号档案查看权限", 80, null));
        roleAppService.assignPermissions(readRoleId, new AssignPermissionsCommand(List.of(READ_PERMISSION)));

        Long userWithReadId = userAppService.create(
                new CreateAdminUserCommand(usernameWithReadPermission, PASSWORD, "只读运营_" + suffix, null, null, null));
        userAppService.assignRoles(userWithReadId, new AssignRolesCommand(List.of(readRoleId)));

        userAppService.create(
                new CreateAdminUserCommand(usernameWithoutPermission, PASSWORD, "无权限运营_" + suffix, null, null, null));

        // 200 用例：aiplatform 下游 mock，证明通路接通（不依赖真实 aiplatform 服务）
        when(aiplatformClient.getAccountProfile(eq(EXTERNAL_ID))).thenReturn(
                new AiplatformAccountProfileWireResponse(
                        "3829492001234567", EXTERNAL_ID, "文野",
                        LocalDateTime.of(2026, 1, 15, 10, 30, 0)));
    }

    // ========== 账号档案（admin:aiplatform:account:read）· 权限三态 + 北向出口形状 ==========

    @Test
    @DisplayName("非超管且拥有 admin:aiplatform:account:read → 档案 200，北向 data 逐字镜像 provider")
    void given_nonSuperAdminWithRead_when_getProfile_then_200AndDataMirrorsProvider() throws Exception {
        String token = login(usernameWithReadPermission);
        ResponseEntity<String> response = getWithToken(PROFILE_ENDPOINT, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 北向出口形状：admin ApiResponse 信封 + data 四字段逐字镜像（id 为 String、createdAt ISO）
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertThat(data.path("id").asText()).isEqualTo("3829492001234567");
        assertThat(data.path("externalId").asText()).isEqualTo(EXTERNAL_ID);
        assertThat(data.path("displayName").asText()).isEqualTo("文野");
        assertThat(data.path("createdAt").asText()).isEqualTo("2026-01-15T10:30:00");
    }

    @Test
    @DisplayName("非超管且缺少权限 → 档案 403（独立权限码 account:read，2026-09-15 拍板）")
    void given_nonSuperAdminWithoutPermission_when_getProfile_then_403() {
        String token = login(usernameWithoutPermission);
        ResponseEntity<String> response = getWithToken(PROFILE_ENDPOINT, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("未登录访问账号档案返回 401")
    void given_unauthenticated_when_getProfile_then_401() {
        ResponseEntity<String> response = getWithToken(PROFILE_ENDPOINT, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ========== 错误信封透传（忠实透传，不做映射） ==========

    @Test
    @DisplayName("下游 IDN_004 → 北向 HTTP 404 + 信封 code=6004 + message 原文（不映射为 BaseCodeMessage）")
    void given_idn004FromDownstream_when_getProfile_then_errorEnvelopePassedThrough() throws Exception {
        // 错误信封翻译已归 AiplatformClient（wire 契约测试钉死）；此处经真实 from() 翻译路径构造
        // 透传异常，验证北向 advice 在真实 MVC 层还原 provider 形状
        when(aiplatformClient.getAccountProfile(eq("auth0|unknown"))).thenThrow(
                AiplatformUpstreamException.from(
                        new OpenApiClientException(404, "{\"code\":6004,\"message\":\"账号不存在\",\"data\":null}")));

        String token = login(usernameWithReadPermission);
        ResponseEntity<String> response = getWithToken(
                "/api/admin/aiplatform/accounts/auth0|unknown", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode root = objectMapper.readTree(response.getBody());
        // 信封 code＝数字业务码 6004（IDN_004），而非映射后的 404——前端比对 aiplatform 业务码的分支活
        assertThat(root.path("code").asInt()).isEqualTo(6004);
        assertThat(root.path("message").asText()).isEqualTo("账号不存在");
        assertThat(root.path("data").isNull()).isTrue();
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
