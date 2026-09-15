package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.http.HttpHeaders;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.aieducenter.admin.aiplatform.application.AiplatformAccountAppService;
import com.aieducenter.admin.aiplatform.application.AiplatformOrderAppService;
import com.aieducenter.admin.aiplatform.application.AiplatformProjectAppService;
import com.aieducenter.admin.aiplatform.application.AiplatformUpstreamException;
import com.aieducenter.admin.aiplatform.application.AiplatformWorkspaceAppService;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformOrderQuery;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformProjectQuery;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformWorkspaceQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformAccountProfileResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformOrderSummaryResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectSummaryResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformWorkspaceDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformWorkspaceSummaryResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformAccountProfileWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformConversationEntryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderBriefWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderSummaryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformPrdWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectSummaryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformVersionDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformVersionWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformWorkspaceDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformWorkspaceListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformWorkspaceSummaryWireResponse;
import com.aieducenter.admin.aiplatform.infrastructure.AiplatformClient;
import com.cartisan.openapi.client.BinaryResponse;
import com.cartisan.openapi.client.OpenApiClientException;
import com.cartisan.web.response.PageResponse;

/**
 * aiplatform BFF 集成测试（issue #63 T1 账号首批 + #64 订单读路径 + #65 项目核心读路径 +
 * #66 沙箱观测与四干预动作）——
 * mock {@link AiplatformClient}，验证 AppService 在 Spring 上下文中的完整接线（DI、query→wire 映射、
 * wire→response DTO 映射、分页 1-based 透传、二进制载体保全、下游错误透传不映射）。
 *
 * <p>镜像 {@code AccountBffIntegrationTest} / {@code PaymentBffIntegrationTest}。不模拟安全层
 * （权限在 {@code AiplatformRbacEnforcementIntegrationTest} 覆盖）；不直测 {@link AiplatformClient}
 * （wire 反序列化契约在 {@code AiplatformAccountClientContractTest} /
 * {@code AiplatformOrderClientContractTest} 以真实传输 stub 钉死，方法边界 mock 会绕过
 * 反序列化——已知反例）。</p>
 *
 * <p>mock 数据按 aiplatform 真实响应形状构造（{@code id} 为 String TSID 十进制串、
 * {@code status} Integer code + {@code statusName}、金额 Long 分、时间 ISO）。错误路径断言
 * <strong>透传</strong>（{@link AiplatformUpstreamException} 携带 provider 的 HTTP 状态 +
 * 数字业务码 + message），而非 payment/identity 式的 {@code BaseCodeMessage} 映射
 * （spec #62 定稿：aiplatform 不做映射翻译）。</p>
 *
 * @since 0.1.0
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AiplatformBffIntegrationTest {

    @Autowired
    private AiplatformAccountAppService accountAppService;

    @Autowired
    private AiplatformOrderAppService orderAppService;

    @Autowired
    private AiplatformProjectAppService projectAppService;

    @Autowired
    private AiplatformWorkspaceAppService workspaceAppService;

    @MockBean
    private AiplatformClient aiplatformClient;

    // ========== 账号档案 · DTO 映射 ==========

    @Test
    void given_accountProfile_when_get_then_returnMappedProfile() {
        when(aiplatformClient.getAccountProfile("auth0|65f2c8a1")).thenReturn(
                new AiplatformAccountProfileWireResponse(
                        "3829492001234567", "auth0|65f2c8a1", "文野",
                        LocalDateTime.of(2026, 1, 15, 10, 30, 0)));

        AiplatformAccountProfileResponse profile = accountAppService.getAccountProfile("auth0|65f2c8a1");

        // DTO 映射：wire → response 逐字段（逐字镜像——无增删字段、无换型）
        assertThat(profile.id()).isEqualTo("3829492001234567");           // String（TSID 十进制串）
        assertThat(profile.externalId()).isEqualTo("auth0|65f2c8a1");
        assertThat(profile.displayName()).isEqualTo("文野");
        assertThat(profile.createdAt()).isEqualTo(LocalDateTime.of(2026, 1, 15, 10, 30, 0));
    }

    @Test
    void given_wireTranslationThrowsUpstream_when_get_then_propagateWithoutMapping() {
        // 错误信封已由 AiplatformClient 翻译为透传异常（IDN_004→HTTP 404 + 数字业务码 6004），
        // AppService 不再二次翻译——原样上抛，由北向 advice 还原 provider 信封
        when(aiplatformClient.getAccountProfile("auth0|unknown")).thenThrow(
                AiplatformUpstreamException.from(
                        new OpenApiClientException(404, "{\"code\":6004,\"message\":\"账号不存在\",\"data\":null}")));

        assertThatThrownBy(() -> accountAppService.getAccountProfile("auth0|unknown"))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(404);
                    assertThat(upstream.envelopeCode()).isEqualTo(6004);
                    assertThat(upstream.getMessage()).isEqualTo("账号不存在");
                });
    }

    // ========== 订单清单 · query→wire 映射 + 分页 1-based 透传 ==========

    @Test
    void given_orderQueryAndPage_when_list_then_wireRequestMappedAndPageEchoedFromProvider() {
        when(aiplatformClient.listOrders(any(), eq(3), eq(20))).thenReturn(
                new PageResponse<>(List.of(
                        new AiplatformOrderSummaryWireResponse(
                                "3829492001234567", "3829492007654321", "英语学习助手", "文野",
                                3, "已支付", 1999000L, "CNY",
                                LocalDateTime.of(2026, 9, 10, 14, 20, 0),
                                LocalDateTime.of(2026, 9, 11, 9, 0, 0))), 42, 3, 20));

        PageResponse<AiplatformOrderSummaryResponse> page = orderAppService.list(
                new AiplatformOrderQuery(List.of(1, 5),
                        LocalDateTime.of(2026, 9, 1, 0, 0, 0),
                        LocalDateTime.of(2026, 9, 15, 23, 59, 59),
                        "auth0|65f2c8a1", "3829492001234567"), 3, 20);

        // 出站参数：北向 query → wire 过滤记录逐字段映射；page 1-based 直传（3→3，无 ±1）
        ArgumentCaptor<AiplatformOrderListWireRequest> wireCaptor =
                ArgumentCaptor.forClass(AiplatformOrderListWireRequest.class);
        verify(aiplatformClient).listOrders(wireCaptor.capture(), eq(3), eq(20));
        assertThat(wireCaptor.getValue()).isEqualTo(new AiplatformOrderListWireRequest(
                List.of(1, 5),
                LocalDateTime.of(2026, 9, 1, 0, 0, 0),
                LocalDateTime.of(2026, 9, 15, 23, 59, 59),
                "auth0|65f2c8a1", "3829492001234567"));

        // 回显取 provider 回报值（含 clamp 后值）——不是北向入参回声；wire → response 逐字段
        assertThat(page.total()).isEqualTo(42L);
        assertThat(page.page()).isEqualTo(3);
        assertThat(page.size()).isEqualTo(20);
        var row = page.items().get(0);
        assertThat(row.id()).isEqualTo("3829492001234567");
        assertThat(row.status()).isEqualTo(3);
        assertThat(row.statusName()).isEqualTo("已支付");
        assertThat(row.amount()).isEqualTo(1999000L);
        assertThat(row.currency()).isEqualTo("CNY");
        assertThat(row.createdAt()).isEqualTo(LocalDateTime.of(2026, 9, 10, 14, 20, 0));
    }

    // ========== 订单详情错误 · 透传不映射 ==========

    @Test
    void given_ord001FromDownstream_when_getDetail_then_propagateWithoutMapping() {
        when(aiplatformClient.getOrder("3829499999999999")).thenThrow(
                AiplatformUpstreamException.from(
                        new OpenApiClientException(404, "{\"code\":5001,\"message\":\"订单不存在\",\"data\":null}")));

        assertThatThrownBy(() -> orderAppService.getDetail("3829499999999999"))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(404);
                    assertThat(upstream.envelopeCode()).isEqualTo(5001);
                    assertThat(upstream.getMessage()).isEqualTo("订单不存在");
                });
    }

    // ========== 源码包 · 二进制载体保全 ==========

    @Test
    void given_tarGzFromDownstream_when_getSourcePackage_then_bytesAndRawHeadersPreserved() {
        byte[] tarGz = {(byte) 0x1f, (byte) 0x8b, 0x08, 0x00, (byte) 0xff, 0x41, 0x00};
        HttpHeaders providerHeaders = HttpHeaders.of(Map.of(
                "Content-Type", List.of("application/gzip"),
                "Content-Disposition", List.of("attachment; filename=\"3829492001234567-source.tar.gz\"")),
                (a, b) -> true);
        when(aiplatformClient.downloadSourcePackage("3829492001234567"))
                .thenReturn(new BinaryResponse(200, providerHeaders, tarGz));

        var pkg = orderAppService.getSourcePackage("3829492001234567");

        // 字节逐位保全 + provider 响应头 raw 值透传（application/gzip + attachment 文件名）
        assertThat(pkg.content()).containsExactly(tarGz);
        assertThat(pkg.contentType()).isEqualTo("application/gzip");
        assertThat(pkg.contentDisposition()).isEqualTo("attachment; filename=\"3829492001234567-source.tar.gz\"");
    }

    // ========== 项目清单 · query→wire 映射（status 单选）+ 分页 1-based 透传 ==========

    @Test
    void given_projectQueryAndPage_when_list_then_wireRequestMappedAndPageEchoedFromProvider() {
        when(aiplatformClient.listProjects(any(), eq(3), eq(20))).thenReturn(new PageResponse<>(List.of(
                new AiplatformProjectSummaryWireResponse(
                        "3829492007654321", "英语学习助手", "文野",
                        1, "官网", 3, "已归档", true,
                        LocalDateTime.of(2026, 8, 1, 9, 0, 0),
                        LocalDateTime.of(2026, 9, 1, 12, 0, 0))), 7, 3, 20));

        PageResponse<AiplatformProjectSummaryResponse> page = projectAppService.list(
                new AiplatformProjectQuery(3,
                        LocalDateTime.of(2026, 9, 1, 0, 0, 0),
                        LocalDateTime.of(2026, 9, 15, 23, 59, 59),
                        "auth0|65f2c8a1", "3829492007654321"), 3, 20);

        // 出站参数：北向 query → wire 过滤记录逐字段映射（status 三档单选单值）；page 1-based 直传（3→3，无 ±1）
        ArgumentCaptor<AiplatformProjectListWireRequest> wireCaptor =
                ArgumentCaptor.forClass(AiplatformProjectListWireRequest.class);
        verify(aiplatformClient).listProjects(wireCaptor.capture(), eq(3), eq(20));
        assertThat(wireCaptor.getValue()).isEqualTo(new AiplatformProjectListWireRequest(
                3,
                LocalDateTime.of(2026, 9, 1, 0, 0, 0),
                LocalDateTime.of(2026, 9, 15, 23, 59, 59),
                "auth0|65f2c8a1", "3829492007654321"));

        // 回显取 provider 回报值；wire → response 逐字段（type/status Integer + *Name、archived Boolean）
        assertThat(page.total()).isEqualTo(7L);
        assertThat(page.page()).isEqualTo(3);
        var row = page.items().get(0);
        assertThat(row.id()).isEqualTo("3829492007654321");
        assertThat(row.type()).isEqualTo(1);
        assertThat(row.typeName()).isEqualTo("官网");
        assertThat(row.status()).isEqualTo(3);
        assertThat(row.statusName()).isEqualTo("已归档");
        assertThat(row.archived()).isTrue();
    }

    // ========== 项目详情 · 订单引用 + 成本指针映射 ==========

    @Test
    void given_projectDetailWire_when_getDetail_then_orderRefsAndCostSummaryMapped() {
        when(aiplatformClient.getProject("3829492007654321")).thenReturn(
                new AiplatformProjectDetailWireResponse(
                        "3829492007654321", "英语学习助手", "文野", "3829492009999999",
                        1, "官网", 1, "进行中", false,
                        LocalDateTime.of(2026, 9, 10, 14, 20, 0),
                        LocalDateTime.of(2026, 9, 14, 18, 30, 0),
                        LocalDateTime.of(2026, 9, 10, 20, 0, 0),
                        LocalDateTime.of(2026, 9, 11, 8, 0, 0),
                        new AiplatformOrderBriefWireResponse("3829492001234567", 2, "已报价"),
                        new AiplatformOrderBriefWireResponse("3829492005555444", 4, "已归档"),
                        new AiplatformProjectDetailWireResponse.CostSummary(
                                Map.of("CNY", new BigDecimal("12.3456")), true)));

        AiplatformProjectDetailResponse detail = projectAppService.getDetail("3829492007654321");

        // wire → response 逐字段：订单引用（activeOrder 未终结/latestOrder 最近）+ 成本指针（Map 原样 + unpriced）
        assertThat(detail.workspaceId()).isEqualTo("3829492009999999");
        assertThat(detail.activeOrder().id()).isEqualTo("3829492001234567");
        assertThat(detail.activeOrder().status()).isEqualTo(2);
        assertThat(detail.latestOrder().statusName()).isEqualTo("已归档");
        assertThat(detail.costSummary().cost()).containsEntry("CNY", new BigDecimal("12.3456"));
        assertThat(detail.costSummary().unpriced()).isTrue();
        assertThat(detail.prdProducedAt()).isEqualTo(LocalDateTime.of(2026, 9, 10, 20, 0, 0));
    }

    // ========== 深读三组 · 对话史 / PRD / 版本 ==========

    @Test
    void given_conversationWire_when_getConversation_then_kindsAndPayloadsMapped() {
        when(aiplatformClient.getConversation("3829492007654321")).thenReturn(List.of(
                new AiplatformConversationEntryWireResponse(
                        90001L, 1, "用户发言", null, "首页加一个轮播图",
                        null, null, List.of(Map.of("type", "circle")), false,
                        LocalDateTime.of(2026, 9, 12, 10, 0, 0)),
                new AiplatformConversationEntryWireResponse(
                        90003L, 5, "收尾卡", "run-abc123", null,
                        null, Map.of("runId", "run-abc123", "commitHash", "abc"), null, false,
                        LocalDateTime.of(2026, 9, 12, 11, 30, 0))));

        var entries = projectAppService.getConversation("3829492007654321");

        // kind Integer code + kindName 随行（aiplatform#186 provider 出口提供）；载荷 Map/List 原样透传
        assertThat(entries).hasSize(2);
        var user = entries.get(0);
        assertThat(user.kind()).isEqualTo(1);
        assertThat(user.kindName()).isEqualTo("用户发言");
        assertThat(user.attachments()).hasSize(1);
        var closing = entries.get(1);
        assertThat(closing.kind()).isEqualTo(5);
        assertThat(closing.closing()).containsEntry("commitHash", "abc");
    }

    @Test
    void given_prdAndVersionsWire_when_deepRead_then_mappedVerbatim() {
        when(aiplatformClient.getPrd("3829492007654321")).thenReturn(new AiplatformPrdWireResponse(
                "3829492007654321", "# PRD\n\n做一个英语学习助手……", Instant.parse("2026-09-12T08:30:00Z")));
        when(aiplatformClient.listVersions("3829492007654321")).thenReturn(List.of(
                new AiplatformVersionWireResponse(
                        "abc", "轮播图上线", "run-abc123", null, LocalDateTime.of(2026, 9, 12, 11, 30, 0))));
        when(aiplatformClient.getVersion("3829492007654321", "abc")).thenReturn(
                new AiplatformVersionDetailWireResponse(
                        "abc", "轮播图上线", "run-abc123", null,
                        LocalDateTime.of(2026, 9, 12, 11, 30, 0), Map.of("runId", "run-abc123")));

        // PRD：markdown 正文 + updatedAt Instant 原样
        var prd = projectAppService.getPrd("3829492007654321");
        assertThat(prd.content()).startsWith("# PRD");
        assertThat(prd.updatedAt()).isEqualTo(Instant.parse("2026-09-12T08:30:00Z"));

        // 版本列表：run 版本 runId 非空 / rollbackFrom null
        var versions = projectAppService.listVersions("3829492007654321");
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).runId()).isEqualTo("run-abc123");
        assertThat(versions.get(0).rollbackFrom()).isNull();

        // 版本详情：closing 载荷原样
        var version = projectAppService.getVersion("3829492007654321", "abc");
        assertThat(version.closing()).containsEntry("runId", "run-abc123");
    }

    // ========== 项目详情错误 · 透传不映射 ==========

    @Test
    void given_prj001FromDownstream_when_getProject_then_propagateWithoutMapping() {
        when(aiplatformClient.getProject("3829499999999999")).thenThrow(
                AiplatformUpstreamException.from(
                        new OpenApiClientException(404, "{\"code\":4001,\"message\":\"项目不存在\",\"data\":null}")));

        assertThatThrownBy(() -> projectAppService.getDetail("3829499999999999"))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(404);
                    assertThat(upstream.envelopeCode()).isEqualTo(4001);
                    assertThat(upstream.getMessage()).isEqualTo("项目不存在");
                });
    }

    // ========== 沙箱清单 · query→wire 映射（期望态/实态两维）+ 分页 1-based 透传 ==========

    @Test
    void given_workspaceQueryAndPage_when_list_then_wireRequestMappedAndPageEchoedFromProvider() {
        when(aiplatformClient.listWorkspaces(any(), eq(2), eq(20))).thenReturn(new PageResponse<>(List.of(
                new AiplatformWorkspaceSummaryWireResponse(
                        "3829492009999999", "ws-3829492009999999",
                        1, "开发", 2, "就绪", 1, "运行", 3, "无容器",
                        LocalDateTime.of(2026, 9, 14, 22, 10, 0),
                        2147483648L, null, null,
                        new AiplatformWorkspaceSummaryWireResponse.ProjectRef(
                                "3829492007654321", "英语学习助手", false))), 17, 2, 20));

        PageResponse<AiplatformWorkspaceSummaryResponse> page = workspaceAppService.list(
                new AiplatformWorkspaceQuery(1, 3), 2, 20);

        // 出站参数：北向 query → wire 过滤记录逐字段映射（漂移清单口径：desired=1 期望运行 + actual=3 实态无容器）；
        // page 1-based 直传（2→2，无 ±1）
        ArgumentCaptor<AiplatformWorkspaceListWireRequest> wireCaptor =
                ArgumentCaptor.forClass(AiplatformWorkspaceListWireRequest.class);
        verify(aiplatformClient).listWorkspaces(wireCaptor.capture(), eq(2), eq(20));
        assertThat(wireCaptor.getValue()).isEqualTo(new AiplatformWorkspaceListWireRequest(1, 3));

        // 回显取 provider 回报值；wire → response 逐字段（四枚举 code+*Name、卷大小 Long、项目引用嵌套）
        assertThat(page.total()).isEqualTo(17L);
        assertThat(page.page()).isEqualTo(2);
        var drift = page.items().get(0);
        assertThat(drift.workspaceId()).isEqualTo("3829492009999999");
        assertThat(drift.desiredState()).isEqualTo(1);
        assertThat(drift.desiredStateName()).isEqualTo("运行");
        assertThat(drift.containerState()).isEqualTo(3);
        assertThat(drift.containerStateName()).isEqualTo("无容器");
        assertThat(drift.volumeSizeBytes()).isEqualTo(2147483648L);
        assertThat(drift.project().projectId()).isEqualTo("3829492007654321");
        assertThat(drift.project().archived()).isFalse();
    }

    // ========== 沙箱详情 · 资源观测 + 项目引用映射 ==========

    @Test
    void given_workspaceDetailWire_when_getDetail_then_resourcesAndProjectRefMapped() {
        when(aiplatformClient.getWorkspace("3829492007777777")).thenReturn(
                new AiplatformWorkspaceDetailWireResponse(
                        "3829492007777777", "ws-3829492007777777", "net-3829492007777777",
                        1, "开发", 2, "就绪", null, 3, "封存", 3, "无容器",
                        LocalDateTime.of(2026, 9, 13, 18, 0, 0), null,
                        LocalDateTime.of(2026, 9, 13, 18, 0, 0),
                        "workspace-archives/3829492007777777.tar.gz", 89128960L,
                        LocalDateTime.of(2026, 9, 8, 10, 0, 0),
                        LocalDateTime.of(2026, 9, 13, 18, 0, 0),
                        List.of(new AiplatformWorkspaceDetailWireResponse.MiddlewareResourceObservation(
                                1, "mw-3829492007777777-pg",
                                "postgresql://aiedu:secret@mw-3829492007777777-pg:5432/aiedu")),
                        new AiplatformWorkspaceSummaryWireResponse.ProjectRef(
                                "3829492007654321", "英语学习助手", false)));

        AiplatformWorkspaceDetailResponse detail = workspaceAppService.getDetail("3829492007777777");

        // wire → response 逐字段：封存态全量（封存包寻址键/审计列）+ 资源观测 + 项目引用
        assertThat(detail.networkName()).isEqualTo("net-3829492007777777");
        assertThat(detail.provisionError()).isNull();
        assertThat(detail.desiredStateName()).isEqualTo("封存");
        assertThat(detail.archivePath()).isEqualTo("workspace-archives/3829492007777777.tar.gz");
        assertThat(detail.archiveSizeBytes()).isEqualTo(89128960L);
        assertThat(detail.resources()).hasSize(1);
        assertThat(detail.resources().get(0).kind()).isEqualTo(1);
        assertThat(detail.resources().get(0).internalUrl()).startsWith("postgresql://");
        assertThat(detail.project().name()).isEqualTo("英语学习助手");
    }

    // ========== 四干预动作 · 委托 + 回执映射 + 错误透传 ==========

    @Test
    void given_wakeReceipt_when_wake_then_delegatedAndDetailMapped() {
        when(aiplatformClient.wakeWorkspace("3829492009999999")).thenReturn(
                new AiplatformWorkspaceDetailWireResponse(
                        "3829492009999999", "ws-3829492009999999", "net-3829492009999999",
                        1, "开发", 2, "就绪", null, 1, "运行", 1, "运行中",
                        LocalDateTime.of(2026, 9, 16, 9, 0, 0), 1073741824L,
                        null, null, null,
                        LocalDateTime.of(2026, 9, 8, 10, 0, 0),
                        LocalDateTime.of(2026, 9, 16, 9, 0, 0),
                        List.of(), null));

        AiplatformWorkspaceDetailResponse receipt = workspaceAppService.wake("3829492009999999");

        // 回执＝动作后的观测详情：期望运行 + 实态运行中（收敛完成的新事实）
        assertThat(receipt.desiredState()).isEqualTo(1);
        assertThat(receipt.containerState()).isEqualTo(1);
        assertThat(receipt.containerStateName()).isEqualTo("运行中");
        assertThat(receipt.project()).isNull();
    }

    @Test
    void given_wsp015FromDownstream_when_hibernate_then_propagateWithoutMapping() {
        when(aiplatformClient.hibernateWorkspace("3829492009999999")).thenThrow(
                AiplatformUpstreamException.from(
                        new OpenApiClientException(409,
                                "{\"code\":1015,\"message\":\"编码 run 进行中，沙箱动作被拒（先取消 run 或等收口）\",\"data\":null}")));

        assertThatThrownBy(() -> workspaceAppService.hibernate("3829492009999999"))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(409);
                    assertThat(upstream.envelopeCode()).isEqualTo(1015);
                    assertThat(upstream.getMessage()).isEqualTo("编码 run 进行中，沙箱动作被拒（先取消 run 或等收口）");
                });
    }
}
