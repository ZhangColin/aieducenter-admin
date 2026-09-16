package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import org.springframework.test.context.TestPropertySource;

import com.aieducenter.admin.application.AdminUserManagementAppService;
import com.aieducenter.admin.application.RoleManagementAppService;
import com.aieducenter.admin.application.dto.command.AssignMenusCommand;
import com.aieducenter.admin.application.dto.command.AssignPermissionsCommand;
import com.aieducenter.admin.application.dto.command.AssignRolesCommand;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.application.dto.command.CreateRoleCommand;
import com.aieducenter.admin.domain.enums.AdminRoleStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;

/**
 * AI 平台菜单种子（V15）可见性与权限汇总集成断言（issue #72 T10 收口）。
 *
 * <p><b>为什么跑在独立 Flyway schema 上</b>：常规测试库 {@code flyway.enabled=false} +
 * {@code ddl-auto=create}（空表、无种子），种子可见性在常规环境不可达；本类照
 * {@code RoleAssignmentFlywaySchemaIntegrationTest} 先例在独立 schema
 * {@code aiplatform_seed_flyway} 全量跑 Flyway 链（V1–V15）+ {@code ddl-auto=none}，
 * 复刻生产 schema 后以 HTTP 外部行为断言消费面事实。与「不写种子快照测试」口径
 * （V12 教训）不冲突：不比对种子行快照，断言的是<b>运营登录即可见</b>的行为——
 * {@code /menus/my} 下发 AI 平台目录与六域子菜单（Soybean 元数据齐备）、位于支付管理
 * 之后账号管理之前（sort_order=3 与 payment 同序、id 82&gt;80 兜底排其后——spec #62 字面值）；
 * 账号读口不种页面（children 恰六叶——嵌订单/项目详情抽屉用）；非超管经角色分配（只配
 * 叶子 id，目录由祖先补全出现）+ 19 权限码（spec #62 清单）既见菜单又汇总权限；禁用角色后
 * 菜单与权限贡献均剔除（CONTEXT.md「RBAC」既定口径在新种子上不回归）。</p>
 *
 * <p>种子 ID 为迁移契约确定性常量（同破窗 ID=1 先例）：directory=82、leaves=160/170/180/190/200/210。
 * 超管用例不新建 SUPER_ADMIN code 角色（V2 已种 id=1、code 唯一），为新建运营挂种子角色。
 * HTTP 基建同 {@code MyMenusNavigationIntegrationTest}：{@code RANDOM_PORT} +
 * {@link TestRestTemplate} 走真实过滤器链；UUID 后缀保证类内用例互不冲突。</p>
 *
 * @since 0.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:25432/aieducenter_test?currentSchema=aiplatform_seed_flyway",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=aiplatform_seed_flyway",
        "spring.flyway.default-schema=aiplatform_seed_flyway",
        "spring.jpa.hibernate.ddl-auto=none"
})
class AiplatformMenuSeedVisibilityIntegrationTest {

    private static final String PASSWORD = "Test1234";

    /** V2 种子 SUPER_ADMIN 角色 id（code 唯一约束——超管用例挂种子角色而非新建）。 */
    private static final long SEEDED_SUPER_ADMIN_ROLE_ID = 1L;

    /** V15 种子确定性 ID：六域叶子（订单/项目/沙箱/成本/单价表/知识素材，sort 1-6；目录 82 由祖先补全）。 */
    private static final List<Long> AIPLATFORM_LEAF_IDS = List.of(160L, 170L, 180L, 190L, 200L, 210L);

    private static final String AIPLATFORM_ROUTE = "aiplatform";
    /** 六域叶子 route_name 顺序 = sortOrder 顺序（spec #62 验收：订单/项目/沙箱/成本/单价表/知识素材）。 */
    private static final List<String> SIX_LEAF_ROUTES = List.of(
            "aiplatform_order", "aiplatform_project", "aiplatform_workspace",
            "aiplatform_cost", "aiplatform_price_entry", "aiplatform_material");

    /** spec #62 权限码清单（19）：七读码（六域 + account）+ 十二写码（逐写操作）。 */
    private static final List<String> ALL_AIPLATFORM_PERMISSIONS = List.of(
            "admin:aiplatform:order:read",
            "admin:aiplatform:project:read",
            "admin:aiplatform:workspace:read",
            "admin:aiplatform:cost:read",
            "admin:aiplatform:price-entry:read",
            "admin:aiplatform:material:read",
            "admin:aiplatform:account:read",
            "admin:aiplatform:order:quote",
            "admin:aiplatform:order:cancel",
            "admin:aiplatform:order:retry-archive",
            "admin:aiplatform:workspace:wake",
            "admin:aiplatform:workspace:hibernate",
            "admin:aiplatform:workspace:rebuild",
            "admin:aiplatform:workspace:seal",
            "admin:aiplatform:material:disable",
            "admin:aiplatform:material:enable",
            "admin:aiplatform:material:delete",
            "admin:aiplatform:price-entry:reprice",
            "admin:aiplatform:price-entry:deactivate");

    private final AdminUserManagementAppService userAppService;
    private final RoleManagementAppService roleAppService;
    private final TestRestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int port;
    private final DataSource dataSource;

    private String suffix;

    @Autowired
    AiplatformMenuSeedVisibilityIntegrationTest(
            AdminUserManagementAppService userAppService,
            RoleManagementAppService roleAppService,
            TestRestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${local.server.port}") int port,
            DataSource dataSource) {
        this.userAppService = userAppService;
        this.roleAppService = roleAppService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.port = port;
        this.dataSource = dataSource;
    }

    @AfterAll
    void dropSchema() throws Exception {
        // 清理：删除本测试专用的独立 schema，不污染共享测试库（req7 先例）
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS aiplatform_seed_flyway CASCADE");
        }
    }

    @BeforeEach
    void setUp() {
        suffix = uuidSuffix();
    }

    @Test
    @DisplayName("超管：/menus/my 见 AI 平台目录 + 六域叶子（Soybean 元数据齐、六叶有序、无账号页面）")
    void given_superAdmin_when_my_then_aiplatformTreeVisibleWithSixLeaves() {
        // 超管不新建 SUPER_ADMIN code（V2 已种 id=1、code 唯一）——新建运营挂种子超管角色
        String superUsername = "aiplatmenu" + suffix;
        Long superUserId = userAppService.create(
                new CreateAdminUserCommand(superUsername, PASSWORD, "超管", null, null, null));
        userAppService.assignRoles(superUserId, new AssignRolesCommand(List.of(SEEDED_SUPER_ADMIN_ROLE_ID)));

        JsonNode menus = myData(login(superUsername)).path("menus");

        // 目录节点：menuType=directory(1)、路由前缀容器元数据齐（ADR-0004 Soybean 口径）
        JsonNode directory = findRootByRouteName(menus, AIPLATFORM_ROUTE);
        assertThat(directory).as("超管经 bypass 应见全树，AI 平台目录必须存在").isNotNull();
        assertThat(directory.path("menuType").asInt()).isEqualTo(1);
        assertThat(directory.path("routePath").asText()).isEqualTo("/aiplatform");
        assertThat(directory.path("component").asText()).isEqualTo("layout.base");
        assertThat(directory.path("i18nKey").asText()).isEqualTo("route.aiplatform");
        assertThat(directory.path("icon").asText()).isEqualTo("carbon:machine-learning-model");
        assertThat(directory.path("status").asInt()).isEqualTo(1);

        // 六域叶子：恰好六叶（账号读口不种页面——无第七叶）、顺序 = sortOrder 1-6、Soybean 元数据齐
        JsonNode children = directory.path("children");
        assertThat(stringList(children, "routeName"))
                .as("六域子菜单恰六叶且按 sortOrder 排序；账号读口不种页面")
                .containsExactlyElementsOf(SIX_LEAF_ROUTES);
        for (JsonNode leaf : children) {
            assertThat(leaf.path("menuType").asInt()).isEqualTo(2);
            assertThat(leaf.path("routePath").asText()).startsWith("/aiplatform/");
            assertThat(leaf.path("component").asText()).startsWith("view.aiplatform_");
            assertThat(leaf.path("i18nKey").asText()).startsWith("route.aiplatform_");
            assertThat(leaf.path("icon").asText()).isNotBlank();
            assertThat(leaf.path("iconType").asInt()).isEqualTo(1);
            assertThat(leaf.path("status").asInt()).isEqualTo(1);
            assertThat(leaf.path("sortOrder").asInt()).isBetween(1, 6);
        }

        // 侧边栏位置：应用管理之后、系统管理之前（spec #62 字面 sort_order=3——与 payment 同序，
        // id 82>80 兜底排支付管理之后；账号管理 sort=4 在其后锚定「系统管理之前」）
        List<String> rootOrder = stringList(menus, "routeName");
        int paymentIdx = rootOrder.indexOf("payment");
        int aiplatformIdx = rootOrder.indexOf(AIPLATFORM_ROUTE);
        int accountIdx = rootOrder.indexOf("account");
        assertThat(paymentIdx).as("支付管理目录种子应在侧边栏（锚定排序断言前提）").isGreaterThanOrEqualTo(0);
        assertThat(accountIdx).as("账号管理目录种子应在侧边栏（锚定排序断言前提）").isGreaterThanOrEqualTo(0);
        assertThat(aiplatformIdx).as("AI 平台目录排序：支付管理 < AI 平台 < 账号管理（sort=3/id 兜底）")
                .isGreaterThan(paymentIdx).isLessThan(accountIdx);
    }

    @Test
    @DisplayName("非超管：角色配六叶种子（祖先补全出目录）+ 19 码 → 菜单与权限均汇总可见")
    void given_roleWithSeedMenusAndAllPermissions_when_my_then_treeVisibleAndPermissionsAggregated() {
        Long roleId = roleAppService.create(
                new CreateRoleCommand("AI平台运营_" + suffix, "AIPLAMENU_" + suffix,
                        "AI 平台六域菜单 + 全量 19 权限码", 88, null));
        // 只分配叶子 id——目录经祖先链补全出现（MenuTreeAssembler 既定行为在真种子上复证）
        roleAppService.assignMenus(roleId, new AssignMenusCommand(AIPLATFORM_LEAF_IDS));
        roleAppService.assignPermissions(roleId, new AssignPermissionsCommand(ALL_AIPLATFORM_PERMISSIONS));
        String username = "aiplatop" + suffix;
        Long userId = userAppService.create(
                new CreateAdminUserCommand(username, PASSWORD, "AI 平台运营", null, null, null));
        userAppService.assignRoles(userId, new AssignRolesCommand(List.of(roleId)));
        String token = login(username);

        // 菜单：六叶 + 补全出的目录（消费面下发即运营登录可见）
        JsonNode menus = myData(token).path("menus");
        JsonNode directory = findRootByRouteName(menus, AIPLATFORM_ROUTE);
        assertThat(directory).as("分配六叶后目录应经祖先补全出现").isNotNull();
        assertThat(stringList(directory.path("children"), "routeName"))
                .containsExactlyElementsOf(SIX_LEAF_ROUTES);

        // 权限汇总：/auth/current permissions = 19 码全集（account:read 有码——配抽屉用，但无页面）
        JsonNode current = okData(getWithToken("/api/admin/auth/current", token));
        assertThat(plainStringList(current.path("permissions")))
                .containsExactlyInAnyOrderElementsOf(ALL_AIPLATFORM_PERMISSIONS);
    }

    @Test
    @DisplayName("禁用角色后：AI 平台菜单与权限贡献均剔除（既有汇总口径在新种子上不回归）")
    void given_roleDisabled_when_my_then_aiplatformContributionExcluded() {
        Long roleId = roleAppService.create(
                new CreateRoleCommand("AI平台临时_" + suffix, "AIPLATMP_" + suffix,
                        "AI 平台临时角色（将被禁用）", 89, null));
        roleAppService.assignMenus(roleId, new AssignMenusCommand(AIPLATFORM_LEAF_IDS));
        roleAppService.assignPermissions(roleId,
                new AssignPermissionsCommand(List.of("admin:aiplatform:order:read")));
        String username = "aiplattmp" + suffix;
        Long userId = userAppService.create(
                new CreateAdminUserCommand(username, PASSWORD, "临时运营", null, null, null));
        userAppService.assignRoles(userId, new AssignRolesCommand(List.of(roleId)));
        String token = login(username);

        // 基线：禁用前菜单与权限均在（否则「剔除」断言无区分度）
        assertThat(findRootByRouteName(myData(token).path("menus"), AIPLATFORM_ROUTE)).isNotNull();
        assertThat(plainStringList(okData(getWithToken("/api/admin/auth/current", token)).path("permissions")))
                .containsExactly("admin:aiplatform:order:read");

        roleAppService.updateStatus(roleId, AdminRoleStatus.DISABLED);

        // 禁用角色在任何汇总聚合中视为不存在：菜单与权限双双剔除（CONTEXT.md「RBAC」决策①）
        assertThat(findRootByRouteName(myData(token).path("menus"), AIPLATFORM_ROUTE)).isNull();
        assertThat(plainStringList(okData(getWithToken("/api/admin/auth/current", token)).path("permissions")))
                .isEmpty();
    }

    // ========== JSON 树辅助 ==========

    /** 在根层数组中按 routeName 定位节点（找不到返回 null——供「存在/剔除」两侧断言共用）。 */
    private static JsonNode findRootByRouteName(JsonNode menus, String routeName) {
        for (JsonNode node : menus) {
            if (routeName.equals(node.path("routeName").asText())) {
                return node;
            }
        }
        return null;
    }

    private List<String> stringList(JsonNode arrayNode, String field) {
        List<String> out = new ArrayList<>();
        arrayNode.forEach(node -> out.add(node.path(field).asText()));
        return out;
    }

    /** 扁平字符串数组（如 /auth/current 的 permissions）收集为 List。 */
    private List<String> plainStringList(JsonNode arrayNode) {
        List<String> out = new ArrayList<>();
        arrayNode.forEach(node -> out.add(node.asText()));
        return out;
    }

    // ========== HTTP / JSON 辅助（同 MyMenusNavigationIntegrationTest 先例） ==========

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
