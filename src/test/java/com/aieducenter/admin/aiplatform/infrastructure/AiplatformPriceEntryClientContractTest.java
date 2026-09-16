package com.aieducenter.admin.aiplatform.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aieducenter.admin.aiplatform.application.AiplatformUpstreamException;
import com.aieducenter.admin.aiplatform.application.dto.command.AiplatformPriceEntryRepriceCommand;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformPriceEntryQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformUnitPriceEntryRepriceResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformUnitPriceEntryResponse;
import com.cartisan.web.response.PageResponse;

/**
 * aiplatform 单价表域（清单含历史行 / 原子改价 / 停用——开行不暴露）的 wire 契约测试
 * （对接 aiplatform #160/#165 契约，issue #68）。
 *
 * <p>契约事实（2026-09-16 对照 aiplatform 源码 {@code BackofficePriceEntryController} /
 * {@code UnitPriceEntryResponse} / {@code UnitPriceEntryRepriceResponse} /
 * {@code RepricePriceEntryCommand} / {@code MeteringMessage} / {@code BackofficePriceEntrySeamTest}
 * 核实）：</p>
 * <ul>
 *   <li>三端点均包 {@code ApiResponse<T>} 信封，按信封反序列化取 {@code .data()}；清单 data 为
 *       {@code PageResponse{items,total,page,size}}（page 1-based、total JSON string——cartisan-web
 *       全局 Long→ToStringSerializer），改价回执 data 为 {@code {closed, opened}} 双行，停用回执
 *       data 为被关行单行。</li>
 *   <li>行字段：{@code id} String（TSID 十进制串）；{@code tokenKind} Integer code（1=输入 2=输出
 *       3=缓存读 4=缓存写 5=推理）+ {@code tokenKindName} 中文名随行；{@code unitPrice} String 精确
 *       十进制串（provider {@code stripTrailingZeros().toPlainString()}——直出 JSON 数值会落科学
 *       计数）；{@code effectiveTo} null＝当前行；{@code operatorId/operatorName} 留痕（存量行/
 *       种子脚本种入行为 null）。</li>
 *   <li>清单 provider/model 精确等值过滤、均可缺省；排序定死生效起点倒序；page 1-based 直传，
 *       provider clamp（page≥1、size∈[1,100] 默认 20）行为透传。</li>
 *   <li>改价命令体 {@code {unitPrice, currency, effectiveFrom}}：unitPrice BigDecimal→JSON 明文
 *       小数；effectiveFrom Instant→ISO-8601（UTC 带 Z），null 照常序列化在场——「缺省即时」由
 *       provider 裁决（null→now()），BFF 不代填时点。停用无请求体（null body）。</li>
 *   <li>错误透传（数字业务码＝域码 3×1000＋序号）：METER_004→3004（400）、METER_005→3005（400）、
 *       METER_006→3006（404，含畸形 id——provider lenient 解析）、METER_007→3007（409）、
 *       METER_008→3008（409）、METER_009→3009（400）、METER_010→3010（400）。</li>
 *   <li>provider 的「开行」端点（POST /price-entries，#165 种子脚本通道）不建北向（spec #62）——
 *       {@code AiplatformClient} 无对应方法，wire 面无从误调。</li>
 * </ul>
 *
 * <p>本测试 <strong>不</strong> mock {@code AiplatformClient}（方法边界 mock 会绕过 wire 反序列化
 * ——已知反例），经 {@link AiplatformWireTestSupport} 子类化 {@code OpenApiClient} 仅替换 HTTP
 * 传输，真实 {@code TypeReference} 反序列化 + 真实 AppService 映射完整保留；改价命令体在 stub
 * 里按生产 mapper 口径（WRITE_BIGDECIMAL_AS_PLAIN）序列化断言出站 JSON 形状。</p>
 *
 * @since 0.1.0
 */
class AiplatformPriceEntryClientContractTest {

