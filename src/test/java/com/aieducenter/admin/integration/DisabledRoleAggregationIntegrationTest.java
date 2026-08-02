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
import com.aieducenter.admin.application.MenuManagementAppService;
import com.aieducenter.admin.application.RoleManagementAppService;
import com.aieducenter.admin.application.dto.command.AssignMenusCommand;
import com.aieducenter.admin.application.dto.command.AssignPermissionsCommand;
import com.aieducenter.admin.application.dto.command.AssignRolesCommand;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.application.dto.command.CreateMenuCommand;
import com.aieducenter.admin.application.dto.command.CreateRoleCommand;
import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.enums.AdminRoleStatus;
import com.aieducenter.admin.domain.enums.AdminUserStatus;
import com.aieducenter.admin.domain.enums.MenuType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;

/**
 * 禁用角色在汇总聚合中视为不存在（issue #21 / REQ-13-T1）——角色管理页的「禁用」成为真实的访问回收。
 *
 * <p>语义：用户菜单/权限 = 其全部<b>启用</b>角色的并集（CONTEXT.md「RBAC」条目决策①）。
 * 各聚合方法（{@code getRoleCodes}/{@code getPermissions}/{@code getMyMenus}）同路径一次修齐，
 * 本类以 HTTP 外部行为钉住：</p>
 * <ul>
 *   <li>角色被禁用后，{@code /auth/current} 的 {@code roleCodes}/{@code permissions} 与
 *       {@code /menus/my} 的 {@code menus} 均剔除该角色贡献，其余启用角色贡献保留（并集语义）——
 *       REQ-13-T3 起 {@code /auth/current} 收敛为身份 claims，菜单聚合断言由 {@code /menus/my} 承接</li>
 *   <li>仅持禁用角色的用户：三项聚合全空</li>
 *   <li>禁用后同一 token 访问受保护端点 → 403（{@code StpInterface} 与 {@code /auth/current}、
 *       {@code /menus/my} 走同一聚合路径，无 Sa-Token 侧缓存——sa-token 1.45 {@code StpLogic.getPermissionList}
 *       每请求直调 {@code StpInterface}）</li>
 *   <li>超管（SUPER_ADMIN）行为不受影响（其角色自身不可禁用，REQ-10 守卫不变）</li>
 * </ul>
 *
 * <p>测试基建同 {@link RbacEnforcementIntegrationTest}：{@code RANDOM_PORT} + {@link TestRestTemplate}
 * 走真实过滤器链；UUID 后缀保证类内用例互不冲突；{@code @DirtiesContext(AFTER_CLASS)} 结束后重建上下文。
 * SUPER_ADMIN 角色 code 有唯一约束，故超管用例在单个测试方法内联创建（类内仅建一次，
 * 同 {@code RbacEnforcementIntegrationTest} 先例）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DisabledRoleAggregationIntegrationTest {

    private static final String PASSWORD = "Test1234";
    private static final String PERM_A = "admin:user:read";
    private static final String PERM_B = "admin:role:read";

    private final AdminUserManagementAppService userAppService;
    private final RoleManagementAppService roleAppService;
    private final MenuManagementAppService menuAppService;
    private final TestRestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int port;

    private String roleACode;
    private String roleBCode;
    private Long roleAId;
    private String menuXRouteName;
    private String menuYRouteName;
    private String mixedUsername;
    private String soloUsername;

    @Autowired
    DisabledRoleAggregationIntegrationTest(
            AdminUserManagementAppService userAppService,
            RoleManagementAppService roleAppService,
            MenuManagementAppService menuAppService,
            TestRestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${local.server.port}") int port) {
        this.userAppService = userAppService;
        this.roleAppService = roleAppService;
        this.menuAppService = menuAppService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.port = port;
    }

    @BeforeEach
    void setUp() {
        // UUID 后缀保证菜单路由名/角色码/用户名唯一，类内多条用例互不冲突
        String suffix = uuidSuffix();
        roleACode = "ROLEA_" + suffix;
        roleBCode = "ROLEB_" + suffix;
        menuXRouteName = "routex_" + suffix;
        menuYRouteName = "routey_" + suffix;
        mixedUsername = "opmixed" + suffix;
        soloUsername = "opsolo" + suffix;

        Long menuXId = menuAppService.create(new CreateMenuCommand(
                "菜单X_" + suffix, menuXRouteName, "/x/" + suffix, "view.x_" + suffix,
                null, null, null, 1, MenuType.MENU,
                null, false, false, false, false, null, null, null, null, AdminUserStatus.ACTIVE));
        Long menuYId = menuAppService.create(new CreateMenuCommand(
                "菜单Y_" + suffix, menuYRouteName, "/y/" + suffix, "view.y_" + suffix,
                null, null, null, 2, MenuType.MENU,
                null, false, false, false, false, null, null, null, null, AdminUserStatus.ACTIVE));

        // 角色 A：权限 PERM_A + 菜单 X；角色 B：权限 PERM_B + 菜单 Y
        roleAId = roleAppService.create(
                new CreateRoleCommand("角色A_" + suffix, roleACode, "将被禁用", 10, null));
        roleAppService.assignPermissions(roleAId, new AssignPermissionsCommand(List.of(PERM_A)));
        roleAppService.assignMenus(roleAId, new AssignMenusCommand(List.of(menuXId)));

        Long roleBId = roleAppService.create(
                new CreateRoleCommand("角色B_" + suffix, roleBCode, "保持启用", 20, null));
        roleAppService.assignPermissions(roleBId, new AssignPermissionsCommand(List.of(PERM_B)));
        roleAppService.assignMenus(roleBId, new AssignMenusCommand(List.of(menuYId)));

        // 混合用户持 [A, B]（验并集语义）；独行用户仅持 [A]（验禁用即全空）
        Long mixedUserId = userAppService.create(
                new CreateAdminUserCommand(mixedUsername, PASSWORD, "混合运营", null, null, null));
        userAppService.assignRoles(mixedUserId, new AssignRolesCommand(List.of(roleAId, roleBId)));

        Long soloUserId = userAppService.create(
                new CreateAdminUserCommand(soloUsername, PASSWORD, "独行运营", null, null, null));
        userAppService.assignRoles(soloUserId, new AssignRolesCommand(List.of(roleAId)));
    }

    @Test
    @DisplayName("启用角色基线：roleCodes/permissions/menus 为全部启用角色的并集")
    void given_enabledRoles_when_current_then_unionOfAllRoleContributions() {
        String token = login(mixedUsername);
        JsonNode data = currentData(token);

        // REQ-13-T3：/auth/current 收敛为 {user, roleCodes, permissions}——HTTP 接缝钉住 menus 键不存在
        assertThat(data.has("user")).as("身份 claims 须含 user：%s", data).isTrue();
        assertThat(data.has("menus")).as("/auth/current 不再下发 menus（导航归 /menus/my）：%s", data).isFalse();

        assertThat(stringList(data.path("roleCodes"))).containsExactlyInAnyOrder(roleACode, roleBCode);
        assertThat(stringList(data.path("permissions"))).containsExactlyInAnyOrder(PERM_A, PERM_B);
        // menus 断言由 /menus/my 承接（REQ-13-T3）
        assertThat(menuRouteNames(myData(token))).contains(menuXRouteName, menuYRouteName);
    }

    @Test
    @DisplayName("禁用角色 A 后：混合用户聚合剔除 A 的贡献、保留启用角色 B 的（并集语义）")
    void given_oneRoleDisabled_when_current_then_excludesDisabledButKeepsEnabledUnion() {
        String token = login(mixedUsername);
        roleAppService.updateStatus(roleAId, AdminRoleStatus.DISABLED);

        JsonNode data = currentData(token);

        assertThat(stringList(data.path("roleCodes"))).containsExactly(roleBCode);
        assertThat(stringList(data.path("permissions"))).containsExactly(PERM_B);
        assertThat(menuRouteNames(myData(token))).contains(menuYRouteName).doesNotContain(menuXRouteName);
    }

    @Test
    @DisplayName("仅持禁用角色的用户：roleCodes/permissions/menus 聚合全空")
    void given_onlyDisabledRoles_when_current_then_allAggregatesEmpty() {
        String token = login(soloUsername);

        // 基线：禁用前聚合非空（否则「全空」断言无区分度）
        JsonNode before = currentData(token);
        assertThat(stringList(before.path("roleCodes"))).containsExactly(roleACode);
        assertThat(stringList(before.path("permissions"))).containsExactly(PERM_A);
        assertThat(menuRouteNames(myData(token))).contains(menuXRouteName);

        roleAppService.updateStatus(roleAId, AdminRoleStatus.DISABLED);

        JsonNode after = currentData(token);
        assertThat(stringList(after.path("roleCodes"))).isEmpty();
        assertThat(stringList(after.path("permissions"))).isEmpty();
        assertThat(myData(token).path("menus").size()).isZero();
    }

    @Test
    @DisplayName("禁用即真实访问回收：同一 token 禁用前 200、禁用后 403")
    void given_roleDisabled_when_accessProtectedEndpoint_then_accessRevoked() {
        String token = login(soloUsername);
        assertThat(getWithToken("/api/admin/users", token).getStatusCode()).isEqualTo(HttpStatus.OK);

        roleAppService.updateStatus(roleAId, AdminRoleStatus.DISABLED);

        assertThat(getWithToken("/api/admin/users", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("超管（SUPER_ADMIN）行为不受影响：聚合与 bypass 照常")
    void given_superAdmin_when_currentAndProtectedAccess_then_unaffected() {
        String suffix = uuidSuffix();
        String superUsername = "opsuper" + suffix;
        Long superRoleId = roleAppService.create(
                new CreateRoleCommand("超管_" + suffix, AdminRole.SUPER_ADMIN_CODE, "超级管理员", 0, null));
        Long superUserId = userAppService.create(
                new CreateAdminUserCommand(superUsername, PASSWORD, "超管", null, null, null));
        userAppService.assignRoles(superUserId, new AssignRolesCommand(List.of(superRoleId)));

        String token = login(superUsername);

        JsonNode data = currentData(token);
        assertThat(stringList(data.path("roleCodes"))).containsExactly(AdminRole.SUPER_ADMIN_CODE);
        // 超管菜单 = 不受角色裁剪的全量（含本用例创建的菜单 X/Y），由 /menus/my 承接（REQ-13-T3）
        assertThat(menuRouteNames(myData(token))).contains(menuXRouteName, menuYRouteName);
        assertThat(getWithToken("/api/admin/users", token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ========== HTTP / JSON 辅助 ==========

    private JsonNode currentData(String token) {
        return fetchData("/api/admin/auth/current", token);
    }

    private JsonNode myData(String token) {
        return fetchData("/api/admin/menus/my", token);
    }

    private JsonNode fetchData(String path, String token) {
        ResponseEntity<String> response = getWithToken(path, token);
        assertThat(response.getStatusCode()).as("GET %s 应 200：%s", path, response.getBody()).isEqualTo(HttpStatus.OK);
        try {
            return objectMapper.readTree(response.getBody()).path("data");
        } catch (Exception e) {
            throw new AssertionError("解析 " + path + " 响应失败：" + response.getBody(), e);
        }
    }

    private List<String> stringList(JsonNode arrayNode) {
        List<String> out = new ArrayList<>();
        arrayNode.forEach(node -> out.add(node.asText()));
        return out;
    }

    private List<String> menuRouteNames(JsonNode data) {
        List<String> out = new ArrayList<>();
        data.path("menus").forEach(node -> out.add(node.path("routeName").asText()));
        return out;
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
