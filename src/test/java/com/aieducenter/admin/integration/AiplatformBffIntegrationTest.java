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
import com.aieducenter.admin.aiplatform.application.AiplatformCostAppService;
import com.aieducenter.admin.aiplatform.application.AiplatformMaterialAppService;
import com.aieducenter.admin.aiplatform.application.AiplatformOrderAppService;
import com.aieducenter.admin.aiplatform.application.AiplatformPriceEntryAppService;
import com.aieducenter.admin.aiplatform.application.AiplatformProjectAppService;
import com.aieducenter.admin.aiplatform.application.AiplatformUpstreamException;
import com.aieducenter.admin.aiplatform.application.AiplatformWorkspaceAppService;
import com.aieducenter.admin.aiplatform.application.dto.command.AiplatformOrderCancelCommand;
import com.aieducenter.admin.aiplatform.application.dto.command.AiplatformOrderQuoteCommand;
import com.aieducenter.admin.aiplatform.application.dto.command.AiplatformPriceEntryRepriceCommand;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformCostQuery;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformMaterialQuery;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformOrderQuery;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformPriceEntryQuery;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformProjectQuery;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformWorkspaceQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformAccountProfileResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformCostOverviewResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformMaterialDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformMaterialSummaryResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformOrderResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformOrderSummaryResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectCostDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectCostResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformUnitPriceEntryRepriceResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformUnitPriceEntryResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformUnpricedUsageResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectSummaryResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformWorkspaceDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformWorkspaceSummaryResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformAccountProfileWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformCostOverviewWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformCostWindowWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformConversationEntryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformMaterialDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformMaterialListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformMaterialSummaryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderBriefWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderCancelWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderQuoteWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderSummaryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformPriceEntryListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformPriceEntryRepriceWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformTokenUsageWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformUnitPriceEntryRepriceWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformUnitPriceEntryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformUnpricedUsageWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformPrdWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectCostDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectCostWireResponse;
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
 * #66 沙箱观测与四干预动作 + #67 成本四读口 + #68 单价表清单/原子改价/停用 + #69 知识素材
 * 清单/详情/停用⇄启用/删除 + #70 订单写路径报价/改价/取消/重试归档）——mock {@link AiplatformClient}，
 * 验证 AppService 在 Spring 上下文
 * 中的完整接线（DI、query→wire 映射、wire→response DTO 映射、分页 1-based 透传、二进制载体
 * 保全、下游错误透传不映射）。
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

    @Autowired
    private AiplatformCostAppService costAppService;

    @Autowired
    private AiplatformPriceEntryAppService priceEntryAppService;

    @Autowired
    private AiplatformMaterialAppService materialAppService;

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

    // ========== 写路径三操作 · 命令映射 + 用户面同构回执映射 ==========

    @Test
    void given_quoteReceipt_when_quote_then_wireCommandMappedAndReceiptMapped() {
        when(aiplatformClient.quoteOrder(eq("3829492001234567"), any())).thenReturn(orderReceiptWire(
                2, "已报价", null, null, null, List.of(
                        new AiplatformOrderWireResponse.PriceEntry(
                                "3829492011111111", 2199000L, "CNY", "含加急费用",
                                LocalDateTime.of(2026, 9, 12, 10, 0, 0)),
                        new AiplatformOrderWireResponse.PriceEntry(
                                "3829492009999999", 1999000L, "CNY", "首次报价",
                                LocalDateTime.of(2026, 9, 11, 9, 0, 0)))));

        AiplatformOrderResponse receipt = orderAppService.quote("3829492001234567",
                new AiplatformOrderQuoteCommand(2199000L, "含加急费用"));

        // 出站参数：北向命令 → wire 命令体逐字段映射（Long 分/null note 原值，不代填）
        ArgumentCaptor<AiplatformOrderQuoteWireRequest> commandCaptor =
                ArgumentCaptor.forClass(AiplatformOrderQuoteWireRequest.class);
        verify(aiplatformClient).quoteOrder(eq("3829492001234567"), commandCaptor.capture());
        assertThat(commandCaptor.getValue())
                .isEqualTo(new AiplatformOrderQuoteWireRequest(2199000L, "含加急费用"));

        // wire → response 逐字段：现值取最新价目行；quotedAt 仍是首次报价时点（改价不刷新）；
        // 价目历史五字段（无操作者留痕——用户面同构回执）
        assertThat(receipt.status()).isEqualTo(2);
        assertThat(receipt.statusName()).isEqualTo("已报价");
        assertThat(receipt.amount()).isEqualTo(2199000L);
        assertThat(receipt.note()).isEqualTo("含加急费用");
        assertThat(receipt.quotedAt()).isEqualTo(LocalDateTime.of(2026, 9, 11, 9, 0, 0));
        assertThat(receipt.priceEntries()).hasSize(2);
        assertThat(receipt.priceEntries().get(0).id()).isEqualTo("3829492011111111");
        assertThat(receipt.priceEntries().get(0).amount()).isEqualTo(2199000L);
        assertThat(receipt.priceEntries().get(1).note()).isEqualTo("首次报价");
        assertThat(receipt.cancelledAt()).isNull();
        assertThat(receipt.paidAt()).isNull();
        assertThat(receipt.archivedAt()).isNull();
    }

    @Test
    void given_cancelReceipt_when_cancel_then_wireCommandMappedAndCancelledReceiptMapped() {
        when(aiplatformClient.cancelOrder(eq("3829492001234567"), any())).thenReturn(orderReceiptWire(
                5, "已取消", LocalDateTime.of(2026, 9, 16, 15, 0, 0), null, null, List.of(
                        new AiplatformOrderWireResponse.PriceEntry(
                                "3829492011111111", 2199000L, "CNY", "含加急费用",
                                LocalDateTime.of(2026, 9, 12, 10, 0, 0)))));

        AiplatformOrderResponse receipt = orderAppService.cancel("3829492001234567",
                new AiplatformOrderCancelCommand("重复下单，用户要求取消"));

        // 出站参数：北向命令 → wire 命令体逐字段映射（reason 原值）
        ArgumentCaptor<AiplatformOrderCancelWireRequest> commandCaptor =
                ArgumentCaptor.forClass(AiplatformOrderCancelWireRequest.class);
        verify(aiplatformClient).cancelOrder(eq("3829492001234567"), commandCaptor.capture());
        assertThat(commandCaptor.getValue())
                .isEqualTo(new AiplatformOrderCancelWireRequest("重复下单，用户要求取消"));

        // 已取消终态回执：cancelledAt 落定，报价事实保留
        assertThat(receipt.status()).isEqualTo(5);
        assertThat(receipt.statusName()).isEqualTo("已取消");
        assertThat(receipt.cancelledAt()).isEqualTo(LocalDateTime.of(2026, 9, 16, 15, 0, 0));
        assertThat(receipt.amount()).isEqualTo(2199000L);
        assertThat(receipt.priceEntries()).hasSize(1);
    }

    @Test
    void given_archiveReceipt_when_retryArchive_then_archivedReceiptMapped() {
        when(aiplatformClient.retryArchiveOrder("3829492001234567")).thenReturn(orderReceiptWire(
                4, "已归档", null, LocalDateTime.of(2026, 9, 15, 20, 0, 0),
                LocalDateTime.of(2026, 9, 16, 9, 30, 0), List.of()));

        AiplatformOrderResponse receipt = orderAppService.retryArchive("3829492001234567");

        // 已归档终态回执：paidAt/archivedAt 双时点（支付成功在前、补归档在后）；无请求体无命令映射
        assertThat(receipt.status()).isEqualTo(4);
        assertThat(receipt.statusName()).isEqualTo("已归档");
        assertThat(receipt.paidAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 20, 0, 0));
        assertThat(receipt.archivedAt()).isEqualTo(LocalDateTime.of(2026, 9, 16, 9, 30, 0));
        assertThat(receipt.cancelledAt()).isNull();
        assertThat(receipt.priceEntries()).isEmpty();
    }

    @Test
    void given_ord007FromDownstream_when_quote_then_propagateWithoutMapping() {
        when(aiplatformClient.quoteOrder(eq("3829492001234567"), any())).thenThrow(
                AiplatformUpstreamException.from(new OpenApiClientException(409,
                        "{\"code\":5007,\"message\":\"订单已支付或已终结，无法报价或改价\",\"data\":null}")));

        assertThatThrownBy(() -> orderAppService.quote("3829492001234567",
                new AiplatformOrderQuoteCommand(2199000L, "含加急费用")))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    // ORD_007 → HTTP 409 + 数字业务码 5007（域码 5×1000＋7）
                    assertThat(upstream.httpStatus()).isEqualTo(409);
                    assertThat(upstream.envelopeCode()).isEqualTo(5007);
                    assertThat(upstream.getMessage()).isEqualTo("订单已支付或已终结，无法报价或改价");
                });
    }

    /**
     * 三写操作共用回执 wire 工厂：按动作后的终态（status/时点组）参数化，固定订单标识与
     * 报价事实（金额/备注取最新价目行、quotedAt 首次报价时点）。paidAt/ cancelledAt/archivedAt
     * 全参数化——各终态只落自己的时点（改价/取消单未支付 paidAt=null，归档单双时点俱全）。
     */
    private static AiplatformOrderWireResponse orderReceiptWire(
            Integer status, String statusName,
            LocalDateTime cancelledAt, LocalDateTime paidAt, LocalDateTime archivedAt,
            List<AiplatformOrderWireResponse.PriceEntry> priceEntries) {
        return new AiplatformOrderWireResponse(
                "3829492001234567", "3829492007654321", status, statusName,
                2199000L, "CNY", "含加急费用", LocalDateTime.of(2026, 9, 11, 9, 0, 0),
                priceEntries, LocalDateTime.of(2026, 9, 10, 14, 20, 0),
                cancelledAt, paidAt, archivedAt);
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

    // ========== 成本总览 · query→wire 映射 + 分解嵌套映射 ==========

    @Test
    void given_costWindow_when_getOverview_then_windowMappedAndBreakdownsMapped() {
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-16T00:00:00Z");
        when(aiplatformClient.getCostOverview(new AiplatformCostWindowWireRequest(from, to)))
                .thenReturn(new AiplatformCostOverviewWireResponse(
                        from, to,
                        new AiplatformTokenUsageWireResponse(5000, 1200, 300, 0, 800),
                        Map.of("CNY", new BigDecimal("12.3456")),
                        List.of(new AiplatformCostOverviewWireResponse.ModelUsage(
                                "anthropic", "claude-fable-5",
                                new AiplatformTokenUsageWireResponse(3000, 1000, 300, 0, 800))),
                        List.of(new AiplatformCostOverviewWireResponse.AgentKindUsage(
                                "naming", null,
                                new AiplatformTokenUsageWireResponse(1000, 200, 0, 0, 0)))));

        AiplatformCostOverviewResponse overview = costAppService.overview(new AiplatformCostQuery(from, to));

        // 出站参数：北向 query → wire 时间窗逐字段映射（from/to Instant 原值）
        verify(aiplatformClient).getCostOverview(new AiplatformCostWindowWireRequest(from, to));

        // wire → response 逐字段：窗口回显 + 五档总量 + 币种分桶 + 双分解
        // （agentKind 裸维度串原值透传、辅助标记 agentKindName=null 不臆造）
        assertThat(overview.from()).isEqualTo(from);
        assertThat(overview.to()).isEqualTo(to);
        assertThat(overview.total().input()).isEqualTo(5000L);
        assertThat(overview.total().reasoning()).isEqualTo(800L);
        assertThat(overview.cost()).containsEntry("CNY", new BigDecimal("12.3456"));
        assertThat(overview.byModel().get(0).model()).isEqualTo("claude-fable-5");
        assertThat(overview.byModel().get(0).tokens().cacheRead()).isEqualTo(300L);
        assertThat(overview.byAgentKind().get(0).agentKind()).isEqualTo("naming");
        assertThat(overview.byAgentKind().get(0).agentKindName()).isNull();
    }

    // ========== unpriced 警示 · 档位映射 ==========

    @Test
    void given_unpricedWire_when_getUnpriced_then_tiersMapped() {
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-16T00:00:00Z");
        when(aiplatformClient.getUnpricedUsage(new AiplatformCostWindowWireRequest(from, to)))
                .thenReturn(new AiplatformUnpricedUsageWireResponse(
                        from, to,
                        List.of(new AiplatformUnpricedUsageWireResponse.UnpricedTier(
                                "openai", "gpt-5.2", 1, "输入", 700))));

        AiplatformUnpricedUsageResponse unpriced = costAppService.unpriced(new AiplatformCostQuery(from, to));

        // tokenKind Integer code + tokenKindName + tokens 只计无价分量
        assertThat(unpriced.items()).hasSize(1);
        assertThat(unpriced.items().get(0).tokenKind()).isEqualTo(1);
        assertThat(unpriced.items().get(0).tokenKindName()).isEqualTo("输入");
        assertThat(unpriced.items().get(0).tokens()).isEqualTo(700L);
    }

    // ========== 项目成本清单 · query→wire + 分页 1-based 透传 ==========

    @Test
    void given_costWindowAndPage_when_listProjectCosts_then_pageEchoedFromProvider() {
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-16T00:00:00Z");
        when(aiplatformClient.listProjectCosts(any(), eq(1), eq(20))).thenReturn(new PageResponse<>(List.of(
                new AiplatformProjectCostWireResponse(
                        "3829492007654321",
                        new AiplatformTokenUsageWireResponse(3000, 1000, 300, 0, 800),
                        Map.of("CNY", new BigDecimal("12.3456")), false),
                new AiplatformProjectCostWireResponse(
                        "3829492005555444",
                        new AiplatformTokenUsageWireResponse(1000, 200, 0, 0, 0),
                        Map.of(), true)), 2, 1, 20));

        PageResponse<AiplatformProjectCostResponse> page =
                costAppService.listProjectCosts(new AiplatformCostQuery(from, to), 1, 20);

        // 出站参数：北向 query → wire 时间窗映射；page 1-based 直传（1→1，无 ±1）
        verify(aiplatformClient).listProjectCosts(
                eq(new AiplatformCostWindowWireRequest(from, to)), eq(1), eq(20));

        // 回显取 provider 回报值；wire → response 逐字段（全未配价行 cost 空 + allUnpriced=true）
        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.page()).isEqualTo(1);
        var costly = page.items().get(0);
        assertThat(costly.projectId()).isEqualTo("3829492007654321");
        assertThat(costly.total().input()).isEqualTo(3000L);
        assertThat(costly.allUnpriced()).isFalse();
        var allUnpriced = page.items().get(1);
        assertThat(allUnpriced.cost()).isEmpty();
        assertThat(allUnpriced.allUnpriced()).isTrue();
    }

    // ========== 单项目下钻 · 分解 + unpriced 档位（无计数）映射 ==========

    @Test
    void given_projectCostDetailWire_when_getProjectCostDetail_then_breakdownsMapped() {
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-16T00:00:00Z");
        when(aiplatformClient.getProjectCostDetail(eq("3829492007654321"),
                eq(new AiplatformCostWindowWireRequest(from, to))))
                .thenReturn(new AiplatformProjectCostDetailWireResponse(
                        "3829492007654321", from, to,
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

        AiplatformProjectCostDetailResponse detail =
                costAppService.getProjectCostDetail("3829492007654321", new AiplatformCostQuery(from, to));

        // wire → response 逐字段：subject/窗口回显 + unpriced 档位（bySubject 口径无计数）+ 双分解
        assertThat(detail.projectId()).isEqualTo("3829492007654321");
        assertThat(detail.from()).isEqualTo(from);
        assertThat(detail.cost()).containsEntry("CNY", new BigDecimal("12.3456"));
        assertThat(detail.unpriced().get(0).tokenKind()).isEqualTo(1);
        assertThat(detail.unpriced().get(0).tokenKindName()).isEqualTo("输入");
        assertThat(detail.byModel().get(0).provider()).isEqualTo("anthropic");
        assertThat(detail.byAgentKind().get(0).agentKind()).isEqualTo("executor");
        assertThat(detail.byAgentKind().get(0).agentKindName()).isEqualTo("执行智能体");
    }

    // ========== 成本域错误 · 透传不映射 ==========

    @Test
    void given_meter011FromDownstream_when_getOverview_then_propagateWithoutMapping() {
        when(aiplatformClient.getCostOverview(any())).thenThrow(
                AiplatformUpstreamException.from(
                        new OpenApiClientException(400, "{\"code\":3011,\"message\":\"无效的成本查询参数\",\"data\":null}")));

        assertThatThrownBy(() -> costAppService.overview(
                new AiplatformCostQuery(Instant.parse("2026-09-01T00:00:00Z"),
                        Instant.parse("2026-09-16T00:00:00Z"))))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    // METER_011 → HTTP 400 + 数字业务码 3011（域码 3×1000＋11）
                    assertThat(upstream.httpStatus()).isEqualTo(400);
                    assertThat(upstream.envelopeCode()).isEqualTo(3011);
                    assertThat(upstream.getMessage()).isEqualTo("无效的成本查询参数");
                });
    }

    // ========== 单价行清单 · query→wire 映射（provider/model 精确过滤）+ 分页 1-based 透传 ==========

    @Test
    void given_priceEntryQueryAndPage_when_list_then_wireRequestMappedAndPageEchoedFromProvider() {
        when(aiplatformClient.listPriceEntries(any(), eq(2), eq(50))).thenReturn(new PageResponse<>(List.of(
                new AiplatformUnitPriceEntryWireResponse(
                        "3830100002222222", "anthropic", "claude-fable-5", 1, "输入",
                        "0.000002", "USD",
                        Instant.parse("2026-08-01T00:00:00Z"), null, "700160", "运营·单价管理员"),
                new AiplatformUnitPriceEntryWireResponse(
                        "3830100001111111", "anthropic", "claude-fable-5", 1, "输入",
                        "0.00000132", "USD",
                        Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"),
                        null, null)), 12, 2, 50));

        PageResponse<AiplatformUnitPriceEntryResponse> page = priceEntryAppService.list(
                new AiplatformPriceEntryQuery("anthropic", "claude-fable-5"), 2, 50);

        // 出站参数：北向 query → wire 过滤记录逐字段映射；page 1-based 直传（2→2，无 ±1）
        ArgumentCaptor<AiplatformPriceEntryListWireRequest> wireCaptor =
                ArgumentCaptor.forClass(AiplatformPriceEntryListWireRequest.class);
        verify(aiplatformClient).listPriceEntries(wireCaptor.capture(), eq(2), eq(50));
        assertThat(wireCaptor.getValue())
                .isEqualTo(new AiplatformPriceEntryListWireRequest("anthropic", "claude-fable-5"));

        // 回显取 provider 回报值；wire → response 逐字段——首行当前行（effectiveTo=null、留痕两肢），
        // 次行历史行（区间两端俱全、存量形制操作者 null）
        assertThat(page.total()).isEqualTo(12L);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(50);
        var current = page.items().get(0);
        assertThat(current.id()).isEqualTo("3830100002222222");
        assertThat(current.tokenKind()).isEqualTo(1);
        assertThat(current.tokenKindName()).isEqualTo("输入");
        assertThat(current.unitPrice()).isEqualTo("0.000002");
        assertThat(current.effectiveTo()).isNull();
        assertThat(current.operatorName()).isEqualTo("运营·单价管理员");
        var historical = page.items().get(1);
        assertThat(historical.effectiveTo()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(historical.operatorId()).isNull();
    }

    // ========== 原子改价 · 命令映射 + closed/opened 双行回执映射 ==========

    @Test
    void given_repriceCommand_when_reprice_then_wireCommandMappedAndReceiptMapped() {
        when(aiplatformClient.repricePriceEntry(eq("3830100002222222"), any())).thenReturn(
                new AiplatformUnitPriceEntryRepriceWireResponse(
                        new AiplatformUnitPriceEntryWireResponse(
                                "3830100002222222", "anthropic", "claude-fable-5", 1, "输入",
                                "0.000002", "USD",
                                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-20T00:00:00Z"),
                                "700160", "运营·单价管理员"),
                        new AiplatformUnitPriceEntryWireResponse(
                                "3830100003333333", "anthropic", "claude-fable-5", 1, "输入",
                                "0.0000018", "USD",
                                Instant.parse("2026-09-20T00:00:00Z"), null,
                                "3829492001234567", "调价运营")));

        AiplatformUnitPriceEntryRepriceResponse receipt = priceEntryAppService.reprice(
                "3830100002222222", new AiplatformPriceEntryRepriceCommand(
                        new BigDecimal("0.0000018"), "USD", Instant.parse("2026-09-20T00:00:00Z")));

        // 出站参数：北向命令 → wire 命令体逐字段映射（BigDecimal/Instant 原值；null 不代填）
        ArgumentCaptor<AiplatformPriceEntryRepriceWireRequest> commandCaptor =
                ArgumentCaptor.forClass(AiplatformPriceEntryRepriceWireRequest.class);
        verify(aiplatformClient).repricePriceEntry(eq("3830100002222222"), commandCaptor.capture());
        assertThat(commandCaptor.getValue()).isEqualTo(new AiplatformPriceEntryRepriceWireRequest(
                new BigDecimal("0.0000018"), "USD", Instant.parse("2026-09-20T00:00:00Z")));

        // wire → response 逐字段：closed 落 effectiveTo、保留原开行留痕；opened 新 id、敞口、带改价操作者
        assertThat(receipt.closed().effectiveTo()).isEqualTo(Instant.parse("2026-09-20T00:00:00Z"));
        assertThat(receipt.closed().operatorId()).isEqualTo("700160");
        assertThat(receipt.opened().id()).isEqualTo("3830100003333333");
        assertThat(receipt.opened().unitPrice()).isEqualTo("0.0000018");
        assertThat(receipt.opened().effectiveTo()).isNull();
        assertThat(receipt.opened().operatorName()).isEqualTo("调价运营");
    }

    // ========== 停用 · 单行回执映射 ==========

    @Test
    void given_deactivateReceipt_when_deactivate_then_singleRowMapped() {
        when(aiplatformClient.deactivatePriceEntry("3830100002222222")).thenReturn(
                new AiplatformUnitPriceEntryWireResponse(
                        "3830100002222222", "anthropic", "claude-fable-5", 1, "输入",
                        "0.000002", "USD",
                        Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-16T10:30:00Z"),
                        "3829492001234567", "调价运营"));

        AiplatformUnitPriceEntryResponse closed = priceEntryAppService.deactivate("3830100002222222");

        // 被关行单行回执：effectiveTo 已落、停用操作者落被关行（唯一落点）
        assertThat(closed.effectiveTo()).isEqualTo(Instant.parse("2026-09-16T10:30:00Z"));
        assertThat(closed.operatorId()).isEqualTo("3829492001234567");
        assertThat(closed.operatorName()).isEqualTo("调价运营");
    }

    // ========== 单价表域错误 · 透传不映射 ==========

    @Test
    void given_meter008FromDownstream_when_reprice_then_propagateWithoutMapping() {
        when(aiplatformClient.repricePriceEntry(eq("3830100002222222"), any())).thenThrow(
                AiplatformUpstreamException.from(new OpenApiClientException(409,
                        "{\"code\":3008,\"message\":\"同匹配键生效区间重叠（跨区间或同起点）\",\"data\":null}")));

        assertThatThrownBy(() -> priceEntryAppService.reprice("3830100002222222",
                new AiplatformPriceEntryRepriceCommand(
                        new BigDecimal("0.0000018"), "USD", Instant.parse("2026-09-20T00:00:00Z"))))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    // METER_008 → HTTP 409 + 数字业务码 3008（域码 3×1000＋8）
                    assertThat(upstream.httpStatus()).isEqualTo(409);
                    assertThat(upstream.envelopeCode()).isEqualTo(3008);
                    assertThat(upstream.getMessage()).isEqualTo("同匹配键生效区间重叠（跨区间或同起点）");
                });
    }

    // ========== 知识素材清单 · query→wire 映射（三维过滤）+ 分页 1-based 透传 ==========

    @Test
    void given_materialQueryAndPage_when_list_then_wireRequestMappedAndPageEchoedFromProvider() {
        Instant sunkFrom = Instant.parse("2026-09-01T00:00:00Z");
        Instant sunkTo = Instant.parse("2026-09-15T23:59:59Z");
        when(aiplatformClient.listMaterials(any(), eq(2), eq(20))).thenReturn(new PageResponse<>(List.of(
                new AiplatformMaterialSummaryWireResponse(
                        "3840600001111111", "PRD", "3829492007654321", "英语学习助手",
                        "英语学习助手 · PRD", 2, "停用",
                        Instant.parse("2026-09-01T08:30:00Z"),
                        "3829492001234567", "内容治理运营"),
                new AiplatformMaterialSummaryWireResponse(
                        "3840600002222222", "PRD", "3829492005555444", "待办清单应用",
                        "待办清单应用 · PRD", 1, "启用",
                        Instant.parse("2026-08-20T12:00:00Z"),
                        null, null)), 9, 2, 20));

        PageResponse<AiplatformMaterialSummaryResponse> page = materialAppService.list(
                new AiplatformMaterialQuery(2, sunkFrom, sunkTo, "3829492007654321"), 2, 20);

        // 出站参数：北向 query → wire 过滤记录逐字段映射（status 单选 + 沉淀时间闭区间 + projectId 精确）；
        // page 1-based 直传（2→2，无 ±1）
        ArgumentCaptor<AiplatformMaterialListWireRequest> wireCaptor =
                ArgumentCaptor.forClass(AiplatformMaterialListWireRequest.class);
        verify(aiplatformClient).listMaterials(wireCaptor.capture(), eq(2), eq(20));
        assertThat(wireCaptor.getValue()).isEqualTo(new AiplatformMaterialListWireRequest(
                2, sunkFrom, sunkTo, "3829492007654321"));

        // 回显取 provider 回报值；wire → response 逐字段——首行已治理（status Integer + *Name、
        // 操作者留痕两肢），次行未治理（操作者两列 null 如实映射）
        assertThat(page.total()).isEqualTo(9L);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(20);
        var governed = page.items().get(0);
        assertThat(governed.id()).isEqualTo("3840600001111111");
        assertThat(governed.kind()).isEqualTo("PRD");
        assertThat(governed.projectId()).isEqualTo("3829492007654321");
        assertThat(governed.projectName()).isEqualTo("英语学习助手");
        assertThat(governed.status()).isEqualTo(2);
        assertThat(governed.statusName()).isEqualTo("停用");
        assertThat(governed.sunkAt()).isEqualTo(Instant.parse("2026-09-01T08:30:00Z"));
        assertThat(governed.operatorId()).isEqualTo("3829492001234567");
        var untreated = page.items().get(1);
        assertThat(untreated.status()).isEqualTo(1);
        assertThat(untreated.statusName()).isEqualTo("启用");
        assertThat(untreated.operatorId()).isNull();
        assertThat(untreated.operatorName()).isNull();
    }

    // ========== 素材详情 · 元数据 + PRD 全文映射 ==========

    @Test
    void given_materialDetailWire_when_getDetail_then_metadataAndContentMapped() {
        when(aiplatformClient.getMaterial("3840600001111111")).thenReturn(
                new AiplatformMaterialDetailWireResponse(
                        "3840600001111111", "PRD", "3829492007654321", "英语学习助手",
                        "英语学习助手 · PRD", 1, "启用",
                        Instant.parse("2026-09-01T08:30:00Z"),
                        null, null,
                        "# PRD\n\n做一个英语学习助手……\n\n## 功能范围\n\n1. 单词卡片\n2. 复习计划"));

        AiplatformMaterialDetailResponse detail = materialAppService.getDetail("3840600001111111");

        // wire → response 逐字段：元数据（与清单行同形）＋全文 content 原样到达（块拼接形态不改写）
        assertThat(detail.id()).isEqualTo("3840600001111111");
        assertThat(detail.kind()).isEqualTo("PRD");
        assertThat(detail.status()).isEqualTo(1);
        assertThat(detail.statusName()).isEqualTo("启用");
        assertThat(detail.sunkAt()).isEqualTo(Instant.parse("2026-09-01T08:30:00Z"));
        assertThat(detail.operatorId()).isNull();
        assertThat(detail.content()).startsWith("# PRD");
        assertThat(detail.content()).contains("\n\n## 功能范围\n\n1. 单词卡片\n2. 复习计划");
    }

    // ========== 三治理动作 · 委托 + 回执映射（删除＝删除前终态） ==========

    @Test
    void given_governanceReceipts_when_disableEnableDelete_then_summaryReceiptsMapped() {
        when(aiplatformClient.disableMaterial("3840600001111111")).thenReturn(
                new AiplatformMaterialSummaryWireResponse(
                        "3840600001111111", "PRD", "3829492007654321", "英语学习助手",
                        "英语学习助手 · PRD", 2, "停用",
                        Instant.parse("2026-09-01T08:30:00Z"),
                        "3829492001234567", "内容治理运营"));
        when(aiplatformClient.enableMaterial("3840600001111111")).thenReturn(
                new AiplatformMaterialSummaryWireResponse(
                        "3840600001111111", "PRD", "3829492007654321", "英语学习助手",
                        "英语学习助手 · PRD", 1, "启用",
                        Instant.parse("2026-09-01T08:30:00Z"),
                        "3829492001234567", "内容治理运营"));
        when(aiplatformClient.deleteMaterial("3840600001111111")).thenReturn(
                new AiplatformMaterialSummaryWireResponse(
                        "3840600001111111", "PRD", "3829492007654321", "英语学习助手",
                        "英语学习助手 · PRD", 2, "停用",
                        Instant.parse("2026-09-01T08:30:00Z"),
                        "3829492001234567", "内容治理运营"));

        // 停用回执：provider 重读登记行——已停用 + 操作者落素材级
        AiplatformMaterialSummaryResponse disabled = materialAppService.disable("3840600001111111");
        assertThat(disabled.status()).isEqualTo(2);
        assertThat(disabled.statusName()).isEqualTo("停用");
        assertThat(disabled.operatorId()).isEqualTo("3829492001234567");
        assertThat(disabled.operatorName()).isEqualTo("内容治理运营");

        // 启用回执：恢复启用（可逆开关的另一侧）
        AiplatformMaterialSummaryResponse enabled = materialAppService.enable("3840600001111111");
        assertThat(enabled.status()).isEqualTo(1);
        assertThat(enabled.statusName()).isEqualTo("启用");

        // 删除回执：删除前终态（本例是一行已停用素材——留痕原样在场，确认移除了什么）
        AiplatformMaterialSummaryResponse deleted = materialAppService.delete("3840600001111111");
        assertThat(deleted.id()).isEqualTo("3840600001111111");
        assertThat(deleted.status()).isEqualTo(2);
        assertThat(deleted.operatorName()).isEqualTo("内容治理运营");
    }

    // ========== 知识素材域错误 · 透传不映射 ==========

    @Test
    void given_knw006FromDownstream_when_disable_then_propagateWithoutMapping() {
        when(aiplatformClient.disableMaterial("3840600001111111")).thenThrow(
                AiplatformUpstreamException.from(new OpenApiClientException(400,
                        "{\"code\":2006,\"message\":\"操作者不能为空\",\"data\":null}")));

        assertThatThrownBy(() -> materialAppService.disable("3840600001111111"))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    // KNW_006 → HTTP 400 + 数字业务码 2006（域码 2×1000＋6，操作者必留痕守卫）
                    assertThat(upstream.httpStatus()).isEqualTo(400);
                    assertThat(upstream.envelopeCode()).isEqualTo(2006);
                    assertThat(upstream.getMessage()).isEqualTo("操作者不能为空");
                });
    }
}
