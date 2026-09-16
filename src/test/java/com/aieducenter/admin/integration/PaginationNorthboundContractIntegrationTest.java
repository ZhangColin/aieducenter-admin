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
 * 北向分页契约集成测试（issue #73 / ADR-0012）——以 {@code GET /api/admin/roles}（自有域，框架
 * {@code Pagination} 绑定的代表端点）HTTP 外部行为钉死全链 1-based 的北向半边：
 *
 * <ul>
 *   <li>{@code page=1} 返回首页（第 2 页不再含首页元素）且回显 {@code page=1}</li>
 *   <li>{@code page=0} 静默贴边为 1（框架 {@code Pagination} 构造期行为，钉住防回归）</li>
 *   <li>{@code size} 缺省 20</li>
 *   <li>{@code size} 超 100 贴边 100</li>
 * </ul>
 *
 * <p>出站半边（wire page 与北向同值直传）由各 *Client wire 契约测试钉死
 * （PaymentClientListEnvelopeContractTest 四端点 / AccountClientListEnvelopeContractTest /
 * AppRegistryClientListEnvelopeContractTest）；BFF 域北向绑定与自有域同款框架 {@code Pagination}，
 * 不重复钉。aiplatform 域为既有 {@code @RequestParam(defaultValue = "1")} 形态（wire 等价，ADR-0012 记差异）。</p>
 *
 * <p>仿 {@link RoleManagementSoybeanAlignmentIntegrationTest}：真库 + 真 Spring + 登录 token。
 * 排序用角色列表默认序（sortOrder 升序 + id 升序兜底，issue #19）保证页边界确定。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaginationNorthboundContractIntegrationTest {

    private static final String PASSWORD = "Test1234";

    private final RoleManagementAppService roleAppService;
    private final AdminUserManagementAppService userAppService;
    private final AdminRoleRepository adminRoleRepository;
    private final AdminUserRepository adminUserRepository;
    private final TestRestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int port;

    private String callerUsername;
    private String prefix;
    private Long role10;
    private Long role20;
    private Long role30;

    @Autowired
    PaginationNorthboundContractIntegrationTest(
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
        // 调用者：超管（bypass 权限检查，读列表需 admin:role:read）
        Long superAdminRoleId = adminRoleRepository.findByCode(AdminRole.SUPER_ADMIN_CODE)
                .map(AdminRole::getId)
                .orElseGet(() -> roleAppService.create(
                        new CreateRoleCommand("超级管理员", AdminRole.SUPER_ADMIN_CODE, "超级管理员", 0, null)));
        callerUsername = "pagecaller";
        if (!adminUserRepository.existsByUsername(callerUsername)) {
            Long callerId = userAppService.create(
                    new CreateAdminUserCommand(callerUsername, PASSWORD, "分页契约调用者", null, null, null));
            userAppService.assignRoles(callerId, new AssignRolesCommand(List.of(superAdminRoleId)));
        }

        // 3 个专属角色，sortOrder 互异（30/20/10）→ 默认序下页边界确定：第 1 页(size2)= [SUPER_ADMIN(0), x10]，
        // 第 2 页 = [x20, x30]
        prefix = "PAGE" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String token = login(callerUsername);
        role30 = createRole(token, "分页30_" + prefix, prefix + "_30", 30);
        role20 = createRole(token, "分页20_" + prefix, prefix + "_20", 20);
        role10 = createRole(token, "分页10_" + prefix, prefix + "_10", 10);
    }

    @Test
    @DisplayName("page=1 返回首页且回显 page=1（?code=<prefix> 隔离出恰好 3 行，页边界确定）")
    void given_roles_when_page1_then_firstPageAndEcho1() throws Exception {
        String token = login(callerUsername);

        ResponseEntity<String> response =
                withToken(HttpMethod.GET, "/api/admin/roles?code=" + prefix + "&page=1&size=2", token, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertThat(data.path("page").asInt()).as("回显 page=1").isEqualTo(1);
        assertThat(data.path("size").asInt()).isEqualTo(2);
        assertThat(data.path("total").asLong()).isEqualTo(3L);
        // 首页证据：默认序（sortOrder 升序）下第 1 页恰为 [x10, x20]，不含 x30
        List<Long> mine = filteredIds(data.path("items"), prefix);
        assertThat(mine).containsExactly(role10, role20);
    }

    @Test
    @DisplayName("page=2 回显 page=2 且恰为第 2 页（1-based 第 2 页 ≠ 0-based 的第 3 页）")
    void given_roles_when_page2_then_secondPageAndEcho2() throws Exception {
        String token = login(callerUsername);

        ResponseEntity<String> response =
                withToken(HttpMethod.GET, "/api/admin/roles?code=" + prefix + "&page=2&size=2", token, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertThat(data.path("page").asInt()).isEqualTo(2);
        // 第 2 页恰为尾元素 x30（若 1-based 被误当 0-based 处理，page=2 会越过末页成空页）
        List<Long> mine = filteredIds(data.path("items"), prefix);
        assertThat(mine).containsExactly(role30);
    }

    @Test
    @DisplayName("page=0 静默贴边为 1（框架 Pagination 构造期行为，ADR-0012）")
    void given_pageZero_when_list_then_clampedToFirstPage() throws Exception {
        String token = login(callerUsername);

        ResponseEntity<String> response =
                withToken(HttpMethod.GET, "/api/admin/roles?code=" + prefix + "&page=0&size=2", token, null);

        assertThat(response.getStatusCode()).as("page<1 贴边不报错").isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertThat(data.path("page").asInt()).as("回显贴边后的 page=1").isEqualTo(1);
        // 与显式 page=1 同页（首页内容一致）
        List<Long> mine = filteredIds(data.path("items"), prefix);
        assertThat(mine).containsExactly(role10, role20);
    }

    @Test
    @DisplayName("size 缺省 20")
    void given_noSize_when_list_then_defaultSize20() throws Exception {
        String token = login(callerUsername);

        ResponseEntity<String> response =
                withToken(HttpMethod.GET, "/api/admin/roles?code=" + prefix + "&page=1", token, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertThat(data.path("size").asInt()).isEqualTo(20);
        assertThat(data.path("page").asInt()).isEqualTo(1);
        // 3 行 < 20：单页收全，顺序为默认序 x10 → x20 → x30
        assertThat(filteredIds(data.path("items"), prefix)).containsExactly(role10, role20, role30);
    }

    @Test
    @DisplayName("size 超 100 贴边 100")
    void given_sizeOver100_when_list_then_clampedTo100() throws Exception {
        String token = login(callerUsername);

        ResponseEntity<String> response =
                withToken(HttpMethod.GET, "/api/admin/roles?code=" + prefix + "&page=1&size=500", token, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertThat(data.path("size").asInt()).isEqualTo(100);
        assertThat(data.path("page").asInt()).isEqualTo(1);
        // 仅 3 行数据，贴边后 items 不越界（≤ total）
        assertThat(data.path("items").size()).isLessThanOrEqualTo((int) data.path("total").asLong());
    }

    // ========== helpers（仿 RoleManagementSoybeanAlignmentIntegrationTest）==========

    private Long createRole(String token, String name, String code, int sortOrder) {
        ResponseEntity<String> response = withToken(HttpMethod.POST, "/api/admin/roles", token,
                "{\"name\":\"" + name + "\",\"code\":\"" + code + "\",\"description\":null,"
                        + "\"sortOrder\":" + sortOrder + ",\"home\":null}");
        assertThat(response.getStatusCode()).as("创建角色应 200：%s", response.getBody()).isEqualTo(HttpStatus.OK);
        try {
            return objectMapper.readTree(response.getBody()).path("data").asLong();
        } catch (Exception e) {
            throw new AssertionError("解析角色 id 失败：" + response.getBody(), e);
        }
    }

    /** 在 items 数组中按 code 前缀过滤，保留响应顺序返回 id 列表。 */
    private List<Long> filteredIds(JsonNode items, String codePrefix) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : items) {
            if (item.path("code").asText().startsWith(codePrefix)) {
                ids.add(item.path("id").asLong());
            }
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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
