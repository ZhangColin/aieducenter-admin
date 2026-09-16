package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformCostOverviewWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformCostWindowWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformConversationEntryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformMaterialDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformMaterialSummaryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderBriefWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderSummaryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformPriceEntryRepriceWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformPriceEntryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformPrdWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectCostDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectCostWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectSummaryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformTokenUsageWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformUnitPriceEntryRepriceWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformUnitPriceEntryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformUnpricedUsageWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformVersionDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformVersionWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformWorkspaceDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformWorkspaceSummaryWireResponse;
import com.aieducenter.admin.aiplatform.infrastructure.AiplatformClient;
import com.aieducenter.admin.application.AdminUserManagementAppService;
import com.aieducenter.admin.application.RoleManagementAppService;
import com.aieducenter.admin.application.dto.command.AssignPermissionsCommand;
import com.aieducenter.admin.application.dto.command.AssignRolesCommand;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.application.dto.command.CreateRoleCommand;
import com.cartisan.core.context.RequestContext;
import com.cartisan.openapi.client.BinaryResponse;
import com.cartisan.openapi.client.OpenApiClientException;
import com.cartisan.web.response.PageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;

/**
 * aiplatform BFF 端点 RBAC 强制执行集成测试（issue #63 T1 账号 + #64 订单读路径 + #65 项目核心读路径 +
 * #66 沙箱观测与四干预动作 + #67 成本四读口 + #68 单价表清单/原子改价/停用 + #69 知识素材
 * 清单/详情/停用⇄启用/删除）
 * ——真实 Sa-Token 过滤链，断言 {@code admin:aiplatform:account:read} / {@code admin:aiplatform:order:read} /
 * {@code admin:aiplatform:project:read} / {@code admin:aiplatform:workspace:read} /
 * {@code admin:aiplatform:cost:read} / {@code admin:aiplatform:price-entry:read} /
 * {@code admin:aiplatform:material:read} + 沙箱四独立写码
 * （{@code workspace:wake|hibernate|rebuild|seal}）+ 单价表两独立写码
 * （{@code price-entry:reprice|deactivate}）+ 知识素材三独立写码
 * （{@code material:disable|enable|delete}）对未登录（401）/ 无权者（403）/ 有权者（200）的行为，
 * 并钉死北向出口形状、二进制透传与 provider 错误信封透传。成本域另钉死时间窗 from/to 北向必填
 * （缺参 400 / 非 Instant 404 均在本服务绑定层裁决、不到 provider，issue #67：不设默认窗口）。
 *
 * <p>镜像 {@code AccountRbacEnforcementIntegrationTest}（登录/鉴权辅助沿用
 * {@code RbacEnforcementIntegrationTest}）。200 用例 mock {@link AiplatformClient}，证明权限放行后
 * 整条 controller→appservice→client 通路接通。超管 bypass 由框架级测试钉住，此处不重复。</p>
 *
 * <p><strong>操作者身份透传契约</strong>（issue #66 断言必带；#69 素材域同款必带）：沙箱四干预动作、
 * 单价表改价/停用、素材停用/启用从 {@code RequestContext} 读 operator 审计，admin 不在 body 塞
 * 身份——框架 cartisan-openapi 自动从 {@code RequestContext} 带 {@code X-User-Id/X-User-Name}
 * 出站 header。本测试在 mocked {@link AiplatformClient} 边界用 {@code doAnswer} 在调用瞬间抓取
 * {@link RequestContext#getUserId()} / {@link RequestContext#getUserName()}，断言其 == 登录治理
 * 运营的 id / 昵称（{@code AccountRbacEnforcementIntegrationTest} 同款手法）。素材域该头是
 * provider 必拦项（缺头 400 KNW_006——治理动作必留痕），透传断言即「北向永缺不了头」的证据。</p>
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
    private static final String WORKSPACE_READ_PERMISSION = "admin:aiplatform:workspace:read";
    private static final String WORKSPACE_WAKE_PERMISSION = "admin:aiplatform:workspace:wake";
    private static final String WORKSPACE_HIBERNATE_PERMISSION = "admin:aiplatform:workspace:hibernate";
    private static final String WORKSPACE_REBUILD_PERMISSION = "admin:aiplatform:workspace:rebuild";
    private static final String WORKSPACE_SEAL_PERMISSION = "admin:aiplatform:workspace:seal";
    private static final String WORKSPACE_ID = "3829492007777777";
    private static final String WORKSPACES_ENDPOINT = "/api/admin/aiplatform/workspaces";
    private static final String WORKSPACE_DETAIL_ENDPOINT = WORKSPACES_ENDPOINT + "/" + WORKSPACE_ID;
    private static final String WAKE_ENDPOINT = WORKSPACE_DETAIL_ENDPOINT + "/wake";
    private static final String HIBERNATE_ENDPOINT = WORKSPACE_DETAIL_ENDPOINT + "/hibernate";
    private static final String REBUILD_ENDPOINT = WORKSPACE_DETAIL_ENDPOINT + "/rebuild";
    private static final String SEAL_ENDPOINT = WORKSPACE_DETAIL_ENDPOINT + "/seal";
    private static final String COST_READ_PERMISSION = "admin:aiplatform:cost:read";
    private static final String COSTS_ENDPOINT = "/api/admin/aiplatform/costs";
    private static final String COST_FROM = "2026-09-01T00:00:00Z";
    private static final String COST_TO = "2026-09-16T00:00:00Z";
    private static final String COST_WINDOW_QUERY = "?from=" + COST_FROM + "&to=" + COST_TO;
    private static final String PRICE_ENTRY_READ_PERMISSION = "admin:aiplatform:price-entry:read";
    private static final String PRICE_ENTRY_REPRICE_PERMISSION = "admin:aiplatform:price-entry:reprice";
    private static final String PRICE_ENTRY_DEACTIVATE_PERMISSION = "admin:aiplatform:price-entry:deactivate";
    private static final String PRICE_ENTRY_ID = "3830100002222222";
    private static final String PRICE_ENTRIES_ENDPOINT = "/api/admin/aiplatform/price-entries";
    private static final String REPRICE_ENDPOINT = PRICE_ENTRIES_ENDPOINT + "/" + PRICE_ENTRY_ID + "/reprice";
    private static final String DEACTIVATE_ENDPOINT = PRICE_ENTRIES_ENDPOINT + "/" + PRICE_ENTRY_ID + "/deactivate";
    private static final String REPRICE_BODY =
            "{\"unitPrice\":0.0000018,\"currency\":\"USD\",\"effectiveFrom\":\"2026-09-20T00:00:00Z\"}";
    private static final String MATERIAL_READ_PERMISSION = "admin:aiplatform:material:read";
    private static final String MATERIAL_DISABLE_PERMISSION = "admin:aiplatform:material:disable";
    private static final String MATERIAL_ENABLE_PERMISSION = "admin:aiplatform:material:enable";
    private static final String MATERIAL_DELETE_PERMISSION = "admin:aiplatform:material:delete";
    private static final String MATERIAL_ID = "3840600001111111";
    private static final String MATERIALS_ENDPOINT = "/api/admin/aiplatform/materials";
    private static final String MATERIAL_DETAIL_ENDPOINT = MATERIALS_ENDPOINT + "/" + MATERIAL_ID;
    private static final String MATERIAL_DISABLE_ENDPOINT = MATERIAL_DETAIL_ENDPOINT + "/disable";
    private static final String MATERIAL_ENABLE_ENDPOINT = MATERIAL_DETAIL_ENDPOINT + "/enable";
    private static final String MATERIAL_DELETE_ENDPOINT = MATERIAL_DETAIL_ENDPOINT;

    private final AdminUserManagementAppService userAppService;
    private final RoleManagementAppService roleAppService;
    private final TestRestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int port;

    @MockBean
    private AiplatformClient aiplatformClient;

    private String usernameWithReadPermission;
    private String usernameWithoutPermission;
    private String usernameWithWorkspaceWrite;
    private String usernameWithHibernateOnly;
    private String usernameWithPriceWrite;
    private String usernameWithRepriceOnly;
    private String usernameWithMaterialWrite;
    private String usernameWithDisableOnly;

    // 在 mocked client 调用瞬间抓取 RequestContext——证明 operator 身份抵达出站调用点
    // （供 OpenApiClient 带 X-User-Id/X-User-Name 出站头，issue #66/#69 断言必带）
    private final AtomicReference<Long> capturedOperatorId = new AtomicReference<>();
    private final AtomicReference<String> capturedOperatorName = new AtomicReference<>();
    private Long workspaceWriteUserId;
    private String workspaceWriteUserNickname;
    private Long priceWriteUserId;
    private String priceWriteUserNickname;
    private Long materialWriteUserId;
    private String materialWriteUserNickname;

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
        capturedOperatorId.set(null);
        capturedOperatorName.set(null);

        String suffix = uuidSuffix();
        usernameWithReadPermission = "aiplaread" + suffix;
        usernameWithoutPermission = "aiplanone" + suffix;
        usernameWithWorkspaceWrite = "aiplawspr" + suffix;
        usernameWithHibernateOnly = "aiplahibr" + suffix;
        usernameWithPriceWrite = "aiplapwpr" + suffix;
        usernameWithRepriceOnly = "aiplarpon" + suffix;
        usernameWithMaterialWrite = "aiplamwpr" + suffix;
        usernameWithDisableOnly = "aipladson" + suffix;

        Long readRoleId = roleAppService.create(
                new CreateRoleCommand("AI平台读权限_" + suffix, "AIPLAREAD_" + suffix,
                        "AI 平台账号档案 + 订单 + 项目 + 沙箱 + 成本 + 单价表 + 知识素材读权限", 80, null));
        roleAppService.assignPermissions(readRoleId,
                new AssignPermissionsCommand(List.of(READ_PERMISSION, ORDER_READ_PERMISSION,
                        PROJECT_READ_PERMISSION, WORKSPACE_READ_PERMISSION, COST_READ_PERMISSION,
                        PRICE_ENTRY_READ_PERMISSION, MATERIAL_READ_PERMISSION)));

        Long userWithReadId = userAppService.create(
                new CreateAdminUserCommand(usernameWithReadPermission, PASSWORD, "只读运营_" + suffix, null, null, null));
        userAppService.assignRoles(userWithReadId, new AssignRolesCommand(List.of(readRoleId)));

        userAppService.create(
                new CreateAdminUserCommand(usernameWithoutPermission, PASSWORD, "无权限运营_" + suffix, null, null, null));

        // 沙箱四写码专用运营（全四写码，无读码）——操作者透传断言用其 id/昵称
        Long workspaceWriteRoleId = roleAppService.create(
                new CreateRoleCommand("AI平台沙箱写操作_" + suffix, "AIPLAWSWR_" + suffix,
                        "AI 平台沙箱四干预动作权限", 90, null));
        roleAppService.assignPermissions(workspaceWriteRoleId, new AssignPermissionsCommand(List.of(
                WORKSPACE_WAKE_PERMISSION, WORKSPACE_HIBERNATE_PERMISSION,
                WORKSPACE_REBUILD_PERMISSION, WORKSPACE_SEAL_PERMISSION)));
        workspaceWriteUserNickname = "沙箱治理运营_" + suffix;
        workspaceWriteUserId = userAppService.create(
                new CreateAdminUserCommand(usernameWithWorkspaceWrite, PASSWORD,
                        workspaceWriteUserNickname, null, null, null));
        userAppService.assignRoles(workspaceWriteUserId, new AssignRolesCommand(List.of(workspaceWriteRoleId)));

        // 仅 hibernate 单写码——钉死四写码彼此独立（spec #62 最小授权）
        Long hibernateOnlyRoleId = roleAppService.create(
                new CreateRoleCommand("AI平台沙箱休眠_" + suffix, "AIPLAHIBR_" + suffix,
                        "AI 平台沙箱仅强制休眠权限", 95, null));
        roleAppService.assignPermissions(hibernateOnlyRoleId,
                new AssignPermissionsCommand(List.of(WORKSPACE_HIBERNATE_PERMISSION)));
        Long hibernateOnlyUserId = userAppService.create(
                new CreateAdminUserCommand(usernameWithHibernateOnly, PASSWORD,
                        "休眠专员_" + suffix, null, null, null));
        userAppService.assignRoles(hibernateOnlyUserId, new AssignRolesCommand(List.of(hibernateOnlyRoleId)));

        // 单价表双写码专用运营（reprice+deactivate，无读码）——操作者透传断言用其 id/昵称
        Long priceWriteRoleId = roleAppService.create(
                new CreateRoleCommand("AI平台单价表写操作_" + suffix, "AIPLAPWPR_" + suffix,
                        "AI 平台单价表改价 + 停用权限", 96, null));
        roleAppService.assignPermissions(priceWriteRoleId, new AssignPermissionsCommand(List.of(
                PRICE_ENTRY_REPRICE_PERMISSION, PRICE_ENTRY_DEACTIVATE_PERMISSION)));
        priceWriteUserNickname = "调价运营_" + suffix;
        priceWriteUserId = userAppService.create(
                new CreateAdminUserCommand(usernameWithPriceWrite, PASSWORD,
                        priceWriteUserNickname, null, null, null));
        userAppService.assignRoles(priceWriteUserId, new AssignRolesCommand(List.of(priceWriteRoleId)));

        // 仅 reprice 单写码——钉死改价/停用两写码彼此独立（spec #62 最小授权）
        Long repriceOnlyRoleId = roleAppService.create(
                new CreateRoleCommand("AI平台单价表改价_" + suffix, "AIPLARPON_" + suffix,
                        "AI 平台单价表仅改价权限", 97, null));
        roleAppService.assignPermissions(repriceOnlyRoleId,
                new AssignPermissionsCommand(List.of(PRICE_ENTRY_REPRICE_PERMISSION)));
        Long repriceOnlyUserId = userAppService.create(
                new CreateAdminUserCommand(usernameWithRepriceOnly, PASSWORD,
                        "调价专员_" + suffix, null, null, null));
        userAppService.assignRoles(repriceOnlyUserId, new AssignRolesCommand(List.of(repriceOnlyRoleId)));

        // 知识素材三写码专用运营（disable+enable+delete，无读码）——操作者透传断言用其 id/昵称
        Long materialWriteRoleId = roleAppService.create(
                new CreateRoleCommand("AI平台知识素材写操作_" + suffix, "AIPLAMWPR_" + suffix,
                        "AI 平台知识素材停用 + 启用 + 删除权限", 98, null));
        roleAppService.assignPermissions(materialWriteRoleId, new AssignPermissionsCommand(List.of(
                MATERIAL_DISABLE_PERMISSION, MATERIAL_ENABLE_PERMISSION, MATERIAL_DELETE_PERMISSION)));
        materialWriteUserNickname = "内容治理运营_" + suffix;
        materialWriteUserId = userAppService.create(
                new CreateAdminUserCommand(usernameWithMaterialWrite, PASSWORD,
                        materialWriteUserNickname, null, null, null));
        userAppService.assignRoles(materialWriteUserId, new AssignRolesCommand(List.of(materialWriteRoleId)));

        // 仅 disable 单写码——钉死三写码彼此独立（spec #62 最小授权：可逆治理与不可逆删除分权）
        Long disableOnlyRoleId = roleAppService.create(
                new CreateRoleCommand("AI平台知识素材停用_" + suffix, "AIPLADSON_" + suffix,
                        "AI 平台知识素材仅停用权限", 99, null));
        roleAppService.assignPermissions(disableOnlyRoleId,
                new AssignPermissionsCommand(List.of(MATERIAL_DISABLE_PERMISSION)));
        Long disableOnlyUserId = userAppService.create(
                new CreateAdminUserCommand(usernameWithDisableOnly, PASSWORD,
                        "素材停用专员_" + suffix, null, null, null));
        userAppService.assignRoles(disableOnlyUserId, new AssignRolesCommand(List.of(disableOnlyRoleId)));

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
        // 沙箱观测面 mock：漂移行（期望运行而实态无容器）+ 封存态详情（资源观测 + 项目引用）
        when(aiplatformClient.listWorkspaces(any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(
                new AiplatformWorkspaceSummaryWireResponse(
                        WORKSPACE_ID, "ws-" + WORKSPACE_ID,
                        1, "开发", 2, "就绪", 1, "运行", 3, "无容器",
                        LocalDateTime.of(2026, 9, 14, 22, 10, 0),
                        2147483648L, null, null,
                        new AiplatformWorkspaceSummaryWireResponse.ProjectRef(
                                PROJECT_ID, "英语学习助手", false))), 17, 2, 20));
        when(aiplatformClient.getWorkspace(eq(WORKSPACE_ID))).thenReturn(
                workspaceActionReceiptWire(3, "封存", 3, "无容器",
                        "workspace-archives/" + WORKSPACE_ID + ".tar.gz", 89128960L));
        // 四干预动作 mock：回执详情 + 抓取 RequestContext（操作者透传契约证据——issue #66 断言必带）
        Runnable capture = () -> {
            capturedOperatorId.set(RequestContext.getUserId());
            capturedOperatorName.set(RequestContext.getUserName());
        };
        doAnswer(inv -> {
            capture.run();
            return workspaceActionReceiptWire(1, "运行", 1, "运行中", null, null);
        }).when(aiplatformClient).wakeWorkspace(eq(WORKSPACE_ID));
        doAnswer(inv -> {
            capture.run();
            return workspaceActionReceiptWire(2, "休眠", 3, "无容器", null, null);
        }).when(aiplatformClient).hibernateWorkspace(eq(WORKSPACE_ID));
        doAnswer(inv -> {
            capture.run();
            return workspaceActionReceiptWire(1, "运行", 1, "运行中", null, null);
        }).when(aiplatformClient).rebuildWorkspace(eq(WORKSPACE_ID));
        doAnswer(inv -> {
            capture.run();
            return workspaceActionReceiptWire(3, "封存", 3, "无容器",
                    "workspace-archives/" + WORKSPACE_ID + ".tar.gz", 89128960L);
        }).when(aiplatformClient).sealWorkspace(eq(WORKSPACE_ID));
        // 成本四读口 mock：总览（五档总量 + 币种分桶 + 双分解）/ unpriced 警示 / 项目成本清单 / 单项目下钻
        Instant costFrom = Instant.parse(COST_FROM);
        Instant costTo = Instant.parse(COST_TO);
        when(aiplatformClient.getCostOverview(eq(new AiplatformCostWindowWireRequest(costFrom, costTo))))
                .thenReturn(new AiplatformCostOverviewWireResponse(
                        costFrom, costTo,
                        new AiplatformTokenUsageWireResponse(5000, 1200, 300, 0, 800),
                        Map.of("CNY", new BigDecimal("12.3456")),
                        List.of(new AiplatformCostOverviewWireResponse.ModelUsage(
                                "anthropic", "claude-fable-5",
                                new AiplatformTokenUsageWireResponse(3000, 1000, 300, 0, 800))),
                        List.of(new AiplatformCostOverviewWireResponse.AgentKindUsage(
                                "naming", null,
                                new AiplatformTokenUsageWireResponse(1000, 200, 0, 0, 0)))));
        when(aiplatformClient.getUnpricedUsage(eq(new AiplatformCostWindowWireRequest(costFrom, costTo))))
                .thenReturn(new AiplatformUnpricedUsageWireResponse(
                        costFrom, costTo,
                        List.of(new AiplatformUnpricedUsageWireResponse.UnpricedTier(
                                "openai", "gpt-5.2", 1, "输入", 700))));
        when(aiplatformClient.listProjectCosts(any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(
                new AiplatformProjectCostWireResponse(
                        PROJECT_ID,
                        new AiplatformTokenUsageWireResponse(3000, 1000, 300, 0, 800),
                        Map.of("CNY", new BigDecimal("12.3456")), false),
                new AiplatformProjectCostWireResponse(
                        "3829492005555444",
                        new AiplatformTokenUsageWireResponse(1000, 200, 0, 0, 0),
                        Map.of(), true)), 2, 1, 20));
        when(aiplatformClient.getProjectCostDetail(eq(PROJECT_ID),
                eq(new AiplatformCostWindowWireRequest(costFrom, costTo))))
                .thenReturn(new AiplatformProjectCostDetailWireResponse(
                        PROJECT_ID, costFrom, costTo,
                        new AiplatformTokenUsageWireResponse(3000, 1000, 300, 0, 800),
                        Map.of("CNY", new BigDecimal("12.3456")),
                        List.of(new AiplatformProjectCostDetailWireResponse.UnpricedTier(
                                "openai", "gpt-5.2", 1, "输入")),
                        List.of(new AiplatformProjectCostDetailWireResponse.ModelUsage(
                                "anthropic", "claude-fable-5",
                                new AiplatformTokenUsageWireResponse(3000, 1000, 300, 0, 800))),
                        List.of(new AiplatformProjectCostDetailWireResponse.AgentKindUsage(
                                "executor", "执行智能体",
                                new AiplatformTokenUsageWireResponse(3000, 1000, 300, 0, 800)))));
        // 单价表域 mock：清单（当前行 + 历史行）/ 改价双行回执（抓取 RequestContext——操作者透传
        // 契约证据，同沙箱四动作）/ 停用单行回执
        when(aiplatformClient.listPriceEntries(any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(
                new AiplatformUnitPriceEntryWireResponse(
                        PRICE_ENTRY_ID, "anthropic", "claude-fable-5", 1, "输入",
                        "0.000002", "USD",
                        Instant.parse("2026-08-01T00:00:00Z"), null, "700160", "运营·单价管理员"),
                new AiplatformUnitPriceEntryWireResponse(
                        "3830100001111111", "anthropic", "claude-fable-5", 1, "输入",
                        "0.00000132", "USD",
                        Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"),
                        null, null)), 12, 2, 20));
        doAnswer(inv -> {
            capture.run();
            return new AiplatformUnitPriceEntryRepriceWireResponse(
                    new AiplatformUnitPriceEntryWireResponse(
                            PRICE_ENTRY_ID, "anthropic", "claude-fable-5", 1, "输入",
                            "0.000002", "USD",
                            Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-20T00:00:00Z"),
                            "700160", "运营·单价管理员"),
                    new AiplatformUnitPriceEntryWireResponse(
                            "3830100003333333", "anthropic", "claude-fable-5", 1, "输入",
                            "0.0000018", "USD",
                            Instant.parse("2026-09-20T00:00:00Z"), null,
                            String.valueOf(priceWriteUserId), priceWriteUserNickname));
        }).when(aiplatformClient).repricePriceEntry(eq(PRICE_ENTRY_ID), any(AiplatformPriceEntryRepriceWireRequest.class));
        doAnswer(inv -> {
            capture.run();
            return new AiplatformUnitPriceEntryWireResponse(
                    PRICE_ENTRY_ID, "anthropic", "claude-fable-5", 1, "输入",
                    "0.000002", "USD",
                    Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-16T10:30:00Z"),
                    String.valueOf(priceWriteUserId), priceWriteUserNickname);
        }).when(aiplatformClient).deactivatePriceEntry(eq(PRICE_ENTRY_ID));
        // 知识素材域 mock：清单（已治理行 + 未治理行）/ 详情（PRD 全文）/ 三治理动作回执（抓取
        // RequestContext——操作者透传契约证据，provider 缺头必拦 KNW_006，issue #69 断言必带）
        when(aiplatformClient.listMaterials(any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(
                new AiplatformMaterialSummaryWireResponse(
                        MATERIAL_ID, "PRD", PROJECT_ID, "英语学习助手", "英语学习助手 · PRD",
                        2, "停用", Instant.parse("2026-09-01T08:30:00Z"),
                        String.valueOf(materialWriteUserId), materialWriteUserNickname),
                new AiplatformMaterialSummaryWireResponse(
                        "3840600002222222", "PRD", "3829492005555444", "待办清单应用",
                        "待办清单应用 · PRD", 1, "启用", Instant.parse("2026-08-20T12:00:00Z"),
                        null, null)), 9, 2, 20));
        when(aiplatformClient.getMaterial(eq(MATERIAL_ID))).thenReturn(new AiplatformMaterialDetailWireResponse(
                MATERIAL_ID, "PRD", PROJECT_ID, "英语学习助手", "英语学习助手 · PRD",
                1, "启用", Instant.parse("2026-09-01T08:30:00Z"), null, null,
                "# PRD\n\n做一个英语学习助手……\n\n## 功能范围\n\n1. 单词卡片\n2. 复习计划"));
        doAnswer(inv -> {
            capture.run();
            return new AiplatformMaterialSummaryWireResponse(
                    MATERIAL_ID, "PRD", PROJECT_ID, "英语学习助手", "英语学习助手 · PRD",
                    2, "停用", Instant.parse("2026-09-01T08:30:00Z"),
                    String.valueOf(materialWriteUserId), materialWriteUserNickname);
        }).when(aiplatformClient).disableMaterial(eq(MATERIAL_ID));
        doAnswer(inv -> {
            capture.run();
            return new AiplatformMaterialSummaryWireResponse(
                    MATERIAL_ID, "PRD", PROJECT_ID, "英语学习助手", "英语学习助手 · PRD",
                    1, "启用", Instant.parse("2026-09-01T08:30:00Z"),
                    String.valueOf(materialWriteUserId), materialWriteUserNickname);
        }).when(aiplatformClient).enableMaterial(eq(MATERIAL_ID));
        doAnswer(inv -> {
            capture.run();
            return new AiplatformMaterialSummaryWireResponse(
                    MATERIAL_ID, "PRD", PROJECT_ID, "英语学习助手", "英语学习助手 · PRD",
                    2, "停用", Instant.parse("2026-09-01T08:30:00Z"),
                    String.valueOf(materialWriteUserId), materialWriteUserNickname);
        }).when(aiplatformClient).deleteMaterial(eq(MATERIAL_ID));
    }

    /**
     * 四动作回执/详情共用的 wire 载荷工厂：按动作后的新事实（期望态/实态/封存包元数据）参数化，
     * 其余字段固定（资源观测一行 + 项目引用——详情全量形状的代表面）。
     */
    private static AiplatformWorkspaceDetailWireResponse workspaceActionReceiptWire(
            Integer desiredState, String desiredStateName,
            Integer containerState, String containerStateName,
            String archivePath, Long archiveSizeBytes) {
        return new AiplatformWorkspaceDetailWireResponse(
                WORKSPACE_ID, "ws-" + WORKSPACE_ID, "net-" + WORKSPACE_ID,
                1, "开发", 2, "就绪", null,
                desiredState, desiredStateName, containerState, containerStateName,
                LocalDateTime.of(2026, 9, 16, 9, 0, 0), 1073741824L,
                archivePath == null ? null : LocalDateTime.of(2026, 9, 16, 9, 0, 0),
                archivePath, archiveSizeBytes,
                LocalDateTime.of(2026, 9, 8, 10, 0, 0), LocalDateTime.of(2026, 9, 16, 9, 0, 0),
                List.of(new AiplatformWorkspaceDetailWireResponse.MiddlewareResourceObservation(
                        1, "mw-" + WORKSPACE_ID + "-pg",
                        "postgresql://aiedu:secret@mw-" + WORKSPACE_ID + "-pg:5432/aiedu")),
                new AiplatformWorkspaceSummaryWireResponse.ProjectRef(PROJECT_ID, "英语学习助手", false));
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

    // ========== 沙箱观测（admin:aiplatform:workspace:read）· 权限三态 + 北向出口形状 ==========

    @Test
    @DisplayName("非超管且拥有 admin:aiplatform:workspace:read → 清单 200，期望态/实态漂移两列镜像 provider")
    void given_nonSuperAdminWithWorkspaceRead_when_listWorkspaces_then_200AndDriftColumnsMirrorProvider()
            throws Exception {
        String token = login(usernameWithReadPermission);
        // 漂移清单口径：desired=1（期望运行）+ actual=3（实态无容器）+ 分页 1-based 直传
        ResponseEntity<String> response = getWithToken(
                WORKSPACES_ENDPOINT + "?desired=1&actual=3&page=2&size=20", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        // PageResponse 形状：items/total/page/size，回显 provider 回报值（page=2——1-based 原值）
        assertThat(data.path("total").asLong()).isEqualTo(17L);
        assertThat(data.path("page").asInt()).isEqualTo(2);
        assertThat(data.path("size").asInt()).isEqualTo(20);
        JsonNode row = data.path("items").get(0);
        // 漂移行两列分示：期望运行（1/运行）而实态无容器（3/无容器）+ 四枚举 code+*Name 成对
        assertThat(row.path("workspaceId").asText()).isEqualTo(WORKSPACE_ID);
        assertThat(row.path("kind").asInt()).isEqualTo(1);
        assertThat(row.path("kindName").asText()).isEqualTo("开发");
        assertThat(row.path("statusName").asText()).isEqualTo("就绪");
        assertThat(row.path("desiredState").asInt()).isEqualTo(1);
        assertThat(row.path("desiredStateName").asText()).isEqualTo("运行");
        assertThat(row.path("containerState").asInt()).isEqualTo(3);
        assertThat(row.path("containerStateName").asText()).isEqualTo("无容器");
        // 卷用量 Long（JSON string）+ 项目引用三字段（跳转项目详情的锚）
        assertThat(row.path("volumeSizeBytes").asLong()).isEqualTo(2147483648L);
        assertThat(row.path("project").path("projectId").asText()).isEqualTo(PROJECT_ID);
        assertThat(row.path("project").path("name").asText()).isEqualTo("英语学习助手");
        assertThat(row.path("project").path("archived").asBoolean()).isFalse();
        // 未封存字段如实出 JSON null（全局 Jackson 含 null）
        assertThat(row.path("sealedAt").isNull()).isTrue();
        assertThat(row.path("archiveSizeBytes").isNull()).isTrue();
    }

    @Test
    @DisplayName("非超管且拥有 workspace:read → 详情 200，资源观测清单 + 封存包元数据镜像 provider")
    void given_nonSuperAdminWithWorkspaceRead_when_getWorkspaceDetail_then_200AndResourcesMirrored()
            throws Exception {
        String token = login(usernameWithReadPermission);
        ResponseEntity<String> response = getWithToken(WORKSPACE_DETAIL_ENDPOINT, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertThat(data.path("workspaceId").asText()).isEqualTo(WORKSPACE_ID);
        assertThat(data.path("networkName").asText()).isEqualTo("net-" + WORKSPACE_ID);
        assertThat(data.path("desiredState").asInt()).isEqualTo(3);
        assertThat(data.path("desiredStateName").asText()).isEqualTo("封存");
        // 封存包寻址键 + 包大小（Long JSON string）
        assertThat(data.path("archivePath").asText()).isEqualTo("workspace-archives/" + WORKSPACE_ID + ".tar.gz");
        assertThat(data.path("archiveSizeBytes").asLong()).isEqualTo(89128960L);
        // 中间件资源清单：kind Integer code（provider 出口无 *Name——忠实镜像）+ 连接串原文
        JsonNode pg = data.path("resources").get(0);
        assertThat(pg.path("kind").asInt()).isEqualTo(1);
        assertThat(pg.path("containerName").asText()).isEqualTo("mw-" + WORKSPACE_ID + "-pg");
        assertThat(pg.path("internalUrl").asText()).startsWith("postgresql://");
        assertThat(data.path("project").path("projectId").asText()).isEqualTo(PROJECT_ID);
    }

    @Test
    @DisplayName("非超管且缺少权限 → 沙箱读两端点 403（独立权限码 workspace:read）")
    void given_nonSuperAdminWithoutPermission_when_workspaceReadEndpoints_then_403() {
        String token = login(usernameWithoutPermission);
        assertThat(getWithToken(WORKSPACES_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getWithToken(WORKSPACE_DETAIL_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("未登录访问沙箱观测/动作端点返回 401")
    void given_unauthenticated_when_workspaceEndpoints_then_401() {
        assertThat(getWithToken(WORKSPACES_ENDPOINT, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getWithToken(WORKSPACE_DETAIL_ENDPOINT, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(postWithToken(WAKE_ENDPOINT, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ========== 沙箱四干预动作（四个独立写码）· 权限三态 + 操作者身份透传 ==========

    @Test
    @DisplayName("非超管且拥有四写码 → 唤醒/休眠/重建/封存 200，回执=详情 DTO 且 RequestContext 透传登录 operator")
    void given_nonSuperAdminWithFourWriteCodes_when_fourActions_then_200_andRequestContextCarriesOperator()
            throws Exception {
        String token = login(usernameWithWorkspaceWrite);

        // 唤醒：回执＝动作后的观测详情（期望运行 + 实态运行中的新事实）
        ResponseEntity<String> wake = postWithToken(WAKE_ENDPOINT, token);
        assertThat(wake.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode wakeData = objectMapper.readTree(wake.getBody()).path("data");
        assertThat(wakeData.path("desiredState").asInt()).isEqualTo(1);
        assertThat(wakeData.path("containerState").asInt()).isEqualTo(1);
        assertThat(wakeData.path("containerStateName").asText()).isEqualTo("运行中");
        assertThat(wakeData.path("resources").size()).isEqualTo(1);

        // 休眠：期望休眠 + 实态无容器（删容器保卷）
        ResponseEntity<String> hibernate = postWithToken(HIBERNATE_ENDPOINT, token);
        assertThat(hibernate.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode hibernateData = objectMapper.readTree(hibernate.getBody()).path("data");
        assertThat(hibernateData.path("desiredStateName").asText()).isEqualTo("休眠");
        assertThat(hibernateData.path("containerStateName").asText()).isEqualTo("无容器");

        // 重建：同唤醒收敛（期望运行 + 实态运行中）
        ResponseEntity<String> rebuild = postWithToken(REBUILD_ENDPOINT, token);
        assertThat(rebuild.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(rebuild.getBody()).path("data")
                .path("containerStateName").asText()).isEqualTo("运行中");

        // 封存：期望封存 + 封存包元数据（寻址键/大小）
        ResponseEntity<String> seal = postWithToken(SEAL_ENDPOINT, token);
        assertThat(seal.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode sealData = objectMapper.readTree(seal.getBody()).path("data");
        assertThat(sealData.path("desiredStateName").asText()).isEqualTo("封存");
        assertThat(sealData.path("archivePath").asText()).isEqualTo("workspace-archives/" + WORKSPACE_ID + ".tar.gz");

        // 操作者身份不经 body——经 RequestContext 抵达出站调用点（OpenApiClient 据此带
        // X-User-Id/X-User-Name 出站头，issue #66 断言必带）：== 登录沙箱治理运营的 id/昵称
        assertThat(capturedOperatorId.get()).isEqualTo(workspaceWriteUserId);
        assertThat(capturedOperatorName.get()).isEqualTo(workspaceWriteUserNickname);
    }

    @Test
    @DisplayName("仅有 workspace:read（无写码）→ 四动作 403（read ≠ 四写码）")
    void given_nonSuperAdminWithReadOnly_when_workspaceActions_then_403() {
        String token = login(usernameWithReadPermission);
        assertThat(postWithToken(WAKE_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postWithToken(HIBERNATE_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postWithToken(REBUILD_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postWithToken(SEAL_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("仅有 workspace:hibernate 单写码 → 休眠 200、唤醒/重建/封存 403（四写码彼此独立，最小授权）")
    void given_nonSuperAdminWithHibernateOnly_when_actions_then_hibernate200Others403() {
        String token = login(usernameWithHibernateOnly);
        assertThat(postWithToken(HIBERNATE_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(postWithToken(WAKE_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postWithToken(REBUILD_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postWithToken(SEAL_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("沙箱动作下游 WSP_015 → 北向 HTTP 409 + 信封 code=1015 + message 原文（不映射）")
    void given_wsp015FromDownstream_when_hibernate_then_errorEnvelopePassedThrough() throws Exception {
        when(aiplatformClient.hibernateWorkspace(eq(WORKSPACE_ID))).thenThrow(
                AiplatformUpstreamException.from(new OpenApiClientException(409,
                        "{\"code\":1015,\"message\":\"编码 run 进行中，沙箱动作被拒（先取消 run 或等收口）\",\"data\":null}")));

        String token = login(usernameWithWorkspaceWrite);
        ResponseEntity<String> response = postWithToken(HIBERNATE_ENDPOINT, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode root = objectMapper.readTree(response.getBody());
        // 信封 code＝数字业务码 1015（WSP_015），而非映射后的 409——前端比对 aiplatform 业务码的分支活
        assertThat(root.path("code").asInt()).isEqualTo(1015);
        assertThat(root.path("message").asText()).isEqualTo("编码 run 进行中，沙箱动作被拒（先取消 run 或等收口）");
        assertThat(root.path("data").isNull()).isTrue();
    }

    // ========== 成本观测（admin:aiplatform:cost:read）· 权限三态 + 北向出口形状 + 时间窗必填 ==========

    @Test
    @DisplayName("非超管且拥有 admin:aiplatform:cost:read → 总览/unpriced 200，五档+币种分桶+分解镜像 provider")
    void given_nonSuperAdminWithCostRead_when_overviewAndUnpriced_then_200AndShapeMirrorsProvider() throws Exception {
        String token = login(usernameWithReadPermission);

        // 全局总览：窗口回显 + 五档总量（JSON 数字）+ 币种分桶 + 分智能体（agentKind 裸维度串 +
        // agentKindName 随行——aiplatform#186 已落，辅助标记为 null 如实出 JSON null）
        ResponseEntity<String> overview = getWithToken(COSTS_ENDPOINT + "/overview" + COST_WINDOW_QUERY, token);
        assertThat(overview.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode overviewData = objectMapper.readTree(overview.getBody()).path("data");
        assertThat(overviewData.path("from").asText()).isEqualTo(COST_FROM);
        assertThat(overviewData.path("to").asText()).isEqualTo(COST_TO);
        JsonNode total = overviewData.path("total");
        assertThat(total.path("input").asLong()).isEqualTo(5000L);
        assertThat(total.path("output").asLong()).isEqualTo(1200L);
        assertThat(total.path("cacheRead").asLong()).isEqualTo(300L);
        assertThat(total.path("cacheWrite").asLong()).isEqualTo(0L);
        assertThat(total.path("reasoning").asLong()).isEqualTo(800L);
        assertThat(overviewData.path("cost").path("CNY").asDouble()).isEqualTo(12.3456);
        JsonNode agentKind = overviewData.path("byAgentKind").get(0);
        assertThat(agentKind.path("agentKind").asText()).isEqualTo("naming");
        assertThat(agentKind.path("agentKindName").isNull()).isTrue();

        // unpriced 全局警示：tokenKind Integer code + tokenKindName + tokens 只计无价分量
        ResponseEntity<String> unpriced = getWithToken(COSTS_ENDPOINT + "/unpriced" + COST_WINDOW_QUERY, token);
        assertThat(unpriced.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode tier = objectMapper.readTree(unpriced.getBody()).path("data").path("items").get(0);
        assertThat(tier.path("provider").asText()).isEqualTo("openai");
        assertThat(tier.path("tokenKind").asInt()).isEqualTo(1);
        assertThat(tier.path("tokenKindName").asText()).isEqualTo("输入");
        assertThat(tier.path("tokens").asLong()).isEqualTo(700L);
    }

    @Test
    @DisplayName("非超管且拥有 cost:read → 项目成本清单/单项目下钻 200，分页 1-based 回显 + allUnpriced 标记")
    void given_nonSuperAdminWithCostRead_when_projectCosts_then_200AndPageEchoed() throws Exception {
        String token = login(usernameWithReadPermission);

        // 项目成本清单：PageResponse 形状（total JSON string 口径在 Long 字段），回显 provider 回报值
        ResponseEntity<String> projects = getWithToken(
                COSTS_ENDPOINT + "/projects" + COST_WINDOW_QUERY + "&page=1&size=20", token);
        assertThat(projects.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode page = objectMapper.readTree(projects.getBody()).path("data");
        assertThat(page.path("total").asLong()).isEqualTo(2L);
        assertThat(page.path("page").asInt()).isEqualTo(1);
        JsonNode row = page.path("items").get(0);
        assertThat(row.path("projectId").asText()).isEqualTo(PROJECT_ID);
        assertThat(row.path("cost").path("CNY").asDouble()).isEqualTo(12.3456);
        assertThat(row.path("allUnpriced").asBoolean()).isFalse();

        // 单项目下钻：subject 原值回显 + unpriced 档位（bySubject 口径无计数）+ 双分解
        ResponseEntity<String> detail = getWithToken(
                COSTS_ENDPOINT + "/projects/" + PROJECT_ID + COST_WINDOW_QUERY, token);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode detailData = objectMapper.readTree(detail.getBody()).path("data");
        assertThat(detailData.path("projectId").asText()).isEqualTo(PROJECT_ID);
        JsonNode tier = detailData.path("unpriced").get(0);
        assertThat(tier.path("tokenKind").asInt()).isEqualTo(1);
        assertThat(tier.path("tokenKindName").asText()).isEqualTo("输入");
        assertThat(detailData.path("byAgentKind").get(0).path("agentKindName").asText()).isEqualTo("执行智能体");
    }

    @Test
    @DisplayName("成本端点时间窗 from/to 必填 → 缺参 400 / 非 Instant 404（框架绑定层裁决，不到 provider）")
    void given_missingOrInvalidWindow_when_costEndpoints_then_bindingLayerRejects() {
        String token = login(usernameWithReadPermission);
        // 缺 to（必填）→ 400（MissingServletRequestParameterException）；非 Instant（无时区态）→ 404
        // （框架 handleTypeMismatch → NOT_FOUND 既定口径）——均在本服务绑定层，不到 provider
        assertThat(getWithToken(COSTS_ENDPOINT + "/overview?from=" + COST_FROM, token)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(getWithToken(COSTS_ENDPOINT + "/overview?from=2026-09-01&to=" + COST_TO, token)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("非超管且缺少权限 → 成本四端点 403（独立权限码 cost:read）")
    void given_nonSuperAdminWithoutPermission_when_costEndpoints_then_403() {
        String token = login(usernameWithoutPermission);
        assertThat(getWithToken(COSTS_ENDPOINT + "/overview" + COST_WINDOW_QUERY, token)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getWithToken(COSTS_ENDPOINT + "/unpriced" + COST_WINDOW_QUERY, token)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getWithToken(COSTS_ENDPOINT + "/projects" + COST_WINDOW_QUERY, token)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getWithToken(COSTS_ENDPOINT + "/projects/" + PROJECT_ID + COST_WINDOW_QUERY, token)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("未登录访问成本四端点返回 401")
    void given_unauthenticated_when_costEndpoints_then_401() {
        assertThat(getWithToken(COSTS_ENDPOINT + "/overview" + COST_WINDOW_QUERY, null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getWithToken(COSTS_ENDPOINT + "/unpriced" + COST_WINDOW_QUERY, null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getWithToken(COSTS_ENDPOINT + "/projects" + COST_WINDOW_QUERY, null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getWithToken(COSTS_ENDPOINT + "/projects/" + PROJECT_ID + COST_WINDOW_QUERY, null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("成本查询下游 METER_011 → 北向 HTTP 400 + 信封 code=3011 + message 原文（不映射）")
    void given_meter011FromDownstream_when_getOverview_then_errorEnvelopePassedThrough() throws Exception {
        when(aiplatformClient.getCostOverview(any())).thenThrow(
                AiplatformUpstreamException.from(new OpenApiClientException(400,
                        "{\"code\":3011,\"message\":\"无效的成本查询参数\",\"data\":null}")));

        String token = login(usernameWithReadPermission);
        ResponseEntity<String> response = getWithToken(COSTS_ENDPOINT + "/overview" + COST_WINDOW_QUERY, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode root = objectMapper.readTree(response.getBody());
        // 信封 code＝数字业务码 3011（METER_011＝域码 3×1000＋11），而非映射后的 400——前端比对业务码的分支活
        assertThat(root.path("code").asInt()).isEqualTo(3011);
        assertThat(root.path("message").asText()).isEqualTo("无效的成本查询参数");
        assertThat(root.path("data").isNull()).isTrue();
    }

    // ========== 单价表（admin:aiplatform:price-entry:read）· 权限三态 + 北向出口形状 ==========

    @Test
    @DisplayName("非超管且拥有 admin:aiplatform:price-entry:read → 清单 200，含历史行 + tokenKind/*Name + unitPrice String + 留痕镜像 provider")
    void given_nonSuperAdminWithPriceEntryRead_when_list_then_200AndShapeMirrorsProvider() throws Exception {
        String token = login(usernameWithReadPermission);
        // provider/model 精确过滤 + 分页 1-based 直传（page=2 无 ±1）
        ResponseEntity<String> response = getWithToken(
                PRICE_ENTRIES_ENDPOINT + "?provider=anthropic&model=claude-fable-5&page=2&size=20", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        // PageResponse 形状：items/total/page/size，回显 provider 回报值（page=2——1-based 原值）
        assertThat(data.path("total").asLong()).isEqualTo(12L);
        assertThat(data.path("page").asInt()).isEqualTo(2);
        assertThat(data.path("size").asInt()).isEqualTo(20);
        // 首行：当前行（effectiveTo 出 JSON null）——tokenKind Integer code + tokenKindName、
        // unitPrice String 明文小数、操作者留痕两肢
        JsonNode current = data.path("items").get(0);
        assertThat(current.path("id").asText()).isEqualTo(PRICE_ENTRY_ID);
        assertThat(current.path("tokenKind").asInt()).isEqualTo(1);
        assertThat(current.path("tokenKindName").asText()).isEqualTo("输入");
        assertThat(current.path("unitPrice").asText()).isEqualTo("0.000002");
        assertThat(current.path("effectiveFrom").asText()).isEqualTo("2026-08-01T00:00:00Z");
        assertThat(current.path("effectiveTo").isNull()).isTrue();
        assertThat(current.path("operatorId").asText()).isEqualTo("700160");
        // 次行：历史行（区间两端俱全）——存量形制操作者两列如实出 JSON null（全局 Jackson 含 null）
        JsonNode historical = data.path("items").get(1);
        assertThat(historical.path("effectiveTo").asText()).isEqualTo("2026-08-01T00:00:00Z");
        assertThat(historical.path("operatorId").isNull()).isTrue();
        assertThat(historical.path("operatorName").isNull()).isTrue();
    }

    @Test
    @DisplayName("非超管且缺少权限 → 单价表三端点 403（read 与两写码皆无）")
    void given_nonSuperAdminWithoutPermission_when_priceEntryEndpoints_then_403() {
        String token = login(usernameWithoutPermission);
        assertThat(getWithToken(PRICE_ENTRIES_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postWithToken(REPRICE_ENDPOINT, REPRICE_BODY, token).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postWithToken(DEACTIVATE_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("单价表清单非法分页值 → 404（本服务绑定层类型不匹配裁决，不到 provider——METER_009 不经北向暴露）")
    void given_illegalPageParam_when_listPriceEntries_then_bindingLayerRejects() {
        String token = login(usernameWithReadPermission);
        // 非整数 page 在北向 int 绑定即类型不匹配，按框架 handleTypeMismatch 既定口径 404
        // （同成本域非 Instant 404 先例）——provider 的 METER_009 口径不经北向可达
        assertThat(getWithToken(PRICE_ENTRIES_ENDPOINT + "?page=abc", token).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("仅有 price-entry:read（无写码）→ 改价/停用 403（read ≠ 两写码）")
    void given_nonSuperAdminWithReadOnly_when_priceEntryActions_then_403() {
        String token = login(usernameWithReadPermission);
        assertThat(postWithToken(REPRICE_ENDPOINT, REPRICE_BODY, token).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postWithToken(DEACTIVATE_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("未登录访问单价表三端点返回 401")
    void given_unauthenticated_when_priceEntryEndpoints_then_401() {
        assertThat(getWithToken(PRICE_ENTRIES_ENDPOINT, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(postWithToken(REPRICE_ENDPOINT, REPRICE_BODY, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(postWithToken(DEACTIVATE_ENDPOINT, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ========== 单价表写操作（reprice/deactivate 两独立写码）· 权限三态 + 回执形状 + 操作者透传 ==========

    @Test
    @DisplayName("非超管且拥有双写码 → 改价/停用 200，closed/opened 双行回执且 RequestContext 透传登录 operator")
    void given_nonSuperAdminWithBothWriteCodes_when_repriceAndDeactivate_then_200_andRequestContextCarriesOperator()
            throws Exception {
        String token = login(usernameWithPriceWrite);

        // 改价：回执＝{closed, opened} 双行——closed 落 effectiveTo＝新起点（保留原开行留痕 700160），
        // opened 沿用匹配键、新单价 String、敞口（effectiveTo null）、带改价操作者
        ResponseEntity<String> reprice = postWithToken(REPRICE_ENDPOINT, REPRICE_BODY, token);
        assertThat(reprice.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(reprice.getBody()).path("data");
        JsonNode closed = data.path("closed");
        assertThat(closed.path("id").asText()).isEqualTo(PRICE_ENTRY_ID);
        assertThat(closed.path("effectiveTo").asText()).isEqualTo("2026-09-20T00:00:00Z");
        assertThat(closed.path("operatorId").asText()).isEqualTo("700160");
        JsonNode opened = data.path("opened");
        assertThat(opened.path("id").asText()).isEqualTo("3830100003333333");
        assertThat(opened.path("unitPrice").asText()).isEqualTo("0.0000018");
        assertThat(opened.path("effectiveFrom").asText()).isEqualTo("2026-09-20T00:00:00Z");
        assertThat(opened.path("effectiveTo").isNull()).isTrue();
        // 改价操作者＝登录运营（RequestContext 透传落痕，provider 口径）
        assertThat(opened.path("operatorId").asText()).isEqualTo(String.valueOf(priceWriteUserId));
        assertThat(opened.path("operatorName").asText()).isEqualTo(priceWriteUserNickname);

        // 停用：被关行单行回执（effectiveTo 已落、停用操作者落被关行）
        ResponseEntity<String> deactivate = postWithToken(DEACTIVATE_ENDPOINT, token);
        assertThat(deactivate.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode closedRow = objectMapper.readTree(deactivate.getBody()).path("data");
        assertThat(closedRow.path("id").asText()).isEqualTo(PRICE_ENTRY_ID);
        assertThat(closedRow.path("effectiveTo").asText()).isEqualTo("2026-09-16T10:30:00Z");

        // 操作者身份不经 body——经 RequestContext 抵达出站调用点（OpenApiClient 据此带
        // X-User-Id/X-User-Name 出站头）：== 登录调价运营的 id/昵称（沙箱四动作同款手法）
        assertThat(capturedOperatorId.get()).isEqualTo(priceWriteUserId);
        assertThat(capturedOperatorName.get()).isEqualTo(priceWriteUserNickname);
    }

    @Test
    @DisplayName("仅有 price-entry:reprice 单写码 → 改价 200、停用 403（两写码彼此独立，最小授权）")
    void given_nonSuperAdminWithRepriceOnly_when_actions_then_reprice200Deactivate403() {
        String token = login(usernameWithRepriceOnly);
        assertThat(postWithToken(REPRICE_ENDPOINT, REPRICE_BODY, token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(postWithToken(DEACTIVATE_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("单价表改价下游 METER_008 → 北向 HTTP 409 + 信封 code=3008 + message 原文（不映射）")
    void given_meter008FromDownstream_when_reprice_then_errorEnvelopePassedThrough() throws Exception {
        when(aiplatformClient.repricePriceEntry(eq(PRICE_ENTRY_ID), any(AiplatformPriceEntryRepriceWireRequest.class)))
                .thenThrow(AiplatformUpstreamException.from(new OpenApiClientException(409,
                        "{\"code\":3008,\"message\":\"同匹配键生效区间重叠（跨区间或同起点）\",\"data\":null}")));

        String token = login(usernameWithPriceWrite);
        ResponseEntity<String> response = postWithToken(REPRICE_ENDPOINT, REPRICE_BODY, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode root = objectMapper.readTree(response.getBody());
        // 信封 code＝数字业务码 3008（METER_008＝域码 3×1000＋8），而非映射后的 409——前端比对业务码的分支活
        assertThat(root.path("code").asInt()).isEqualTo(3008);
        assertThat(root.path("message").asText()).isEqualTo("同匹配键生效区间重叠（跨区间或同起点）");
        assertThat(root.path("data").isNull()).isTrue();
    }

    // ========== 知识素材（admin:aiplatform:material:read）· 权限三态 + 北向出口形状 ==========

    @Test
    @DisplayName("非超管且拥有 admin:aiplatform:material:read → 清单 200，三维过滤 + status/*Name + 留痕镜像 provider")
    void given_nonSuperAdminWithMaterialRead_when_listMaterials_then_200AndShapeMirrorsProvider() throws Exception {
        String token = login(usernameWithReadPermission);
        // 三维过滤（status 单选 + 沉淀时间闭区间 Instant + projectId 精确）+ 分页 1-based 直传
        ResponseEntity<String> response = getWithToken(
                MATERIALS_ENDPOINT + "?status=2&sunkFrom=2026-09-01T00:00:00Z&sunkTo=2026-09-15T23:59:59Z"
                        + "&projectId=" + PROJECT_ID + "&page=2&size=20", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        // PageResponse 形状：items/total/page/size，回显 provider 回报值（page=2——1-based 原值）
        assertThat(data.path("total").asLong()).isEqualTo(9L);
        assertThat(data.path("page").asInt()).isEqualTo(2);
        assertThat(data.path("size").asInt()).isEqualTo(20);
        // 首行：已治理行——status Integer code + statusName、来源项目引用、治理留痕两肢
        JsonNode governed = data.path("items").get(0);
        assertThat(governed.path("id").asText()).isEqualTo(MATERIAL_ID);
        assertThat(governed.path("kind").asText()).isEqualTo("PRD");
        assertThat(governed.path("projectId").asText()).isEqualTo(PROJECT_ID);
        assertThat(governed.path("projectName").asText()).isEqualTo("英语学习助手");
        assertThat(governed.path("status").asInt()).isEqualTo(2);
        assertThat(governed.path("statusName").asText()).isEqualTo("停用");
        assertThat(governed.path("sunkAt").asText()).isEqualTo("2026-09-01T08:30:00Z");
        assertThat(governed.path("operatorId").asText()).isEqualTo(String.valueOf(materialWriteUserId));
        assertThat(governed.path("operatorName").asText()).isEqualTo(materialWriteUserNickname);
        // 次行：未治理行——操作者两列如实出 JSON null（全局 Jackson 含 null）
        JsonNode untreated = data.path("items").get(1);
        assertThat(untreated.path("status").asInt()).isEqualTo(1);
        assertThat(untreated.path("statusName").asText()).isEqualTo("启用");
        assertThat(untreated.path("operatorId").isNull()).isTrue();
        assertThat(untreated.path("operatorName").isNull()).isTrue();
    }

    @Test
    @DisplayName("非超管且拥有 material:read → 详情 200，元数据 + PRD 全文 content 镜像 provider")
    void given_nonSuperAdminWithMaterialRead_when_getMaterialDetail_then_200WithFullContent() throws Exception {
        String token = login(usernameWithReadPermission);
        ResponseEntity<String> response = getWithToken(MATERIAL_DETAIL_ENDPOINT, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertThat(data.path("id").asText()).isEqualTo(MATERIAL_ID);
        assertThat(data.path("status").asInt()).isEqualTo(1);
        assertThat(data.path("statusName").asText()).isEqualTo("启用");
        // 全文＝块按 seq 以空行拼接（多段形态原样到达）；未治理过的操作者两列 JSON null
        assertThat(data.path("content").asText()).startsWith("# PRD");
        assertThat(data.path("content").asText()).contains("\n\n## 功能范围\n\n1. 单词卡片\n2. 复习计划");
        assertThat(data.path("operatorId").isNull()).isTrue();
    }

    @Test
    @DisplayName("非超管且缺少权限 → 素材五端点 403（read 与三写码皆无）")
    void given_nonSuperAdminWithoutPermission_when_materialEndpoints_then_403() {
        String token = login(usernameWithoutPermission);
        assertThat(getWithToken(MATERIALS_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getWithToken(MATERIAL_DETAIL_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postWithToken(MATERIAL_DISABLE_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postWithToken(MATERIAL_ENABLE_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(deleteWithToken(MATERIAL_DELETE_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("素材清单非法参数 → 404（本服务绑定层类型不匹配裁决，不到 provider——KNW_007 不经北向暴露）")
    void given_illegalFilterParam_when_listMaterials_then_bindingLayerRejects() {
        String token = login(usernameWithReadPermission);
        // 非整数 status / 非 Instant 沉淀时间（无时区态）在北向绑定即类型不匹配，按框架
        // handleTypeMismatch 既定口径 404——provider 的 KNW_007 口径仅剩「数值但未知 code」一径可达
        assertThat(getWithToken(MATERIALS_ENDPOINT + "?status=abc", token).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getWithToken(MATERIALS_ENDPOINT + "?sunkFrom=2026-09-01", token).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("未登录访问素材五端点返回 401（含 DELETE）")
    void given_unauthenticated_when_materialEndpoints_then_401() {
        assertThat(getWithToken(MATERIALS_ENDPOINT, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getWithToken(MATERIAL_DETAIL_ENDPOINT, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(postWithToken(MATERIAL_DISABLE_ENDPOINT, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(postWithToken(MATERIAL_ENABLE_ENDPOINT, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(deleteWithToken(MATERIAL_DELETE_ENDPOINT, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ========== 素材三治理动作（disable/enable/delete 三独立写码）· 权限三态 + 回执形状 + 操作者透传 ==========

    @Test
    @DisplayName("非超管且拥有三写码 → 停用/启用/删除 200，回执=summary 且 RequestContext 透传登录 operator")
    void given_nonSuperAdminWithThreeWriteCodes_when_governanceActions_then_200_andRequestContextCarriesOperator()
            throws Exception {
        String token = login(usernameWithMaterialWrite);

        // 停用：回执＝provider 重读登记行——已停用 + 治理操作者落素材级
        ResponseEntity<String> disable = postWithToken(MATERIAL_DISABLE_ENDPOINT, token);
        assertThat(disable.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode disabled = objectMapper.readTree(disable.getBody()).path("data");
        assertThat(disabled.path("id").asText()).isEqualTo(MATERIAL_ID);
        assertThat(disabled.path("status").asInt()).isEqualTo(2);
        assertThat(disabled.path("statusName").asText()).isEqualTo("停用");
        assertThat(disabled.path("operatorId").asText()).isEqualTo(String.valueOf(materialWriteUserId));
        assertThat(disabled.path("operatorName").asText()).isEqualTo(materialWriteUserNickname);

        // 启用：恢复启用（可逆开关的另一侧）
        ResponseEntity<String> enable = postWithToken(MATERIAL_ENABLE_ENDPOINT, token);
        assertThat(enable.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode enabled = objectMapper.readTree(enable.getBody()).path("data");
        assertThat(enabled.path("status").asInt()).isEqualTo(1);
        assertThat(enabled.path("statusName").asText()).isEqualTo("启用");

        // 删除：回执＝删除前终态（provider 契约如此——确认移除了什么；本例是一行已停用素材）
        ResponseEntity<String> delete = deleteWithToken(MATERIAL_DELETE_ENDPOINT, token);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode deleted = objectMapper.readTree(delete.getBody()).path("data");
        assertThat(deleted.path("id").asText()).isEqualTo(MATERIAL_ID);
        assertThat(deleted.path("status").asInt()).isEqualTo(2);

        // 操作者身份不经 body——经 RequestContext 抵达出站调用点（OpenApiClient 据此带
        // X-User-Id/X-User-Name 出站头，provider 缺头必拦 KNW_006——issue #69 断言必带）：
        // == 登录内容治理运营的 id/昵称
        assertThat(capturedOperatorId.get()).isEqualTo(materialWriteUserId);
        assertThat(capturedOperatorName.get()).isEqualTo(materialWriteUserNickname);
    }

    @Test
    @DisplayName("仅有 material:read（无写码）→ 三治理动作 403（read ≠ 三写码）")
    void given_nonSuperAdminWithReadOnly_when_materialActions_then_403() {
        String token = login(usernameWithReadPermission);
        assertThat(postWithToken(MATERIAL_DISABLE_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postWithToken(MATERIAL_ENABLE_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(deleteWithToken(MATERIAL_DELETE_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("仅有 material:disable 单写码 → 停用 200、启用/删除 403（三写码彼此独立，可逆与不可逆分权）")
    void given_nonSuperAdminWithDisableOnly_when_actions_then_disable200Others403() {
        String token = login(usernameWithDisableOnly);
        assertThat(postWithToken(MATERIAL_DISABLE_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(postWithToken(MATERIAL_ENABLE_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(deleteWithToken(MATERIAL_DELETE_ENDPOINT, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("素材停用下游 KNW_006（缺操作者头）→ 北向 HTTP 400 + 信封 code=2006 + message 原文（不映射）")
    void given_knw006FromDownstream_when_disable_then_errorEnvelopePassedThrough() throws Exception {
        when(aiplatformClient.disableMaterial(eq(MATERIAL_ID))).thenThrow(
                AiplatformUpstreamException.from(new OpenApiClientException(400,
                        "{\"code\":2006,\"message\":\"操作者不能为空\",\"data\":null}")));

        String token = login(usernameWithMaterialWrite);
        ResponseEntity<String> response = postWithToken(MATERIAL_DISABLE_ENDPOINT, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode root = objectMapper.readTree(response.getBody());
        // 信封 code＝数字业务码 2006（KNW_006＝域码 2×1000＋6），而非映射后的 400——前端比对业务码的分支活
        assertThat(root.path("code").asInt()).isEqualTo(2006);
        assertThat(root.path("message").asText()).isEqualTo("操作者不能为空");
        assertThat(root.path("data").isNull()).isTrue();
    }

    @Test
    @DisplayName("素材删除下游 KNW_005（重复删除）→ 北向 HTTP 404 + 信封 code=2005 + message 原文（不映射）")
    void given_knw005FromDownstream_when_delete_then_errorEnvelopePassedThrough() throws Exception {
        when(aiplatformClient.deleteMaterial(eq(MATERIAL_ID))).thenThrow(
                AiplatformUpstreamException.from(new OpenApiClientException(404,
                        "{\"code\":2005,\"message\":\"知识素材不存在\",\"data\":null}")));

        String token = login(usernameWithMaterialWrite);
        ResponseEntity<String> response = deleteWithToken(MATERIAL_DELETE_ENDPOINT, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode root = objectMapper.readTree(response.getBody());
        // 信封 code＝数字业务码 2005（KNW_005＝域码 2×1000＋5），而非映射后的 404——前端比对业务码的分支活
        assertThat(root.path("code").asInt()).isEqualTo(2005);
        assertThat(root.path("message").asText()).isEqualTo("知识素材不存在");
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
        return restTemplate.exchange(url(path), HttpMethod.GET,
                new HttpEntity<>(jsonHeadersWithToken(token)), String.class);
    }

    /** 无请求体 POST（沙箱四干预动作、单价表停用同形——provider 端只读路径参数，body 为空）。 */
    private ResponseEntity<String> postWithToken(String path, String token) {
        return restTemplate.exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(null, jsonHeadersWithToken(token)), String.class);
    }

    /** JSON 请求体 POST（单价表改价命令体同形——逐字镜像 provider 契约 {unitPrice, currency, effectiveFrom}）。 */
    private ResponseEntity<String> postWithToken(String path, String body, String token) {
        return restTemplate.exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(body, jsonHeadersWithToken(token)), String.class);
    }

    /** 无请求体 DELETE（素材删除同形——provider 端只读路径参数，body 为空）。 */
    private ResponseEntity<String> deleteWithToken(String path, String token) {
        return restTemplate.exchange(url(path), HttpMethod.DELETE,
                new HttpEntity<>(null, jsonHeadersWithToken(token)), String.class);
    }

    /** JSON 请求头 + Sa-Token 头（前缀按框架配置拼装；token null 则只带内容类型）。 */
    private HttpHeaders jsonHeadersWithToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            SaTokenConfig cfg = SaManager.getConfig();
            String value = (cfg.getTokenPrefix() == null || cfg.getTokenPrefix().isEmpty())
                    ? token
                    : cfg.getTokenPrefix() + " " + token;
            headers.set(cfg.getTokenName(), value);
        }
        return headers;
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
