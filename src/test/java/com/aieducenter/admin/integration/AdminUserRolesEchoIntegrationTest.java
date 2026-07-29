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
 * 用户详情 roles 回显集成测试（REQ-4 / issue #3）。
 *
 * <p>走真库 + 真 Spring + SecurityFilter，在 HTTP 外部行为上钉住契约：</p>
 * <ul>
 *   <li>{@code GET /users/{id}} 返回 {@code roles: [{id, name, code}]}，id 按字符串序列化（Long 精度约定）；</li>
 *   <li>分配（PUT roles）后再查详情，roles 反映最新分配；</li>
 *   <li>角色被软删但关联行仍在（残留关联）→ roles 不含该角色（显式过滤的真实库行为）；</li>
 *   <li>未分配角色 → {@code roles: []}（非 null、不省略）；</li>
 *   <li>{@code GET /users} 列表 items 无 roles 键（响应形状与现状逐字段一致）。</li>
 * </ul>
 *
 * <p>残留关联状态由 {@code adminRoleRepository.delete(role)} 直接构造（绕过应用层
 * 「角色使用中不可删」守卫——生产上该状态经直接 DB 操作或守卫落地前的历史数据产生，
 * 清理残留关联不在本 issue 范围）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminUserRolesEchoIntegrationTest {

    private static final String PASSWORD = "Test1234";

    private final AdminUserManagementAppService userAppService;
    private final RoleManagementAppService roleAppService;
    private final AdminUserRepository adminUserRepository;
    private final AdminRoleRepository adminRoleRepository;
    private final TestRestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int port;

    private String callerUsername;

    @Autowired
    AdminUserRolesEchoIntegrationTest(
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
    void setUp() {
        // SUPER_ADMIN 角色（按 code 幂等）
        Long superAdminRoleId = adminRoleRepository.findByCode(AdminRole.SUPER_ADMIN_CODE)
                .map(AdminRole::getId)
                .orElseGet(() -> roleAppService.create(
                        new CreateRoleCommand("超级管理员", AdminRole.SUPER_ADMIN_CODE, "超级管理员", 0)));

        // 调用者：超管（bypass 权限检查，以便调用 admin:user:* 端点）
        callerUsername = "roleecho";
        if (!adminUserRepository.existsByUsername(callerUsername)) {
            Long callerId = userAppService.create(
                    new CreateAdminUserCommand(callerUsername, PASSWORD, "调用者", null, null));
            userAppService.assignRoles(callerId, new AssignRolesCommand(List.of(superAdminRoleId)));
        }
    }

    @Test
    @DisplayName("分配角色后查详情 → roles 回显 {id, name, code}，id 为字符串")
    void given_assignedRoles_when_getUserDetail_then_rolesEchoed() throws Exception {
        String suffixA = uuidSuffix();
        String suffixB = uuidSuffix();
        Long roleA = roleAppService.create(new CreateRoleCommand("运营_" + suffixA, "OP_A_" + suffixA, "运营A", 10));
        Long roleB = roleAppService.create(new CreateRoleCommand("客服_" + suffixB, "OP_B_" + suffixB, "客服B", 20));
        Long userId = userAppService.create(
                new CreateAdminUserCommand("echo_" + uuidSuffix(), PASSWORD, "回显用户", null, null));
        userAppService.assignRoles(userId, new AssignRolesCommand(List.of(roleA, roleB)));

        JsonNode roles = getUserRoles(login(callerUsername), userId);

        assertThat(roles.isArray()).isTrue();
        assertThat(roles).hasSize(2);
        // 独立事实源断言：name/code/id 均来自创建时的已知值
        assertThat(roles.findValuesAsText("name")).containsExactlyInAnyOrder("运营_" + suffixA, "客服_" + suffixB);
        assertThat(roles.findValuesAsText("code")).containsExactlyInAnyOrder("OP_A_" + suffixA, "OP_B_" + suffixB);
        // id 字符串序列化（Long 精度约定，与响应中其他 id 字段一致）
        JsonNode first = roles.get(0);
        assertThat(first.path("id").isTextual()).isTrue();
        assertThat(roles.findValuesAsText("id")).containsExactlyInAnyOrder(
                String.valueOf(roleA), String.valueOf(roleB));
        // 裁剪投影：不得泄露 menuIds/permissionCodes 等重数据
        assertThat(first.has("menuIds")).isFalse();
        assertThat(first.has("permissionCodes")).isFalse();
    }

    @Test
    @DisplayName("角色被软删但关联行仍在 → 详情 roles 不含该角色（显式过滤真实行为）")
    void given_softDeletedRoleStillLinked_when_getUserDetail_then_roleExcluded() throws Exception {
        Long aliveRoleId = roleAppService.create(
                new CreateRoleCommand("存活_" + uuidSuffix(), "ALIVE_" + uuidSuffix(), "存活角色", 10));
        Long doomedRoleId = roleAppService.create(
                new CreateRoleCommand("将删_" + uuidSuffix(), "DOOMED_" + uuidSuffix(), "将删角色", 20));
        Long userId = userAppService.create(
                new CreateAdminUserCommand("stale_" + uuidSuffix(), PASSWORD, "残留关联用户", null, null));
        userAppService.assignRoles(userId, new AssignRolesCommand(List.of(aliveRoleId, doomedRoleId)));

        // 直接经仓储软删角色（绕过应用层「使用中不可删」守卫）→ 关联行残留
        AdminRole doomed = adminRoleRepository.findById(doomedRoleId).orElseThrow();
        adminRoleRepository.delete(doomed);

        JsonNode roles = getUserRoles(login(callerUsername), userId);

        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).path("id").asText()).isEqualTo(String.valueOf(aliveRoleId));
    }

    @Test
    @DisplayName("未分配角色 → roles 为空数组 []（非 null、不省略）")
    void given_noRoles_when_getUserDetail_then_rolesEmptyArray() throws Exception {
        Long userId = userAppService.create(
                new CreateAdminUserCommand("norole_" + uuidSuffix(), PASSWORD, "无角色用户", null, null));

        JsonNode roles = getUserRoles(login(callerUsername), userId);

        assertThat(roles.isArray()).isTrue();
        assertThat(roles).isEmpty();
    }

    @Test
    @DisplayName("PUT 分配后再查详情 → roles 反映最新分配（全量覆盖语义）")
    void given_reassignedRoles_when_getUserDetail_then_reflectsLatest() throws Exception {
        Long roleA = roleAppService.create(new CreateRoleCommand("旧角色_" + uuidSuffix(), "OLD_" + uuidSuffix(), "旧", 10));
        Long roleB = roleAppService.create(new CreateRoleCommand("新角色_" + uuidSuffix(), "NEW_" + uuidSuffix(), "新", 20));
        Long userId = userAppService.create(
                new CreateAdminUserCommand("swap_" + uuidSuffix(), PASSWORD, "换角色用户", null, null));
        userAppService.assignRoles(userId, new AssignRolesCommand(List.of(roleA)));

        String token = login(callerUsername);
        ResponseEntity<String> assigned = withToken(HttpMethod.PUT,
                "/api/admin/users/" + userId + "/roles", token, "{\"roleIds\":[" + roleB + "]}");
        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode roles = getUserRoles(token, userId);
        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).path("id").asText()).isEqualTo(String.valueOf(roleB));
    }

    @Test
    @DisplayName("GET /users 列表 items 无 roles 键（响应形状与现状一致）")
    void given_usersExist_when_getUserList_then_itemsHaveNoRolesKey() throws Exception {
        userAppService.create(new CreateAdminUserCommand("listcheck_" + uuidSuffix(), PASSWORD, "列表用户", null, null));

        ResponseEntity<String> response = withToken(HttpMethod.GET, "/api/admin/users", login(callerUsername), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode items = objectMapper.readTree(response.getBody()).path("data").path("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items).isNotEmpty();
        for (JsonNode item : items) {
            assertThat(item.has("roles")).as("列表项不得携带 roles 键：%s", item).isFalse();
        }
    }

    // ========== helpers ==========

    private JsonNode getUserRoles(String token, Long userId) throws Exception {
        ResponseEntity<String> response = withToken(HttpMethod.GET, "/api/admin/users/" + userId, token, null);
        assertThat(response.getStatusCode()).as("查询用户详情失败：%s", response.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertThat(data.has("roles")).as("详情必须携带 roles 键：%s", data).isTrue();
        return data.path("roles");
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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String uuidSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