    /**
     * aiplatform GET /api/backoffice/price-entries 的真实成功响应形状（信封 + PageResponse）：
     * 生效起点倒序两行——首行当前行（effectiveTo=null、带开行操作者留痕），次行历史行（区间两端
     * 俱全、存量形制操作者 null）；total 为 JSON string。
     */
    private static final String PRICE_ENTRY_PAGE_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "items": [
                  {
                    "id": "3830100002222222",
                    "provider": "anthropic",
                    "model": "claude-fable-5",
                    "tokenKind": 1,
                    "tokenKindName": "输入",
                    "unitPrice": "0.000002",
                    "currency": "USD",
                    "effectiveFrom": "2026-08-01T00:00:00Z",
                    "effectiveTo": null,
                    "operatorId": "700160",
                    "operatorName": "运营·单价管理员"
                  },
                  {
                    "id": "3830100001111111",
                    "provider": "anthropic",
                    "model": "claude-fable-5",
                    "tokenKind": 1,
                    "tokenKindName": "输入",
                    "unitPrice": "0.00000132",
                    "currency": "USD",
                    "effectiveFrom": "2026-07-01T00:00:00Z",
                    "effectiveTo": "2026-08-01T00:00:00Z",
                    "operatorId": null,
                    "operatorName": null
                  }
                ],
                "total": "2",
                "page": 1,
                "size": 20
              },
              "requestId": "req-p1e2l",
              "errors": null
            }
            """;

    /**
     * aiplatform POST /api/backoffice/price-entries/{id}/reprice 的真实成功响应形状：
     * {closed, opened} 双行回执——closed 落 effectiveTo＝新起点、保留原开行留痕（不被改写）；
     * opened 沿用匹配键、新单价、敞口、带改价操作者。
     */
    private static final String REPRICE_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "closed": {
                  "id": "3830100002222222",
                  "provider": "anthropic",
                  "model": "claude-fable-5",
                  "tokenKind": 1,
                  "tokenKindName": "输入",
                  "unitPrice": "0.000002",
                  "currency": "USD",
                  "effectiveFrom": "2026-08-01T00:00:00Z",
                  "effectiveTo": "2026-09-20T00:00:00Z",
                  "operatorId": "700160",
                  "operatorName": "运营·单价管理员"
                },
                "opened": {
                  "id": "3830100003333333",
                  "provider": "anthropic",
                  "model": "claude-fable-5",
                  "tokenKind": 1,
                  "tokenKindName": "输入",
                  "unitPrice": "0.0000018",
                  "currency": "USD",
                  "effectiveFrom": "2026-09-20T00:00:00Z",
                  "effectiveTo": null,
                  "operatorId": "3829492001234567",
                  "operatorName": "调价运营"
                }
              },
              "requestId": "req-p3r4p",
              "errors": null
            }
            """;

    /**
     * aiplatform POST /api/backoffice/price-entries/{id}/deactivate 的真实成功响应形状：
     * 被关行单行回执——effectiveTo 已落（≈现在）、停用操作者落被关行（唯一落点）。
     */
    private static final String DEACTIVATE_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "id": "3830100002222222",
                "provider": "anthropic",
                "model": "claude-fable-5",
                "tokenKind": 1,
                "tokenKindName": "输入",
                "unitPrice": "0.000002",
                "currency": "USD",
                "effectiveFrom": "2026-08-01T00:00:00Z",
                "effectiveTo": "2026-09-16T10:30:00Z",
                "operatorId": "3829492001234567",
                "operatorName": "调价运营"
              },
              "requestId": "req-p5d6a",
              "errors": null
            }
            """;

    /** 区间重叠（跨区间或同起点）：HTTP 409 + 信封 code=3008（METER_008，域码 3）。 */
    private static final String METER_008_ERROR_ENVELOPE = """
            {
              "code": 3008,
              "message": "同匹配键生效区间重叠（跨区间或同起点）",
              "data": null,
              "requestId": "req-p7o8v",
              "errors": null
            }
            """;

    /** 行不存在（含畸形 id）：HTTP 404 + 信封 code=3006（METER_006）。 */
    private static final String METER_006_ERROR_ENVELOPE = """
            {
              "code": 3006,
              "message": "单价行不存在",
              "data": null,
              "requestId": "req-p9n0f",
              "errors": null
            }
            """;

    private static final String ENTRY_ID = "3830100002222222";

    // ========== 清单（含历史行 + provider/model 过滤 + 1-based 回显）==========

    @Test
    void given_filtersAndPage_when_listPriceEntries_then_wireUrlMirrorsProviderContract() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.priceEntryAppServiceWithStubTransport(
                PRICE_ENTRY_PAGE_ENVELOPE, wireUrl, new String[1]);

        appService.list(new AiplatformPriceEntryQuery("anthropic", "claude-fable-5"), 1, 20);

        // 出站 wire 形状逐字镜像 provider 契约：page 1-based 直传零换算，provider/model 精确过滤
        assertThat(wireUrl[0]).isEqualTo("http://stub-aiplatform/api/backoffice/price-entries"
                + "?page=1&size=20&provider=anthropic&model=claude-fable-5");
    }

    @Test
    void given_nullFilters_when_listPriceEntries_then_filtersOmittedFromWireUrl() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.priceEntryAppServiceWithStubTransport(
                PRICE_ENTRY_PAGE_ENVELOPE, wireUrl, new String[1]);

        appService.list(new AiplatformPriceEntryQuery(null, null), 2, 50);

        // 缺省＝全量行：null 过滤维度不出站（provider 空白归一口径同）；page 直传
        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/price-entries?page=2&size=50");
    }

    @Test
    void given_priceEntryPageEnvelope_when_list_then_envelopeUnwrappedAndRowsBound() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.priceEntryAppServiceWithStubTransport(
                PRICE_ENTRY_PAGE_ENVELOPE, wireUrl, new String[1]);

        PageResponse<AiplatformUnitPriceEntryResponse> page =
                appService.list(new AiplatformPriceEntryQuery("anthropic", "claude-fable-5"), 1, 20);

        // 信封正确拆开——证明 PRICE_ENTRY_PAGE_TYPEREF 按 ApiResponse<PageResponse<…>> 反序列化；
        // total JSON string→long、分页回显 provider 回报值（1-based）——BFF 不夹取不换算
        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);

        // 首行：当前行（effectiveTo=null）——tokenKind Integer code + tokenKindName 随行、
        // unitPrice String 精确十进制串、操作者留痕两肢
        var current = page.items().get(0);
        assertThat(current.id()).isEqualTo(ENTRY_ID);
        assertThat(current.provider()).isEqualTo("anthropic");
        assertThat(current.model()).isEqualTo("claude-fable-5");
        assertThat(current.tokenKind()).isEqualTo(1);
        assertThat(current.tokenKindName()).isEqualTo("输入");
        assertThat(current.unitPrice()).isEqualTo("0.000002");
        assertThat(current.currency()).isEqualTo("USD");
        assertThat(current.effectiveFrom()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(current.effectiveTo()).isNull();
        assertThat(current.operatorId()).isEqualTo("700160");
        assertThat(current.operatorName()).isEqualTo("运营·单价管理员");

        // 次行：历史行（区间两端俱全）——存量形制操作者两列 null 如实绑定
        var historical = page.items().get(1);
        assertThat(historical.id()).isEqualTo("3830100001111111");
        assertThat(historical.unitPrice()).isEqualTo("0.00000132");
        assertThat(historical.effectiveFrom()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(historical.effectiveTo()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(historical.operatorId()).isNull();
        assertThat(historical.operatorName()).isNull();
    }

    // ========== 原子改价（命令体序列化 + closed/opened 双行回执）==========

    @Test
    void given_repriceCommand_when_reprice_then_wireUrlAndCommandBodyMirrorProvider() {
        String[] wireUrl = new String[1];
        String[] wireBody = new String[1];
        var appService = AiplatformWireTestSupport.priceEntryAppServiceWithStubTransport(
                REPRICE_ENVELOPE, wireUrl, wireBody);

        appService.reprice(ENTRY_ID, new AiplatformPriceEntryRepriceCommand(
                new BigDecimal("0.0000018"), "USD", Instant.parse("2026-09-20T00:00:00Z")));

        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/price-entries/" + ENTRY_ID + "/reprice");
        // 命令体出站形状（生产 mapper 口径）：unitPrice JSON 明文小数（不落科学计数）、
        // effectiveFrom ISO-8601（UTC 带 Z）——逐字镜像 provider RepricePriceEntryCommand
        assertThat(wireBody[0]).isEqualTo(
                "{\"unitPrice\":0.0000018,\"currency\":\"USD\",\"effectiveFrom\":\"2026-09-20T00:00:00Z\"}");
    }

    @Test
    void given_repriceCommandWithoutEffectiveFrom_when_reprice_then_nullFieldSerializedProviderDecidesNow() {
        String[] wireBody = new String[1];
        var appService = AiplatformWireTestSupport.priceEntryAppServiceWithStubTransport(
                REPRICE_ENVELOPE, new String[1], wireBody);

        appService.reprice(ENTRY_ID, new AiplatformPriceEntryRepriceCommand(
                new BigDecimal("0.0000018"), "USD", null));

        // effectiveFrom 缺省＝provider 裁决即时（null→now()）：BFF 不代填时点，null 照常序列化在场
        // （全局 Jackson 含 null——provider record 反序列化 null 组件即走缺省分支）
        assertThat(wireBody[0]).isEqualTo(
                "{\"unitPrice\":0.0000018,\"currency\":\"USD\",\"effectiveFrom\":null}");
    }

    @Test
    void given_repriceEnvelope_when_reprice_then_closedOpenedReceiptBound() {
        var appService = AiplatformWireTestSupport.priceEntryAppServiceWithStubTransport(
                REPRICE_ENVELOPE, new String[1], new String[1]);

        AiplatformUnitPriceEntryRepriceResponse receipt = appService.reprice(ENTRY_ID,
                new AiplatformPriceEntryRepriceCommand(
                        new BigDecimal("0.0000018"), "USD", Instant.parse("2026-09-20T00:00:00Z")));

        // closed：被关旧行——effectiveTo 落＝新行起点；保留原开行留痕（不被改写为改价者）
        var closed = receipt.closed();
        assertThat(closed.id()).isEqualTo(ENTRY_ID);
        assertThat(closed.unitPrice()).isEqualTo("0.000002");
        assertThat(closed.effectiveFrom()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(closed.effectiveTo()).isEqualTo(Instant.parse("2026-09-20T00:00:00Z"));
        assertThat(closed.operatorId()).isEqualTo("700160");

        // opened：所开新行——新 id、沿用匹配键、新单价、敞口（effectiveTo=null）、带改价操作者
        var opened = receipt.opened();
        assertThat(opened.id()).isEqualTo("3830100003333333");
        assertThat(opened.provider()).isEqualTo("anthropic");
        assertThat(opened.model()).isEqualTo("claude-fable-5");
        assertThat(opened.tokenKind()).isEqualTo(1);
        assertThat(opened.unitPrice()).isEqualTo("0.0000018");
        assertThat(opened.effectiveFrom()).isEqualTo(Instant.parse("2026-09-20T00:00:00Z"));
        assertThat(opened.effectiveTo()).isNull();
        assertThat(opened.operatorId()).isEqualTo("3829492001234567");
        assertThat(opened.operatorName()).isEqualTo("调价运营");
    }

    // ========== 停用（无请求体 + 单行回执）==========

    @Test
    void given_entryId_when_deactivate_then_wireUrlMirrorsProviderAndNoBody() {
        String[] wireUrl = new String[1];
        String[] wireBody = new String[1];
        var appService = AiplatformWireTestSupport.priceEntryAppServiceWithStubTransport(
                DEACTIVATE_ENVELOPE, wireUrl, wireBody);

        appService.deactivate(ENTRY_ID);

        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/price-entries/" + ENTRY_ID + "/deactivate");
        // 无请求体（provider 端只读路径参数——同沙箱四动作先例）
        assertThat(wireBody[0]).isNull();
    }

    @Test
    void given_deactivateEnvelope_when_deactivate_then_singleRowReceiptBound() {
        var appService = AiplatformWireTestSupport.priceEntryAppServiceWithStubTransport(
                DEACTIVATE_ENVELOPE, new String[1], new String[1]);

        AiplatformUnitPriceEntryResponse closed = appService.deactivate(ENTRY_ID);

        // 被关行单行回执：effectiveTo 已落（≈现在）、停用操作者落被关行（唯一落点）
        assertThat(closed.id()).isEqualTo(ENTRY_ID);
        assertThat(closed.effectiveTo()).isEqualTo(Instant.parse("2026-09-16T10:30:00Z"));
        assertThat(closed.operatorId()).isEqualTo("3829492001234567");
        assertThat(closed.operatorName()).isEqualTo("调价运营");
    }

    // ========== 错误透传（METER_ 数字业务码，不映射）==========

    @Test
    void given_meter008ErrorEnvelope_when_reprice_then_passThroughWithoutMapping() {
        var appService = AiplatformWireTestSupport.priceEntryAppServiceWithErrorTransport(
                409, METER_008_ERROR_ENVELOPE, new String[1]);

        // 忠实透传（spec #62）：HTTP 409 + 数字业务码 3008（METER_008＝域码 3×1000＋8）+ provider message 原样
        assertThatThrownBy(() -> appService.reprice(ENTRY_ID, new AiplatformPriceEntryRepriceCommand(
                new BigDecimal("0.0000018"), "USD", Instant.parse("2026-09-20T00:00:00Z"))))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(409);
                    assertThat(upstream.envelopeCode()).isEqualTo(3008);
                    assertThat(upstream.getMessage()).isEqualTo("同匹配键生效区间重叠（跨区间或同起点）");
                });
    }

    @Test
    void given_meter006ErrorEnvelope_when_deactivate_then_passThroughWithoutMapping() {
        var appService = AiplatformWireTestSupport.priceEntryAppServiceWithErrorTransport(
                404, METER_006_ERROR_ENVELOPE, new String[1]);

        assertThatThrownBy(() -> appService.deactivate("999999999"))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(404);
                    assertThat(upstream.envelopeCode()).isEqualTo(3006);
                    assertThat(upstream.getMessage()).isEqualTo("单价行不存在");
                });
    }
}
