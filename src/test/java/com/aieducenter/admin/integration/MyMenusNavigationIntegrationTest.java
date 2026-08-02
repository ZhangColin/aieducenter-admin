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
 * 「我的导航」端点 {@code GET /api/admin/menus/my}（issue #22 / REQ-13-T2）——前端 Soybean dynamic
 * 路由模式的导航数据源，响应 {@code {home, menus}}。
 *
 * <p>以 HTTP 外部行为钉住验收标准：</p>
 * <ul>
 *   <li>消费面权限：未登录 → 401；已登录但无 {@code admin:menu:read} → 200（不挂管理权限）</li>
 *   <li>menus：全部启用角色并集裁剪（祖先补全 + 裁空 directory 保持）；只含启用菜单——
 *       禁用叶子不下发、禁用 directory 整棵子树不下发（幸存子节点不提升到根）</li>
 *   <li>超管：不受角色裁剪的全量<b>启用</b>菜单（status 过滤同样生效）；home 同规则</li>
 *   <li>home：全部启用角色按 {@code (sortOrder 升, id 升)} 取第一个非空白值；全空 → JSON null</li>
 *   <li>回归保护：管理面 {@code GET /menus}、{@code GET /menus/tree} 仍返回禁用菜单</li>
 * </ul>
 *
 * <p>测试基建同 {@link RbacEnforcementIntegrationTest}：{@code RANDOM_PORT} + {@link TestRestTemplate}
 * 走真实过滤器链；UUID 后缀保证类内用例互不冲突。SUPER_ADMIN 角色 code 有唯一约束，
 * 超管用例在单个测试方法内联创建（类内仅建一次，同 {@code DisabledRoleAggregationIntegrationTest} 先例）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MyMenusNavigationIntegrationTest {

    private static final String PASSWORD = "Test1234";

    private final AdminUserManagementAppService userAppService;
    private final RoleManagementAppService roleAppService;
    private final MenuManagementAppService menuAppService;
    private final TestRestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int port;

    private String suffix;
    private String dirERouteName;
    private String dirDRouteName;
    private String leafXRouteName;
    private String leafYRouteName;
    private String leafWRouteName;
    private String leafZRouteName;
    private String homeC;
    private Long roleAId;
    private Long roleBId;
    private Long roleCId;
    private String mixedUsername;
    private String soloUsername;

    @Autowired
    MyMenusNavigationIntegrationTest(
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
        suffix = uuidSuffix();
        dirERouteName = "dire_" + suffix;
        dirDRouteName = "dird_" + suffix;
        leafXRouteName = "leafx_" + suffix;
        leafYRouteName = "leafy_" + suffix;
        leafWRouteName = "leafw_" + suffix;
        leafZRouteName = "leafz_" + suffix;
        homeC = "homec_" + suffix;

        // dirE（启用 directory）├ leafX（启用，sort 1）├ leafY（启用，sort 2）└ leafW（禁用，sort 3）
        Long dirEId = menuAppService.create(dir(dirERouteName, null, 1, AdminUserStatus.ACTIVE));
        Long leafXId = menuAppService.create(leaf(leafXRouteName, dirEId, 1, AdminUserStatus.ACTIVE));
        Long leafYId = menuAppService.create(leaf(leafYRouteName, dirEId, 2, AdminUserStatus.ACTIVE));
        Long leafWId = menuAppService.create(leaf(leafWRouteName, dirEId, 3, AdminUserStatus.DISABLED));
        // dirD（禁用 directory）└ leafZ（启用）——禁用 directory 的整棵子树不得下发
        Long dirDId = menuAppService.create(dir(dirDRouteName, null, 2, AdminUserStatus.DISABLED));
        Long leafZId = menuAppService.create(leaf(leafZRouteName, dirDId, 1, AdminUserStatus.ACTIVE));

        // 角色 A（sort 1，home null）：leafX + leafZ + leafW（后两者分别因子树禁用/自身禁用不可见）
        roleAId = roleAppService.create(
                new CreateRoleCommand("角色A_" + suffix, "ROLEA_" + suffix, "home null", 1, null));
        roleAppService.assignMenus(roleAId, new AssignMenusCommand(List.of(leafXId, leafZId, leafWId)));
        // 角色 B（sort 2，home 空白）：leafY
        roleBId = roleAppService.create(
                new CreateRoleCommand("角色B_" + suffix, "ROLEB_" + suffix, "home 空白", 2, "   "));
        roleAppService.assignMenus(roleBId, new AssignMenusCommand(List.of(leafYId)));
        // 角色 C（sort 3，home 非空白）：无菜单——home 仍取其值（home 与菜单分配无关）
        roleCId = roleAppService.create(
                new CreateRoleCommand("角色C_" + suffix, "ROLEC_" + suffix, "home 非空白", 3, homeC));

        mixedUsername = "opmixed" + suffix;
        Long mixedUserId = userAppService.create(
                new CreateAdminUserCommand(mixedUsername, PASSWORD, "混合运营", null, null, null));
        userAppService.assignRoles(mixedUserId, new AssignRolesCommand(List.of(roleAId, roleBId, roleCId)));

        soloUsername = "opsolo" + suffix;
        Long soloUserId = userAppService.create(
                new CreateAdminUserCommand(soloUsername, PASSWORD, "独行运营", null, null, null));
        userAppService.assignRoles(soloUserId, new AssignRolesCommand(List.of(roleAId)));
    }

    @Test
    @DisplayName("未登录访问 /menus/my → 401")
    void given_unauthenticated_when_my_then_401() {
        ResponseEntity<String> response = getWithToken("/api/admin/menus/my", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("已登录但无 admin:menu:read 权限 → /menus/my 200；管理面 /menus 仍 403")
    void given_loginWithoutMenuReadPermission_when_my_then_200_andManagementEndpoint403() {
        String token = login(mixedUsername);

        ResponseEntity<String> myResponse = getWithToken("/api/admin/menus/my", token);
        assertThat(myResponse.getStatusCode()).as("消费面登录即可：%s", myResponse.getBody()).isEqualTo(HttpStatus.OK);

        // 同一用户访问管理面 → 403，证明该用户确实无 admin:menu:read（消费面放行不是权限失效）
        assertThat(getWithToken("/api/admin/menus", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("启用角色并集裁剪：祖先补全 + 禁用叶子/禁用 directory 子树剔除 + home 取第一个非空白")
    void given_mixedUser_when_my_then_unionClippedEnabledTreeAndHome() {
        JsonNode data = myData(login(mixedUsername));

        // home：角色A(null) → 角色B(空白) → 角色C(homeC)，第一个非空白
        assertThat(data.path("home").asText()).isEqualTo(homeC);

        // menus：唯一根 dirE，子节点按 sortOrder = [leafX, leafY]
        JsonNode menus = data.path("menus");
        assertThat(menus).hasSize(1);
        JsonNode root = menus.get(0);
        assertThat(root.path("routeName").asText()).isEqualTo(dirERouteName);
        assertThat(stringList(root.path("children"), "routeName"))
                .containsExactly(leafXRouteName, leafYRouteName);

        // 全树递归：禁用叶子 leafW、禁用 directory dirD 及其启用后代 leafZ 均不下发（leafZ 不提升到根）
        assertThat(allRouteNames(menus))
                .contains(dirERouteName, leafXRouteName, leafYRouteName)
                .doesNotContain(leafWRouteName, dirDRouteName, leafZRouteName);

        // 节点字段模型 = MenuResponse 全字段（Soybean 路由生成器全字段，枚举整数 code 出站）
        JsonNode leafX = root.path("children").get(0);
        assertThat(leafX.path("routePath").asText()).isEqualTo("/" + leafXRouteName);
        assertThat(leafX.path("component").asText()).isEqualTo("view." + leafXRouteName);
        assertThat(leafX.path("menuType").asInt()).isEqualTo(2);
        assertThat(leafX.path("status").asInt()).isEqualTo(1);
        assertThat(leafX.has("iconType")).isTrue();
        assertThat(leafX.has("sortOrder")).isTrue();
        assertThat(leafX.has("children")).isTrue();
    }

    @Test
    @DisplayName("home 排序：sortOrder 更小的非空白 home 优先（与角色创建顺序无关）")
    void given_firstNonBlankHomeAtLowerSortOrder_when_my_then_thatHome() {
        String homeF = "homef_" + suffix;
        Long roleFId = roleAppService.create(
                new CreateRoleCommand("角色F_" + suffix, "ROLEF_" + suffix, "sort 1 home", 1, homeF));
        String username = "ophome" + suffix;
        Long userId = userAppService.create(
                new CreateAdminUserCommand(username, PASSWORD, "首页运营", null, null, null));
        userAppService.assignRoles(userId, new AssignRolesCommand(List.of(roleCId, roleFId)));

        JsonNode data = myData(login(username));

        assertThat(data.path("home").asText()).isEqualTo(homeF); // sort 1 的 homeF 先于 sort 3 的 homeC
    }

    @Test
    @DisplayName("全部角色 home 为空 → JSON \"home\": null（含 null 序列化，非省略字段）")
    void given_allBlankHomes_when_my_then_homeJsonNull() {
        String username = "opblank" + suffix;
        Long userId = userAppService.create(
                new CreateAdminUserCommand(username, PASSWORD, "空首页运营", null, null, null));
        userAppService.assignRoles(userId, new AssignRolesCommand(List.of(roleAId, roleBId)));

        JsonNode data = myData(login(username));

        assertThat(data.has("home")).as("home 字段须序列化出 null 而非省略：%s", data).isTrue();
        assertThat(data.path("home").isNull()).isTrue();
        // 菜单不受 home 影响：A+B 并集 = dirE 下 [leafX, leafY]
        assertThat(allRouteNames(data.path("menus"))).contains(leafXRouteName, leafYRouteName);
    }

    @Test
    @DisplayName("超管：不受角色裁剪的全量启用菜单（未分配的启用菜单可见、禁用项不下发），home 同规则")
    void given_superAdmin_when_my_then_allEnabledMenusWithoutDisabled() {
        String superUsername = "opsuper" + suffix;
        Long superRoleId = roleAppService.create(
                new CreateRoleCommand("超管_" + suffix, AdminRole.SUPER_ADMIN_CODE, "超级管理员", 0, null));
        Long superUserId = userAppService.create(
                new CreateAdminUserCommand(superUsername, PASSWORD, "超管", null, null, null));
        userAppService.assignRoles(superUserId, new AssignRolesCommand(List.of(superRoleId)));

        JsonNode data = myData(login(superUsername));

        // 本用例创建的启用菜单均未分配给 SUPER_ADMIN 角色——可见即证明不受角色裁剪
        assertThat(allRouteNames(data.path("menus")))
                .contains(dirERouteName, leafXRouteName, leafYRouteName)
                .doesNotContain(leafWRouteName, dirDRouteName, leafZRouteName);
        // SUPER_ADMIN.home 为 null（种子即如此）→ 全空 → null
        assertThat(data.path("home").isNull()).isTrue();
    }

    @Test
    @DisplayName("禁用角色 B 后：混合用户剔除 B 的菜单贡献、保留启用角色的；home 仍取启用角色 C")
    void given_roleDisabled_when_my_then_contributionExcluded() {
        String token = login(mixedUsername);
        roleAppService.updateStatus(roleBId, AdminRoleStatus.DISABLED);

        JsonNode data = myData(token);

        assertThat(allRouteNames(data.path("menus")))
                .contains(leafXRouteName)
                .doesNotContain(leafYRouteName);
        assertThat(data.path("home").asText()).isEqualTo(homeC);
    }

    @Test
    @DisplayName("仅持禁用角色的用户 → menus: [] 且 home: null")
    void given_onlyDisabledRoles_when_my_then_emptyMenusAndNullHome() {
        String token = login(soloUsername);

        // 基线：禁用前可见 leafX（否则「全空」断言无区分度）
        assertThat(allRouteNames(myData(token).path("menus"))).contains(leafXRouteName);

        roleAppService.updateStatus(roleAId, AdminRoleStatus.DISABLED);

        JsonNode data = myData(token);
        assertThat(data.path("menus").size()).isZero();
        assertThat(data.path("home").isNull()).isTrue();
    }

    @Test
    @DisplayName("回归保护：管理面 GET /menus、GET /menus/tree 仍返回禁用菜单（含禁用项全量）")
    void given_disabledMenus_when_managementEndpoints_then_stillReturnDisabled() {
        Long roleMId = roleAppService.create(
                new CreateRoleCommand("菜单运营_" + suffix, "MENUOP_" + suffix, "仅菜单查看", 10, null));
        roleAppService.assignPermissions(roleMId, new AssignPermissionsCommand(List.of("admin:menu:read")));
        String username = "opmenu" + suffix;
        Long userId = userAppService.create(
                new CreateAdminUserCommand(username, PASSWORD, "菜单运营", null, null, null));
        userAppService.assignRoles(userId, new AssignRolesCommand(List.of(roleMId)));
        String token = login(username);

        JsonNode tree = okData(getWithToken("/api/admin/menus/tree", token));
        assertThat(allRouteNames(tree)).contains(leafWRouteName, dirDRouteName, leafZRouteName);

        JsonNode page = okData(getWithToken("/api/admin/menus?size=500", token));
        List<String> flatRouteNames = new ArrayList<>();
        page.path("items").forEach(node -> flatRouteNames.add(node.path("routeName").asText()));
        assertThat(flatRouteNames).contains(leafWRouteName, dirDRouteName, leafZRouteName);
    }

    // ========== fixture ==========

    /** 目录（Soybean directory，component 为空）。 */
    private CreateMenuCommand dir(String routeName, Long parentId, int sortOrder, AdminUserStatus status) {
        return new CreateMenuCommand(
                "目录_" + routeName, routeName, "/" + routeName, null,
                null, null, parentId, sortOrder, MenuType.DIRECTORY,
                null, false, false, false, false, null, null, null, null, status);
    }

    /** 叶子菜单（routePath/component 由 routeName 派生，可断言）。 */
    private CreateMenuCommand leaf(String routeName, Long parentId, int sortOrder, AdminUserStatus status) {
        return new CreateMenuCommand(
                "菜单_" + routeName, routeName, "/" + routeName, "view." + routeName,
                null, null, parentId, sortOrder, MenuType.MENU,
                null, false, false, false, false, null, null, null, null, status);
    }

    // ========== HTTP / JSON 辅助 ==========

    private JsonNode myData(String token) {
        return okData(getWithToken("/api/admin/menus/my", token));
    }

    private JsonNode okData(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).as("请求应 200：%s", response.getBody()).isEqualTo(HttpStatus.OK);
        try {
            return objectMapper.readTree(response.getBody()).path("data");
        } catch (Exception e) {
            throw new AssertionError("解析响应失败：" + response.getBody(), e);
        }
    }

    /** 递归收集树中全部 routeName（含 directory 与各层后代）。 */
    private List<String> allRouteNames(JsonNode nodes) {
        List<String> out = new ArrayList<>();
        collectRouteNames(nodes, out);
        return out;
    }

    private void collectRouteNames(JsonNode nodes, List<String> out) {
        for (JsonNode node : nodes) {
            JsonNode routeName = node.path("routeName");
            if (!routeName.isMissingNode() && !routeName.isNull()) {
                out.add(routeName.asText());
            }
            collectRouteNames(node.path("children"), out);
        }
    }

    private List<String> stringList(JsonNode arrayNode, String field) {
        List<String> out = new ArrayList<>();
        arrayNode.forEach(node -> out.add(node.path(field).asText()));
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
