package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformConversationEntryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderBriefWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderSummaryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformPriceEntryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformPrdWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectSummaryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformVersionDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformVersionWireResponse;
import com.aieducenter.admin.aiplatform.infrastructure.AiplatformClient;
import com.aieducenter.admin.application.AdminUserManagementAppService;
import com.aieducenter.admin.application.RoleManagementAppService;
import com.aieducenter.admin.application.dto.command.AssignPermissionsCommand;
import com.aieducenter.admin.application.dto.command.AssignRolesCommand;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.application.dto.command.CreateRoleCommand;
import com.cartisan.openapi.client.BinaryResponse;
import com.cartisan.openapi.client.OpenApiClientException;
import com.cartisan.web.response.PageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;

/**
 * aiplatform BFF 端点 RBAC 强制执行集成测试（issue #63 T1 账号 + #64 订单读路径 + #65 项目核心读路径）
 * ——真实 Sa-Token 过滤链，断言 {@code admin:aiplatform:account:read} / {@code admin:aiplatform:order:read} /
 * {@code admin:aiplatform:project:read} 独立权限码对未登录（401）/ 无权者（403）/ 有权者（200）的行为，
 * 并钉死北向出口形状、二进制透传与 provider 错误信封透传。
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
    private static final String ORDER_READ_PERMISSION = "admin:aiplatform:order:read";
    private static final String PROJECT_READ_PERMISSION = "admin:aiplatform:project:read";
    private static final String EXTERNAL_ID = "auth0|65f2c8a1b3d4e5f6a7b8c9d0";
    private static final String PROFILE_ENDPOINT = "/api/admin/aiplatform/accounts/" + EXTERNAL_ID;
    private static final String ORDER_ID = "3829492001234567";
    private static final String ORDERS_ENDPOINT = "/api/admin/aiplatform/orders";
    private static final String ORDER_DETAIL_ENDPOINT = ORDERS_ENDPOINT + "/" + ORDER_ID;
    private static final String SOURCE_PACKAGE_ENDPOINT = ORDER_DETAIL_ENDPOINT + "/source-package";
    private static final String PROJECT_ID = "3829492007654321";
    private static final String PROJECTS_ENDPOINT = "/api/admin/aiplatform/projects";
    private static final String PROJECT_DETAIL_ENDPOINT = PROJECTS_ENDPOINT + "/" + PROJECT_ID;
    private static final String CONVERSATION_ENDPOINT = PROJECT_DETAIL_ENDPOINT + "/conversation";
    private static final String PRD_ENDPOINT = PROJECT_DETAIL_ENDPOINT + "/prd";
    private static final String VERSIONS_ENDPOINT = PROJECT_DETAIL_ENDPOINT + "/versions";
    private static final String VERSION_HASH = "3f9c1a2b7d84e5f6a0b1c2d3e4f5a6b7c8d9e0f1";
    private static final String VERSION_DETAIL_ENDPOINT = VERSIONS_ENDPOINT + "/" + VERSION_HASH;

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
                new CreateRoleCommand("AI平台读权限_" + suffix, "AIPLAREAD_" + suffix,
                        "AI 平台账号档案 + 订单 + 项目读权限", 80, null));
        roleAppService.assignPermissions(readRoleId,
                new AssignPermissionsCommand(List.of(READ_PERMISSION, ORDER_READ_PERMISSION,
                        PROJECT_READ_PERMISSION)));

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
        when(aiplatformClient.listOrders(any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(
                List.of(new AiplatformOrderSummaryWireResponse(
                        ORDER_ID, "3829492007654321", "英语学习助手", "文野",
                        3, "已支付", 1999000L, "CNY",
                        LocalDateTime.of(2026, 9, 10, 14, 20, 0),
                        LocalDateTime.of(2026, 9, 11, 9, 0, 0))), 42, 3, 20));
        when(aiplatformClient.getOrder(eq(ORDER_ID))).thenReturn(new AiplatformOrderDetailWireResponse(
                ORDER_ID, "3829492007654321", "英语学习助手", "文野",
                2, "已报价", 2199000L, "CNY", "含加急费用",
                List.of(new AiplatformPriceEntryWireResponse(
                        "3829492011111111", 2199000L, "CNY", "含加急费用",
                        "1234567890123456", "报价运营", LocalDateTime.of(2026, 9, 12, 10, 0, 0))),
                "# PRD\n\n做一个英语学习助手……",
                LocalDateTime.of(2026, 9, 10, 14, 20, 0), LocalDateTime.of(2026, 9, 11, 9, 0, 0),
                null, null, null, null, null, null, null, null));
        when(aiplatformClient.downloadSourcePackage(eq(ORDER_ID))).thenReturn(new BinaryResponse(200,
                java.net.http.HttpHeaders.of(Map.of(
                        "Content-Type", List.of("application/gzip"),
                        "Content-Disposition", List.of("attachment; filename=\"" + ORDER_ID + "-source.tar.gz\"")),
                        (a, b) -> true),
                new byte[] {(byte) 0x1f, (byte) 0x8b, 0x08, 0x00, (byte) 0xff, 0x41, 0x00}));
        when(aiplatformClient.listProjects(any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(
                new AiplatformProjectSummaryWireResponse(
                        PROJECT_ID, "英语学习助手", "文野",
                        1, "官网", 3, "已归档", true,
                        LocalDateTime.of(2026, 8, 1, 9, 0, 0),
                        LocalDateTime.of(2026, 9, 1, 12, 0, 0))), 7, 2, 50));
        when(aiplatformClient.getProject(eq(PROJECT_ID))).thenReturn(new AiplatformProjectDetailWireResponse(
                PROJECT_ID, "英语学习助手", "文野", "3829492009999999",
                1, "官网", 1, "进行中", false,
                LocalDateTime.of(2026, 9, 10, 14, 20, 0), LocalDateTime.of(2026, 9, 14, 18, 30, 0),
                LocalDateTime.of(2026, 9, 10, 20, 0, 0), LocalDateTime.of(2026, 9, 11, 8, 0, 0),
                new AiplatformOrderBriefWireResponse(ORDER_ID, 2, "已报价"),
                new AiplatformOrderBriefWireResponse("3829492005555444", 4, "已归档"),
                new AiplatformProjectDetailWireResponse.CostSummary(Map.of("CNY", new BigDecimal("12.3456")), true)));
        when(aiplatformClient.getConversation(eq(PROJECT_ID))).thenReturn(List.of(
                new AiplatformConversationEntryWireResponse(
                        90001L, 1, "用户发言", null, "首页加一个轮播图",
                        null, null, null, false, LocalDateTime.of(2026, 9, 12, 10, 0, 0)),
                new AiplatformConversationEntryWireResponse(
                        90003L, 5, "收尾卡", "run-abc123", null,
                        null, Map.of("runId", "run-abc123", "commitHash", VERSION_HASH), null, false,
                        LocalDateTime.of(2026, 9, 12, 11, 30, 0))));
        when(aiplatformClient.getPrd(eq(PROJECT_ID))).thenReturn(new AiplatformPrdWireResponse(
                PROJECT_ID, "# PRD\n\n做一个英语学习助手……", Instant.parse("2026-09-12T08:30:00Z")));
        when(aiplatformClient.listVersions(eq(PROJECT_ID))).thenReturn(List.of(new AiplatformVersionWireResponse(
                VERSION_HASH, "轮播图上线", "run-abc123", null, LocalDateTime.of(2026, 9, 12, 11, 30, 0))));
        when(aiplatformClient.getVersion(eq(PROJECT_ID), eq(VERSION_HASH))).thenReturn(
                new AiplatformVersionDetailWireResponse(
                        VERSION_HASH, "轮播图上线", "run-abc123", null,
                        LocalDateTime.of(2026, 9, 12, 11, 30, 0),
                        Map.of("runId", "run-abc123", "commitHash", VERSION_HASH)));
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

    // ========== 订单（admin:aiplatform:order:read）· 权限三态 + 北向出口形状 ==========

    @Test
    @DisplayName("非超管且拥有 admin:aiplatform:order:read → 清单 200，分页回显 provider 值、金额 Long（分）")
    void given_nonSuperAdminWithOrderRead_when_listOrders_then_200AndPageShapeMirrorsProvider() throws Exception {
        String token = login(usernameWithReadPermission);
        // 四维过滤 + 分页 1-based 直传（page=3 无 ±1）
        ResponseEntity<String> response = getWithToken(
                ORDERS_ENDPOINT + "?status=1,5&externalId=auth0%7C65f2c8a1&page=3&size=20", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        // PageResponse 形状：items/total/page/size，回显 provider 回报值（page=3——1-based 原值）
        assertThat(data.path("total").asLong()).isEqualTo(42L);
        assertThat(data.path("page").asInt()).isEqualTo(3);
        assertThat(data.path("size").asInt()).isEqualTo(20);
        JsonNode row = data.path("items").get(0);
        assertThat(row.path("id").asText()).isEqualTo(ORDER_ID);
        assertThat(row.path("status").asInt()).isEqualTo(3);
        assertThat(row.path("statusName").asText()).isEqualTo("已支付");
        assertThat(row.path("amount").asLong()).isEqualTo(1999000L);
        assertThat(row.path("currency").asText()).isEqualTo("CNY");
        assertThat(row.path("createdAt").asText()).isEqualTo("2026-09-10T14:20:00");
    }

    @Test
    @DisplayName("非超管且拥有 order:read → 详情 200，价目历史嵌套行带操作者留痕")
    void given_nonSuperAdminWithOrderRead_when_getDetail_then_200AndPriceEntriesNested() throws Exception {
        String token = login(usernameWithReadPermission);
        ResponseEntity<String> response = getWithToken(ORDER_DETAIL_ENDPOINT, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertThat(data.path("id").asText()).isEqualTo(ORDER_ID);
        assertThat(data.path("status").asInt()).isEqualTo(2);
        assertThat(data.path("statusName").asText()).isEqualTo("已报价");
        assertThat(data.path("amount").asLong()).isEqualTo(2199000L);
        assertThat(data.path("note").asText()).isEqualTo("含加急费用");
        assertThat(data.path("prdSnapshot").asText()).startsWith("# PRD");
        JsonNode entry = data.path("priceEntries").get(0);
        assertThat(entry.path("amount").asLong()).isEqualTo(2199000L);
        assertThat(entry.path("operatorId").asText()).isEqualTo("1234567890123456");
        assertThat(entry.path("operatorName").asText()).isEqualTo("报价运营");
        // 未支付/未取消的 NULL 字段如实出 JSON null（全局 Jackson 含 null）
        assertThat(data.path("paidAt").isNull()).isTrue();
        assertThat(data.path("cancelReason").isNull()).isTrue();
    }

    @Test
    @DisplayName("非超管且拥有 order:read → 源码包 200，gzip 字节 + provider 响应头透传（无信封）")
    void given_nonSuperAdminWithOrderRead_when_downloadSourcePackage_then_200BinaryWithProviderHeaders() {
        String token = login(usernameWithReadPermission);
        ResponseEntity<byte[]> response = getBinaryWithToken(SOURCE_PACKAGE_ENDPOINT, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 无 ApiResponse 信封：body 即 tar.gz 字节流（含非法 UTF-8 字节，逐位透传）
        assertThat(response.getBody())
                .containsExactly((byte) 0x1f, (byte) 0x8b, 0x08, 0x00, (byte) 0xff, 0x41, 0x00);
        // Content-Type / Content-Disposition 取 provider 原值（attachment 文件名透传）
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/gzip");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"" + ORDER_ID + "-source.tar.gz\"");
    }

    @Test
    @DisplayName("非超管且缺少权限 → 订单三端点 403（独立权限码 order:read）")
    void given_nonSuperAdminWithoutPermission_when_orderEndpoints_then_403() {
        String token = login(usernameWithoutPermission);
        assertThat(getWithToken(ORDERS_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getWithToken(ORDER_DETAIL_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getWithToken(SOURCE_PACKAGE_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("未登录访问订单清单/源码包返回 401")
    void given_unauthenticated_when_orderEndpoints_then_401() {
        assertThat(getWithToken(ORDERS_ENDPOINT, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getWithToken(SOURCE_PACKAGE_ENDPOINT, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("订单详情下游 ORD_001 → 北向 HTTP 404 + 信封 code=5001 + message 原文（不映射）")
    void given_ord001FromDownstream_when_getDetail_then_errorEnvelopePassedThrough() throws Exception {
        when(aiplatformClient.getOrder(eq("3829499999999999"))).thenThrow(
                AiplatformUpstreamException.from(
                        new OpenApiClientException(404, "{\"code\":5001,\"message\":\"订单不存在\",\"data\":null}")));

        String token = login(usernameWithReadPermission);
        ResponseEntity<String> response = getWithToken(
                "/api/admin/aiplatform/orders/3829499999999999", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode root = objectMapper.readTree(response.getBody());
        assertThat(root.path("code").asInt()).isEqualTo(5001);
        assertThat(root.path("message").asText()).isEqualTo("订单不存在");
        assertThat(root.path("data").isNull()).isTrue();
    }

    // ========== 项目（admin:aiplatform:project:read）· 权限三态 + 北向出口形状 ==========

    @Test
    @DisplayName("非超管且拥有 admin:aiplatform:project:read → 清单 200，分页回显 provider 值、status 单选")
    void given_nonSuperAdminWithProjectRead_when_listProjects_then_200AndPageShapeMirrorsProvider() throws Exception {
        String token = login(usernameWithReadPermission);
        // 四维过滤（status 三档单选）+ 分页 1-based 直传
        ResponseEntity<String> response = getWithToken(
                PROJECTS_ENDPOINT + "?status=3&externalId=auth0%7C65f2c8a1&page=2&size=50", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        // PageResponse 形状：items/total/page/size，回显 provider 回报值（page=2——1-based 原值）
        assertThat(data.path("total").asLong()).isEqualTo(7L);
        assertThat(data.path("page").asInt()).isEqualTo(2);
        assertThat(data.path("size").asInt()).isEqualTo(50);
        JsonNode row = data.path("items").get(0);
        // 归档行照读：type/status Integer + *Name、archived 原始事实位
        assertThat(row.path("id").asText()).isEqualTo(PROJECT_ID);
        assertThat(row.path("type").asInt()).isEqualTo(1);
        assertThat(row.path("typeName").asText()).isEqualTo("官网");
        assertThat(row.path("status").asInt()).isEqualTo(3);
        assertThat(row.path("statusName").asText()).isEqualTo("已归档");
        assertThat(row.path("archived").asBoolean()).isTrue();
        assertThat(row.path("ownerDisplayName").asText()).isEqualTo("文野");
    }

    @Test
    @DisplayName("非超管且拥有 project:read → 详情 200，订单引用 + 成本指针嵌套镜像 provider")
    void given_nonSuperAdminWithProjectRead_when_getDetail_then_200WithOrderRefsAndCostSummary() throws Exception {
        String token = login(usernameWithReadPermission);
        ResponseEntity<String> response = getWithToken(PROJECT_DETAIL_ENDPOINT, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertThat(data.path("id").asText()).isEqualTo(PROJECT_ID);
        assertThat(data.path("workspaceId").asText()).isEqualTo("3829492009999999");
        assertThat(data.path("status").asInt()).isEqualTo(1);
        assertThat(data.path("statusName").asText()).isEqualTo("进行中");
        // 订单引用：activeOrder（未终结已报价——有值即冻结迭代）+ latestOrder（最近一张已归档）
        assertThat(data.path("activeOrder").path("id").asText()).isEqualTo(ORDER_ID);
        assertThat(data.path("activeOrder").path("status").asInt()).isEqualTo(2);
        assertThat(data.path("activeOrder").path("statusName").asText()).isEqualTo("已报价");
        assertThat(data.path("latestOrder").path("statusName").asText()).isEqualTo("已归档");
        // 成本指针：cost 按币种（JSON 数字）+ unpriced 标记
        assertThat(data.path("costSummary").path("cost").path("CNY").asDouble()).isEqualTo(12.3456);
        assertThat(data.path("costSummary").path("unpriced").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("非超管且拥有 project:read → 对话史/PRD/版本 200，kind+kindName 随行、载荷原样")
    void given_nonSuperAdminWithProjectRead_when_deepRead_then_200AndPayloadsMirrored() throws Exception {
        String token = login(usernameWithReadPermission);

        // 对话史：kind Integer code + kindName 中文名随行（aiplatform#186 已落——provider 出口提供）
        ResponseEntity<String> conversation = getWithToken(CONVERSATION_ENDPOINT, token);
        assertThat(conversation.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode entries = objectMapper.readTree(conversation.getBody()).path("data");
        assertThat(entries.size()).isEqualTo(2);
        assertThat(entries.get(0).path("kind").asInt()).isEqualTo(1);
        assertThat(entries.get(0).path("kindName").asText()).isEqualTo("用户发言");
        assertThat(entries.get(1).path("closing").path("commitHash").asText()).isEqualTo(VERSION_HASH);

        // PRD：markdown 正文 + updatedAt（Instant，ISO-8601 UTC 带 Z）
        ResponseEntity<String> prd = getWithToken(PRD_ENDPOINT, token);
        assertThat(prd.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode prdData = objectMapper.readTree(prd.getBody()).path("data");
        assertThat(prdData.path("content").asText()).startsWith("# PRD");
        assertThat(prdData.path("updatedAt").asText()).isEqualTo("2026-09-12T08:30:00Z");

        // 版本列表（新→旧）+ 版本详情（ref＝hex 40 位，closing 载荷原样）
        ResponseEntity<String> versions = getWithToken(VERSIONS_ENDPOINT, token);
        assertThat(versions.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(versions.getBody()).path("data").size()).isEqualTo(1);
        ResponseEntity<String> versionDetail = getWithToken(VERSION_DETAIL_ENDPOINT, token);
        assertThat(versionDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode versionData = objectMapper.readTree(versionDetail.getBody()).path("data");
        assertThat(versionData.path("commitHash").asText()).isEqualTo(VERSION_HASH);
        assertThat(versionData.path("closing").path("runId").asText()).isEqualTo("run-abc123");
    }

    @Test
    @DisplayName("非超管且缺少权限 → 项目六端点 403（独立权限码 project:read，深读三组同码）")
    void given_nonSuperAdminWithoutPermission_when_projectEndpoints_then_403() {
        String token = login(usernameWithoutPermission);
        assertThat(getWithToken(PROJECTS_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getWithToken(PROJECT_DETAIL_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getWithToken(CONVERSATION_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getWithToken(PRD_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getWithToken(VERSIONS_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getWithToken(VERSION_DETAIL_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("未登录访问项目六端点返回 401")
    void given_unauthenticated_when_projectEndpoints_then_401() {
        assertThat(getWithToken(PROJECTS_ENDPOINT, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getWithToken(PROJECT_DETAIL_ENDPOINT, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getWithToken(CONVERSATION_ENDPOINT, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getWithToken(PRD_ENDPOINT, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getWithToken(VERSIONS_ENDPOINT, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getWithToken(VERSION_DETAIL_ENDPOINT, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("项目详情下游 PRJ_001 → 北向 HTTP 404 + 信封 code=4001 + message 原文（不映射）")
    void given_prj001FromDownstream_when_getProjectDetail_then_errorEnvelopePassedThrough() throws Exception {
        when(aiplatformClient.getProject(eq("3829499999999999"))).thenThrow(
                AiplatformUpstreamException.from(
                        new OpenApiClientException(404, "{\"code\":4001,\"message\":\"项目不存在\",\"data\":null}")));

        String token = login(usernameWithReadPermission);
        ResponseEntity<String> response = getWithToken(PROJECTS_ENDPOINT + "/3829499999999999", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode root = objectMapper.readTree(response.getBody());
        // 信封 code＝数字业务码 4001（PRJ_001），而非映射后的 404——前端比对 aiplatform 业务码的分支活
        assertThat(root.path("code").asInt()).isEqualTo(4001);
        assertThat(root.path("message").asText()).isEqualTo("项目不存在");
        assertThat(root.path("data").isNull()).isTrue();
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

    /** 二进制端点取回（byte[] body——不把 gzip 字节流经 String 解码，透传断言的前提）。 */
    private ResponseEntity<byte[]> getBinaryWithToken(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            SaTokenConfig cfg = SaManager.getConfig();
            String value = (cfg.getTokenPrefix() == null || cfg.getTokenPrefix().isEmpty())
                    ? token
                    : cfg.getTokenPrefix() + " " + token;
            headers.set(cfg.getTokenName(), value);
        }
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String uuidSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
