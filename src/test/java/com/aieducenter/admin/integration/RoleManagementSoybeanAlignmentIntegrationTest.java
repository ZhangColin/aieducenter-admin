package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
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
import com.aieducenter.admin.application.dto.command.AssignRolesCommand;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.application.dto.command.CreateRoleCommand;
import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.repository.AdminRoleRepository;
import com.aieducenter.admin.domain.repository.AdminUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;

/**
 * 角色管理对齐 Soybean 集成测试（REQ-10 / issue #16）。
 *
 * <p>以 HTTP 外部行为（状态码 + 错误体 + 响应字段）端到端钉死 issue #16 的 AC：</p>
 * <ul>
 *   <li>SUPER_ADMIN 角色禁用 → 403（命中 {@code SUPER_ADMIN_CANNOT_DISABLE}）</li>
 *   <li>SUPER_ADMIN 角色删除 → 403（命中 {@code SUPER_ADMIN_CANNOT_DELETE}）；守卫在聚合内</li>
 *   <li>普通角色启停/删除不受影响（守卫不误伤普通角色）</li>
 *   <li>{@code GET /roles/all}：仅启用、不分页、精简 {id,name,code}</li>
 *   <li>{@code home} CRUD round-trip + {@code RoleResponse} 出 status/createdAt/updatedAt</li>
 *   <li>分配接口接受空集 = 清空（去掉 {@code @NotEmpty}）</li>
 * </ul>
 *
 * <p>仿 {@link BreakGlassAccountProtectionIntegrationTest}：真库（{@code ddl-auto=create}、Flyway 关闭）
 * + 真 Spring + SecurityFilter + 全局异常处理。SUPER_ADMIN 守卫点在 {@link AdminRole#disable()} /
 * {@link AdminRole#markAsDeleted()}（聚合内，单一执行点，仿 AdminUser 破窗号 guard，ADR-0003 修订）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoleManagementSoybeanAlignmentIntegrationTest {

    private static final String PASSWORD = "Test1234";

    private final RoleManagementAppService roleAppService;
    private final AdminUserManagementAppService userAppService;
    private final AdminRoleRepository adminRoleRepository;
    private final AdminUserRepository adminUserRepository;
    private final TestRestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int port;

    private String callerUsername;
    private Long superAdminRoleId;

    @Autowired
    RoleManagementSoybeanAlignmentIntegrationTest(
            RoleManagementAppService roleAppService,
            AdminUserManagementAppService userAppService,
            AdminRoleRepository adminRoleRepository,
            AdminUserRepository adminUserRepository,
            TestRestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${local.server.port}") int port) {
        this.roleAppService = roleAppService;
        this.userAppService = userAppService;
        this.adminRoleRepository = adminRoleRepository;
        this.adminUserRepository = adminUserRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.port = port;
    }

    @BeforeEach
    void setUp() {
        // SUPER_ADMIN 角色（按 code 幂等；守卫测试目标 + caller 授权都走它）
        superAdminRoleId = adminRoleRepository.findByCode(AdminRole.SUPER_ADMIN_CODE)
                .map(AdminRole::getId)
                .orElseGet(() -> roleAppService.create(
                        new CreateRoleCommand("超级管理员", AdminRole.SUPER_ADMIN_CODE, "超级管理员", 0, null)));

        // 调用者：超管（bypass 权限检查，以便调用 admin:role:write 端点操作 SUPER_ADMIN 角色）
        callerUsername = "rolecaller";
        if (!adminUserRepository.existsByUsername(callerUsername)) {
            Long callerId = userAppService.create(
                    new CreateAdminUserCommand(callerUsername, PASSWORD, "调用者", null, null, null));
            userAppService.assignRoles(callerId, new AssignRolesCommand(List.of(superAdminRoleId)));
        }
    }

    // ========== SUPER_ADMIN 角色守卫（破窗韧性）==========

    @Test
    @DisplayName("禁用 SUPER_ADMIN 角色 → 403，错误体命中「超级管理员角色不能禁用」")
    void given_superAdminRole_when_disable_then_403() {
        String token = login(callerUsername);

        ResponseEntity<String> response = withToken(HttpMethod.PUT,
                "/api/admin/roles/" + superAdminRoleId + "/status?status=0", token, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(messageOf(response)).contains("超级管理员角色不能禁用");
    }

    @Test
    @DisplayName("删除 SUPER_ADMIN 角色 → 403，错误体命中「超级管理员角色不能删除」")
    void given_superAdminRole_when_delete_then_403() {
        String token = login(callerUsername);

        ResponseEntity<String> response = withToken(HttpMethod.DELETE,
                "/api/admin/roles/" + superAdminRoleId, token, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(messageOf(response)).contains("超级管理员角色不能删除");
    }

    // ========== 普通角色启停/删除（守卫不误伤）==========

    @Test
    @DisplayName("普通角色：禁用→/roles/all 不含；启用→含；删除→成功且不含")
    void given_normalRole_when_disableEnableDelete_then_successAndFiltered() throws Exception {
        String token = login(callerUsername);
        Long roleId = createRole(token, "运营_" + uuidSuffix(), "OPERATOR_" + uuidSuffix(), null);

        // 初始启用 → /roles/all 含
        assertThat(roleAllIds(token)).contains(roleId);

        // 禁用 → /roles/all 不含
        assertThat(withToken(HttpMethod.PUT,
                "/api/admin/roles/" + roleId + "/status?status=0", token, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(roleAllIds(token)).doesNotContain(roleId);

        // 启用 → /roles/all 复含
        assertThat(withToken(HttpMethod.PUT,
                "/api/admin/roles/" + roleId + "/status?status=1", token, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(roleAllIds(token)).contains(roleId);

        // 删除 → 成功，/roles/all 不含
        assertThat(withToken(HttpMethod.DELETE, "/api/admin/roles/" + roleId, token, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(roleAllIds(token)).doesNotContain(roleId);
    }

    // ========== /roles/all 形状 ==========

    @Test
    @DisplayName("GET /roles/all：精简 {id,name,code}（无 sortOrder/description/menuIds），仅启用")
    void given_roles_when_getAll_then_compactEnabledOptions() throws Exception {
        String token = login(callerUsername);

        ResponseEntity<String> response = withToken(HttpMethod.GET, "/api/admin/roles/all", token, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertThat(data.isArray()).isTrue();
        // 至少含 SUPER_ADMIN（启用）；字典项仅 id/name/code 三字段
        boolean hasSuperAdmin = false;
        for (JsonNode item : data) {
            assertThat(item.has("id")).isTrue();
            assertThat(item.has("name")).isTrue();
            assertThat(item.has("code")).isTrue();
            assertThat(item.has("sortOrder")).as("/roles/all 字典项不应出 sortOrder").isFalse();
            assertThat(item.has("description")).isFalse();
            if ("SUPER_ADMIN".equals(item.path("code").asText())) {
                hasSuperAdmin = true;
            }
        }
        assertThat(hasSuperAdmin).as("/roles/all 应含启用的 SUPER_ADMIN").isTrue();
    }

    // ========== home round-trip + 响应字段 ==========

    @Test
    @DisplayName("home CRUD round-trip + RoleResponse 出 status/createdAt/updatedAt")
    void given_roleWithHome_when_createGetUpdate_then_homeRoundTrips_andAuditFieldsExposed() throws Exception {
        String token = login(callerUsername);
        String code = "HOME_" + uuidSuffix();

        // 创建带 home
        Long roleId = createRole(token, "首页角色_" + code, code, "home");

        JsonNode role = getRole(token, roleId);
        assertThat(role.path("home").asText()).isEqualTo("home");
        assertThat(role.path("status").asInt()).isEqualTo(1); // ENABLED 整数 code 出站
        assertThat(role.path("createdAt").isNull()).isFalse();
        assertThat(role.path("updatedAt").isNull()).isFalse();

        // 更新 home
        ResponseEntity<String> updated = withToken(HttpMethod.PUT, "/api/admin/roles/" + roleId, token,
                "{\"name\":\"首页角色_" + code + "\",\"code\":\"" + code + "\",\"description\":null,"
                        + "\"sortOrder\":1,\"home\":\"manage_user\"}");
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode roleAfter = getRole(token, roleId);
        assertThat(roleAfter.path("home").asText()).isEqualTo("manage_user");
    }

    // ========== 分配接口接受空集 = 清空 ==========

    @Test
    @DisplayName("分配权限空集 = 清空（去掉 @NotEmpty 后不再 400，clear-then-add）")
    void given_roleWithPermission_when_assignEmpty_then_cleared() throws Exception {
        String token = login(callerUsername);
        Long roleId = createRole(token, "权限角色_" + uuidSuffix(), "PERM_" + uuidSuffix(), null);

        // 先分配一个已知权限（admin:user:read 由 @RequirePermission 扫描，必然存在）
        assertThat(withToken(HttpMethod.PUT, "/api/admin/roles/" + roleId + "/permissions", token,
                "{\"permissionCodes\":[\"admin:user:read\"]}").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getRole(token, roleId).path("permissionCodes").toString()).contains("admin:user:read");

        // 再分配空集 → 清空（旧 @NotEmpty 会 400，现 200）
        assertThat(withToken(HttpMethod.PUT, "/api/admin/roles/" + roleId + "/permissions", token,
                "{\"permissionCodes\":[]}").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getRole(token, roleId).path("permissionCodes").size()).isZero();
    }

    // ========== helpers ==========

    private Long createRole(String token, String name, String code, String home) {
        ResponseEntity<String> response = withToken(HttpMethod.POST, "/api/admin/roles", token,
                "{\"name\":\"" + name + "\",\"code\":\"" + code + "\",\"description\":null,"
                        + "\"sortOrder\":10,\"home\":" + (home == null ? "null" : "\"" + home + "\"") + "}");
        assertThat(response.getStatusCode()).as("创建角色应 200：%s", response.getBody()).isEqualTo(HttpStatus.OK);
        try {
            return objectMapper.readTree(response.getBody()).path("data").asLong();
        } catch (Exception e) {
            throw new AssertionError("解析角色 id 失败：" + response.getBody(), e);
        }
    }

    private JsonNode getRole(String token, Long roleId) throws Exception {
        ResponseEntity<String> response = withToken(HttpMethod.GET, "/api/admin/roles/" + roleId, token, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).path("data");
    }

    private List<Long> roleAllIds(String token) throws Exception {
        ResponseEntity<String> response = withToken(HttpMethod.GET, "/api/admin/roles/all", token, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : data) {
            ids.add(item.path("id").asLong());
        }
        return ids;
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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String uuidSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
