package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import com.aieducenter.admin.application.AdminUserManagementAppService;
import com.aieducenter.admin.application.RoleManagementAppService;
import com.aieducenter.admin.application.dto.command.AssignRolesCommand;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.application.dto.command.CreateRoleCommand;
import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.aieducenter.admin.domain.repository.AdminRoleRepository;
import com.aieducenter.admin.domain.repository.AdminUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;

/**
 * 破窗账号保护集成测试（Phase 0 Bug ③）。
 *
 * <p>以 HTTP 外部行为（状态码 + 错误体 message）断言内置破窗号 {@code admin}(id=1) 的韧性守卫：</p>
 * <ul>
 *   <li>删除破窗号 → 403（命中 {@code BREAK_GLASS_CANNOT_DELETE}）</li>
 *   <li>禁用破窗号 → 403（{@code BREAK_GLASS_CANNOT_DISABLE}）</li>
 *   <li>从破窗号移除 SUPER_ADMIN 角色 → 403（{@code BREAK_GLASS_SUPER_ADMIN_REQUIRED}）</li>
 *   <li>建/删普通管理员 → 成功（守卫不误伤普通账号）</li>
 *   <li>{@code AdminUserResponse.breakGlass} 派生字段：破窗号=true、普通账号=false</li>
 * </ul>
 *
 * <p>守卫点在 {@link AdminUser#markAsDeleted()} / {@link AdminUser#disable()}（聚合内，单一执行点），
 * 应用服务仅加载并委托；本类走真库 + 真 Spring + SecurityFilter + 全局异常处理，验证端到端外部行为。</p>
 *
 * <p>破窗号用保留 ID = 1（与 V2 种子一致）；测试库 {@code ddl-auto=create-drop}、Flyway 关闭，
 * 故 id=1 由 {@code @BeforeEach} 直接以反射设 id 后经仓储落库（模拟种子保留段）。所有破窗号操作均被拒
 * （无状态变更），故 id=1 在类内复用；用 {@code @DirtiesContext(AFTER_CLASS)} 类末重建上下文。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BreakGlassAccountProtectionIntegrationTest {

    private static final String PASSWORD = "Test1234";
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(10);

    private final AdminUserManagementAppService userAppService;
    private final RoleManagementAppService roleAppService;
    private final AdminUserRepository adminUserRepository;
    private final AdminRoleRepository adminRoleRepository;
    private final TestRestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int port;

    private String callerUsername;
    private Long superAdminRoleId;

    @Autowired
    BreakGlassAccountProtectionIntegrationTest(
            AdminUserManagementAppService userAppService,
            RoleManagementAppService roleAppService,
            AdminUserRepository adminUserRepository,
            AdminRoleRepository adminRoleRepository,
            TestRestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${local.server.port}") int port) {
        this.userAppService = userAppService;
        this.roleAppService = roleAppService;
        this.adminUserRepository = adminUserRepository;
        this.adminRoleRepository = adminRoleRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.port = port;
    }

    @BeforeEach
    void setUp() throws Exception {
        // SUPER_ADMIN 角色（按 code 幂等；caller 与破窗号的授权都走它）
        superAdminRoleId = adminRoleRepository.findByCode(AdminRole.SUPER_ADMIN_CODE)
                .map(AdminRole::getId)
                .orElseGet(() -> roleAppService.create(
                        new CreateRoleCommand("超级管理员", AdminRole.SUPER_ADMIN_CODE, "超级管理员", 0)));

        // 破窗号 admin（保留 ID = 1，按 id 幂等——所有破窗号操作均被拒，不会变更/删除它）
        if (adminUserRepository.findById(AdminUser.BREAK_GLASS_ADMIN_ID).isEmpty()) {
            AdminUser breakGlass = new AdminUser(
                    "breakglass", ENCODER.encode(PASSWORD), "破窗号");
            setId(breakGlass, AdminUser.BREAK_GLASS_ADMIN_ID);
            adminUserRepository.save(breakGlass);
        }

        // 调用者：超管（bypass 权限检查，以便调用 admin:user:write 端点操作破窗号）
        callerUsername = "bgcaller";
        if (!adminUserRepository.existsByUsername(callerUsername)) {
            Long callerId = userAppService.create(
                    new CreateAdminUserCommand(callerUsername, PASSWORD, "调用者", null, null));
            userAppService.assignRoles(callerId, new AssignRolesCommand(List.of(superAdminRoleId)));
        }
    }

    @Test
    @DisplayName("删除破窗号 admin(id=1) → 403，错误体命中「内置账号不可删除」")
    void given_breakGlassAdmin_when_delete_then_403() {
        String token = login(callerUsername);

        ResponseEntity<String> response = withToken(HttpMethod.DELETE, "/api/admin/users/" + AdminUser.BREAK_GLASS_ADMIN_ID, token, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(messageOf(response)).contains("内置账号不可删除");
    }

    @Test
    @DisplayName("禁用破窗号 admin(id=1) → 403，错误体命中「内置账号不可禁用」")
    void given_breakGlassAdmin_when_disable_then_403() {
        String token = login(callerUsername);

        ResponseEntity<String> response = withToken(HttpMethod.PUT,
                "/api/admin/users/" + AdminUser.BREAK_GLASS_ADMIN_ID + "/status?status=0", token, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(messageOf(response)).contains("内置账号不可禁用");
    }

    @Test
    @DisplayName("从破窗号移除 SUPER_ADMIN 角色 → 403，错误体命中「内置账号必须保留超级管理员角色」")
    void given_breakGlassAdmin_when_assignRolesWithoutSuperAdmin_then_403() {
        Long operatorRoleId = roleAppService.create(
                new CreateRoleCommand("运营_" + uuidSuffix(), "OPERATOR_" + uuidSuffix(), "运营", 10));
        String token = login(callerUsername);

        ResponseEntity<String> response = withToken(HttpMethod.PUT,
                "/api/admin/users/" + AdminUser.BREAK_GLASS_ADMIN_ID + "/roles",
                token, "{\"roleIds\":[" + operatorRoleId + "]}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(messageOf(response)).contains("内置账号必须保留超级管理员角色");
    }

    @Test
    @DisplayName("建/删普通管理员 → 成功（守卫不误伤普通账号）")
    void given_normalAdmin_when_createAndDelete_then_success() throws Exception {
        String token = login(callerUsername);
        String username = "normal" + uuidSuffix();

        ResponseEntity<String> created = withToken(HttpMethod.POST, "/api/admin/users", token,
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\","
                        + "\"nickname\":\"普通运营\",\"email\":null,\"phone\":null}");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long createdId = objectMapper.readTree(created.getBody()).path("data").asLong();
        assertThat(createdId).isPositive();

        ResponseEntity<String> deleted = withToken(HttpMethod.DELETE, "/api/admin/users/" + createdId, token, null);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("AdminUserResponse.breakGlass：破窗号=true、普通账号=false")
    void given_breakGlassAndNormal_when_getUser_then_breakGlassFlagCorrect() throws Exception {
        String token = login(callerUsername);

        ResponseEntity<String> breakGlassResp = withToken(HttpMethod.GET,
                "/api/admin/users/" + AdminUser.BREAK_GLASS_ADMIN_ID, token, null);
        assertThat(breakGlassResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(breakGlassFlag(breakGlassResp)).isTrue();

        String username = "flagcheck" + uuidSuffix();
        Long normalId = userAppService.create(
                new CreateAdminUserCommand(username, PASSWORD, "普通运营", null, null));
        ResponseEntity<String> normalResp = withToken(HttpMethod.GET, "/api/admin/users/" + normalId, token, null);
        assertThat(normalResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(breakGlassFlag(normalResp)).isFalse();
    }

    // ========== helpers ==========

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

    private ResponseEntity<String> withToken(HttpMethod method, String path, String token, String body) {
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

    private String messageOf(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody()).path("message").asText();
        } catch (Exception e) {
            throw new AssertionError("解析响应 message 失败：" + response.getBody(), e);
        }
    }

    private boolean breakGlassFlag(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody()).path("data").path("breakGlass").asBoolean();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String uuidSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static void setId(Object aggregate, long id) throws Exception {
        java.lang.reflect.Field idField = aggregate.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(aggregate, id);
    }
}
