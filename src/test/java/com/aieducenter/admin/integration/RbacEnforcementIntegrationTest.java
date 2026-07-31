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
import org.springframework.test.annotation.DirtiesContext;

import com.aieducenter.admin.application.AdminUserManagementAppService;
import com.aieducenter.admin.application.RoleManagementAppService;
import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.application.dto.command.AssignPermissionsCommand;
import com.aieducenter.admin.application.dto.command.AssignRolesCommand;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.application.dto.command.CreateRoleCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;

/**
 * RBAC 强制执行集成测试（Phase 0 Bug ① Sa-Token loginType + Bug ② 超管 bypass）。
 *
 * <p>以 HTTP 外部行为（200 / 403 / 401）断言：</p>
 * <ul>
 *   <li>非超管 + 有权限 → 200（Bug ①：权限解析按真实角色生效）</li>
 *   <li>非超管 + 无权限 → 403（Bug ①）</li>
 *   <li>超管（SUPER_ADMIN、无任何权限）→ 200（Bug ②：框架 {@code AuthorizationBypassResolver} 放行）</li>
 *   <li>未登录 → 401（{@code @RequireAuth} 不被 bypass，无后门）</li>
 * </ul>
 *
 * <p>注：admin 无 {@code @RequireRole} 端点，故 {@code @RequireRole} 的 bypass 覆盖由框架侧
 * {@code AuthAnnotationIntegrationTest} 验证（同一 {@code shouldBypassAuthorization} 代码路径）。</p>
 *
 * <p>用 {@code RANDOM_PORT} + {@link TestRestTemplate} 走真实 servlet 过滤器链，Sa-Token 登录/token
 * 往返与生产一致。</p>
 *
 * <p>token 头名/前缀从 {@link SaManager} 运行时配置读取（不硬编码）——因为 {@code src/test/resources/application.yml}
 * 遮蔽了 main 的，测试环境 Sa-Token 用默认 token-name {@code satoken}，与生产的 {@code Authorization/Bearer} 不同。
 * 读运行时配置可自适应两者。</p>
 *
 * <p>{@code @DirtiesContext(AFTER_CLASS)}：本类结束后重建上下文 + 真库（{@code ddl-auto=create-drop}），
 * 避免种子数据（用户名/角色码带 UUID 后缀、类内不冲突）污染后续测试类。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RbacEnforcementIntegrationTest {

    private static final String PASSWORD = "Test1234";
    private static final String PERMISSION_CODE = "admin:user:read";

    private final AdminUserManagementAppService userAppService;
    private final RoleManagementAppService roleAppService;
    private final TestRestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int port;

    private String usernameWithPermission;
    private String usernameWithoutPermission;

    @Autowired
    RbacEnforcementIntegrationTest(
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
        // UUID 后缀保证用户名/角色码唯一，类内多条用例互不冲突
        String suffix = uuidSuffix();
        usernameWithPermission = "opwith" + suffix;
        usernameWithoutPermission = "opnone" + suffix;

        Long roleId = roleAppService.create(
                new CreateRoleCommand("用户运营_" + suffix, "USEROP_" + suffix, "仅有用户查看权限", 10, null));
        roleAppService.assignPermissions(roleId, new AssignPermissionsCommand(List.of(PERMISSION_CODE)));

        Long userWithPermissionId = userAppService.create(
                new CreateAdminUserCommand(usernameWithPermission, PASSWORD, "有权限运营", null, null));
        userAppService.assignRoles(userWithPermissionId, new AssignRolesCommand(List.of(roleId)));

        userAppService.create(
                new CreateAdminUserCommand(usernameWithoutPermission, PASSWORD, "无权限运营", null, null));
    }

    @Test
    @DisplayName("非超管且拥有权限：访问 @RequirePermission 接口返回 200")
    void given_nonSuperAdminWithPermission_when_listUsers_then_200() {
        String token = login(usernameWithPermission);

        ResponseEntity<String> response = getWithToken("/api/admin/users", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("超管（SUPER_ADMIN 角色、未授予任何权限）：访问 @RequirePermission 接口返回 200（框架 bypass 放行）")
    void given_superAdmin_when_listUsers_then_200() {
        // 超管自包含种子：SUPER_ADMIN 角色（固定 code，触发 isSuperAdmin）+ 超管用户，不授予任何权限，
        // 完全依赖框架 AuthorizationBypassResolver 放行（验 Phase 0 Bug ②）。
        String suffix = uuidSuffix();
        String superAdminUsername = "superadmin" + suffix;
        Long superAdminRoleId = roleAppService.create(
                new CreateRoleCommand("超管_" + suffix, AdminRole.SUPER_ADMIN_CODE, "超级管理员", 0, null));
        Long superAdminUserId = userAppService.create(
                new CreateAdminUserCommand(superAdminUsername, PASSWORD, "超管", null, null));
        userAppService.assignRoles(superAdminUserId, new AssignRolesCommand(List.of(superAdminRoleId)));

        String token = login(superAdminUsername);

        ResponseEntity<String> response = getWithToken("/api/admin/users", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("非超管且缺少权限：访问 @RequirePermission 接口返回 403")
    void given_nonSuperAdminWithoutPermission_when_listUsers_then_403() {
        String token = login(usernameWithoutPermission);

        ResponseEntity<String> response = getWithToken("/api/admin/users", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("未登录访问 @RequireAuth 保护接口返回 401")
    void given_unauthenticated_when_listUsers_then_401() {
        ResponseEntity<String> response = getWithToken("/api/admin/users", null);

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
