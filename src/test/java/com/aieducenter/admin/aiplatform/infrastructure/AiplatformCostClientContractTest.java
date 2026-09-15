package com.aieducenter.admin.aiplatform.infrastructure;

import java.time.Instant;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aieducenter.admin.aiplatform.application.AiplatformUpstreamException;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformCostQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformCostOverviewResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectCostDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectCostResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformUnpricedUsageResponse;
import com.cartisan.web.response.PageResponse;

/**
 * aiplatform 成本域（全局总览 / unpriced 全局警示 / 项目成本清单 / 单项目下钻）的 wire 契约测试
 * （对接 aiplatform #161/#164 契约，issue #67）。
 *
 * <p>契约事实（2026-09-16 对照 aiplatform 源码 {@code BackofficeCostController} /
 * {@code BackofficeCostOverviewResponse} / {@code BackofficeUnpricedUsageResponse} /
 * {@code BackofficeProjectCostResponse} / {@code BackofficeProjectCostDetailResponse} /
 * {@code MeteringMessage} / {@code ErrorCodePrefix} 核实）：</p>
 * <ul>
 *   <li>四端点均包 {@code ApiResponse<T>} 信封，按信封反序列化取 {@code .data()}；项目成本清单 data 为
 *       {@code PageResponse{items,total,page,size}}（page 1-based、total JSON string——cartisan-web
 *       全局 Long→ToStringSerializer），其余三端点 data 为裸对象。</li>
 *   <li>时间窗 {@code from}/{@code to} 半开区间 [from, to)、ISO-8601 Instant（UTC 带 Z）；
 *       provider 侧可缺省（缺省＝该侧不限），北向<strong>必填</strong>（issue #67：不设默认窗口）、
 *       值逐字透传。from/to 原样回显（北向必填 ⇒ 响应恒有值）。</li>
 *   <li>{@code total}/{@code tokens} 为 TokenUsage 五档（input/output/cacheRead/cacheWrite/reasoning，
 *       primitive long——provider 序列化为 JSON 数字，非 cartisan-web 的 Long 字符串口径）。</li>
 *   <li>{@code cost} 为 {@code Map<币种, BigDecimal>}（键 = ISO 4217 币种码，分桶直读不折算）；
 *       无生效单价的分量不进 cost（不伪装 0），其档位在 unpriced 如实呈现。</li>
 *   <li>{@code byModel}/{@code byAgentKind} 分解镜像：byModel＝provider+model+tokens 三字段；
 *       byAgentKind＝agentKind 裸维度串（dims 原值）+ agentKindName 中文名随行（aiplatform#186
 *       已落——主链经智能体配置回解，naming/classify 等辅助标记为 null）+ tokens。</li>
 *   <li>unpriced 档位：全局警示项带 token 计数（tokens long，只计无价分量）；单项目下钻项无 token
 *       计数（bySubject 口径）；tokenKind 为 Integer code（1=输入 2=输出 3=缓存读 4=缓存写 5=推理）
 *       + tokenKindName 随附。</li>
 *   <li>项目成本清单排序服务端定死成本降序、全未配价项目排后且 allUnpriced=true；分页 provider clamp
 *       （page≥1、size∈[1,100] 默认 20）行为透传、回显 provider 回报值。</li>
 *   <li>错误透传（数字业务码＝域码 3×1000＋序号）：METER_011→3011（400，无效的成本查询参数）。</li>
 * </ul>
 *
 * <p>本测试 <strong>不</strong> mock {@code AiplatformClient}（方法边界 mock 会绕过 wire 反序列化
 * ——已知反例），经 {@link AiplatformWireTestSupport} 子类化 {@code OpenApiClient} 仅替换 HTTP
 * 传输，真实 {@code TypeReference} 反序列化 + 真实 AppService 映射完整保留。</p>
 *
 * @since 0.1.0
 */
class AiplatformCostClientContractTest {

    /**
     * aiplatform GET /api/backoffice/costs/overview 的真实成功响应形状：窗口回显 + 五档总量 +
     * 双币种成本分桶 + 双模型分解 + 双智能体分解（主链带名、辅助标记 agentKindName=null）。
     * TokenUsage 五档为 JSON 数字（provider 侧 primitive long）；cost 值为 JSON 数字（BigDecimal）。
     */
    private static final String COST_OVERVIEW_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "from": "2026-09-01T00:00:00Z",
                "to": "2026-09-16T00:00:00Z",
                "total": {
                  "input": 5000,
                  "output": 1200,
                  "cacheRead": 300,
                  "cacheWrite": 0,
                  "reasoning": 800
                },
                "cost": {
                  "CNY": 12.3456,
                  "USD": 1.7500
                },
                "byModel": [
                  {
                    "provider": "anthropic",
                    "model": "claude-fable-5",
                    "tokens": { "input": 3000, "output": 1000, "cacheRead": 300, "cacheWrite": 0, "reasoning": 800 }
                  },
                  {
                    "provider": "openai",
                    "model": "gpt-5.2",
                    "tokens": { "input": 2000, "output": 200, "cacheRead": 0, "cacheWrite": 0, "reasoning": 0 }
                  }
                ],
                "byAgentKind": [
                  {
                    "agentKind": "main",
                    "agentKindName": "主编排",
                    "tokens": { "input": 4000, "output": 1000, "cacheRead": 300, "cacheWrite": 0, "reasoning": 800 }
                  },
                  {
                    "agentKind": "naming",
                    "agentKindName": null,
                    "tokens": { "input": 1000, "output": 200, "cacheRead": 0, "cacheWrite": 0, "reasoning": 0 }
                  }
                ]
              },
              "requestId": "req-c1o2v",
              "errors": null
            }
            """;

    /**
     * aiplatform GET /api/backoffice/costs/unpriced 的真实成功响应形状：窗口回显 + 两档未配价
     * 档位（tokenKind Integer code + tokenKindName + tokens 只计无价分量，JSON 数字）。
     */
    private static final String UNPRICED_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "from": "2026-09-01T00:00:00Z",
                "to": "2026-09-16T00:00:00Z",
                "items": [
                  {
                    "provider": "openai",
                    "model": "gpt-5.2",
                    "tokenKind": 1,
                    "tokenKindName": "输入",
                    "tokens": 700
                  },
                  {
                    "provider": "deepseek",
                    "model": "deepseek-v4",
                    "tokenKind": 5,
                    "tokenKindName": "推理",
                    "tokens": 300
                  }
                ]
              },
              "requestId": "req-c3u4n",
              "errors": null
            }
            """;

    /**
     * aiplatform GET /api/backoffice/costs/projects 的真实成功响应形状（信封 + PageResponse）：
     * 成本降序两行——首行有价有量（allUnpriced=false、双币种分桶），次行全未配价（cost 空、
     * allUnpriced=true、排后）；total 为 JSON string（cartisan-web 全局 Long→ToStringSerializer）。
     */
    private static final String PROJECT_COST_PAGE_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "items": [
                  {
                    "projectId": "3829492007654321",
                    "total": { "input": 3000, "output": 1000, "cacheRead": 300, "cacheWrite": 0, "reasoning": 800 },
                    "cost": { "CNY": 12.3456, "USD": 1.7500 },
                    "allUnpriced": false
                  },
                  {
                    "projectId": "3829492005555444",
                    "total": { "input": 1000, "output": 200, "cacheRead": 0, "cacheWrite": 0, "reasoning": 0 },
                    "cost": {},
                    "allUnpriced": true
                  }
                ],
                "total": "2",
                "page": 1,
                "size": 20
              },
              "requestId": "req-c5p6r",
              "errors": null
            }
            """;

    /**
     * aiplatform GET /api/backoffice/costs/projects/{projectId} 的真实成功响应形状：subject 原值
     * 回显 + 窗口回显 + 五档总量 + 成本分桶 + 未配价档位（bySubject 口径无 token 计数）+ 双分解。
     */
    private static final String PROJECT_COST_DETAIL_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "projectId": "3829492007654321",
                "from": "2026-09-01T00:00:00Z",
                "to": "2026-09-16T00:00:00Z",
                "total": { "input": 3000, "output": 1000, "cacheRead": 300, "cacheWrite": 0, "reasoning": 800 },
                "cost": { "CNY": 12.3456 },
                "unpriced": [
                  { "provider": "openai", "model": "gpt-5.2", "tokenKind": 1, "tokenKindName": "输入" }
                ],
                "byModel": [
                  {
                    "provider": "anthropic",
                    "model": "claude-fable-5",
                    "tokens": { "input": 3000, "output": 1000, "cacheRead": 300, "cacheWrite": 0, "reasoning": 800 }
                  }
                ],
                "byAgentKind": [
                  {
                    "agentKind": "executor",
                    "agentKindName": "执行智能体",
                    "tokens": { "input": 3000, "output": 1000, "cacheRead": 300, "cacheWrite": 0, "reasoning": 800 }
                  }
                ]
              },
              "requestId": "req-c7d8e",
              "errors": null
            }
            """;

    /** aiplatform 成本查询参数绑定失败（非法时间窗/分页值）：HTTP 400 + 信封 code=3011（METER_011，域码 3）。 */
    private static final String METER_011_ERROR_ENVELOPE = """
            {
              "code": 3011,
              "message": "无效的成本查询参数",
              "data": null,
              "requestId": "req-c9t0i",
              "errors": null
            }
            """;

    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-16T00:00:00Z");

    // ========== 全局总览（时间窗 + 五档总量 + 成本分桶 + 双分解）==========

    @Test
    void given_costWindow_when_getOverview_then_wireUrlMirrorsProviderContract() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.costAppServiceWithStubTransport(
                COST_OVERVIEW_ENVELOPE, wireUrl);

        appService.overview(new AiplatformCostQuery(FROM, TO));

        // 出站 wire 形状逐字镜像 provider 契约：from/to 为 ISO-8601 Instant（UTC 带 Z），
        // ':' URL 编码为 %3A——与 provider 签名侧 raw query string 同形入签
        assertThat(wireUrl[0]).isEqualTo("http://stub-aiplatform/api/backoffice/costs/overview"
                + "?from=2026-09-01T00%3A00%3A00Z"
                + "&to=2026-09-16T00%3A00%3A00Z");
    }

    @Test
    void given_overviewEnvelope_when_getOverview_then_envelopeUnwrappedAndTypesBound() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.costAppServiceWithStubTransport(
                COST_OVERVIEW_ENVELOPE, wireUrl);

        AiplatformCostOverviewResponse overview = appService.overview(new AiplatformCostQuery(FROM, TO));

        // 信封正确拆开——证明 COST_OVERVIEW_TYPEREF 按 ApiResponse<…> 反序列化并取 .data()；
        // 窗口原样回显（北向必填 ⇒ 恒有值）
        assertThat(overview.from()).isEqualTo(FROM);
        assertThat(overview.to()).isEqualTo(TO);

        // 五档总量（primitive long——provider 序列化为 JSON 数字，非 Long 字符串口径）
        assertThat(overview.total().input()).isEqualTo(5000L);
        assertThat(overview.total().output()).isEqualTo(1200L);
        assertThat(overview.total().cacheRead()).isEqualTo(300L);
        assertThat(overview.total().cacheWrite()).isEqualTo(0L);
        assertThat(overview.total().reasoning()).isEqualTo(800L);

        // 平台成本按币种分桶直读（键 = ISO 4217 币种码、值 BigDecimal）
        assertThat(overview.cost())
                .containsEntry("CNY", new BigDecimal("12.3456"))
                .containsEntry("USD", new BigDecimal("1.7500"));

        // byModel：provider+model+tokens 三字段
        assertThat(overview.byModel()).hasSize(2);
        var model = overview.byModel().get(0);
        assertThat(model.provider()).isEqualTo("anthropic");
        assertThat(model.model()).isEqualTo("claude-fable-5");
        assertThat(model.tokens().input()).isEqualTo(3000L);

        // byAgentKind：agentKind 裸维度串原值透传 + agentKindName 随行（#186 已落——主链回解有值，
        // 辅助标记 null 不臆造映射）
        assertThat(overview.byAgentKind()).hasSize(2);
        var main = overview.byAgentKind().get(0);
        assertThat(main.agentKind()).isEqualTo("main");
        assertThat(main.agentKindName()).isEqualTo("主编排");
        assertThat(main.tokens().reasoning()).isEqualTo(800L);
        var naming = overview.byAgentKind().get(1);
        assertThat(naming.agentKind()).isEqualTo("naming");
        assertThat(naming.agentKindName()).isNull();
    }

    // ========== unpriced 全局警示（用量驱动，档位带 token 计数）==========

    @Test
    void given_costWindow_when_getUnpriced_then_wireUrlMirrorsProviderContract() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.costAppServiceWithStubTransport(
                UNPRICED_ENVELOPE, wireUrl);

        appService.unpriced(new AiplatformCostQuery(FROM, TO));

        assertThat(wireUrl[0]).isEqualTo("http://stub-aiplatform/api/backoffice/costs/unpriced"
                + "?from=2026-09-01T00%3A00%3A00Z"
                + "&to=2026-09-16T00%3A00%3A00Z");
    }

    @Test
    void given_unpricedEnvelope_when_getUnpriced_then_tierCodesNamesAndCountsBound() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.costAppServiceWithStubTransport(
                UNPRICED_ENVELOPE, wireUrl);

        AiplatformUnpricedUsageResponse unpriced = appService.unpriced(new AiplatformCostQuery(FROM, TO));

        assertThat(unpriced.from()).isEqualTo(FROM);
        assertThat(unpriced.to()).isEqualTo(TO);

        // 未配价档位：tokenKind Integer code（1=输入 … 5=推理）+ tokenKindName + tokens
        // 只计无价分量（JSON 数字——primitive long）
        assertThat(unpriced.items()).hasSize(2);
        var inputTier = unpriced.items().get(0);
        assertThat(inputTier.provider()).isEqualTo("openai");
        assertThat(inputTier.model()).isEqualTo("gpt-5.2");
        assertThat(inputTier.tokenKind()).isEqualTo(1);
        assertThat(inputTier.tokenKindName()).isEqualTo("输入");
        assertThat(inputTier.tokens()).isEqualTo(700L);
        var reasoningTier = unpriced.items().get(1);
        assertThat(reasoningTier.tokenKind()).isEqualTo(5);
        assertThat(reasoningTier.tokenKindName()).isEqualTo("推理");
        assertThat(reasoningTier.tokens()).isEqualTo(300L);
    }

    // ========== 项目成本清单（成本降序分页 + allUnpriced 标记 + 1-based 回显）==========

    @Test
    void given_costWindowAndPage_when_listProjectCosts_then_wireUrlMirrorsProviderContract() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.costAppServiceWithStubTransport(
                PROJECT_COST_PAGE_ENVELOPE, wireUrl);

        appService.listProjectCosts(new AiplatformCostQuery(FROM, TO), 1, 20);

        // page 1-based 直传零换算（同订单/项目/沙箱）；from/to 时间窗随行
        assertThat(wireUrl[0]).isEqualTo("http://stub-aiplatform/api/backoffice/costs/projects"
                + "?page=1&size=20"
                + "&from=2026-09-01T00%3A00%3A00Z"
                + "&to=2026-09-16T00%3A00%3A00Z");
    }

    @Test
    void given_projectCostPageEnvelope_when_listProjectCosts_then_pageEchoedAndRowsBound() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.costAppServiceWithStubTransport(
                PROJECT_COST_PAGE_ENVELOPE, wireUrl);

        PageResponse<AiplatformProjectCostResponse> page =
                appService.listProjectCosts(new AiplatformCostQuery(FROM, TO), 1, 20);

        // 信封正确拆开——证明 PROJECT_COST_PAGE_TYPEREF 按 ApiResponse<PageResponse<…>> 反序列化；
        // 分页回显 provider 回报值（1-based，含 clamp 后值）——BFF 不夹取不换算
        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);

        // 首行：有价有量（成本降序在前）、双币种分桶 + allUnpriced=false
        var costly = page.items().get(0);
        assertThat(costly.projectId()).isEqualTo("3829492007654321");
        assertThat(costly.total().input()).isEqualTo(3000L);
        assertThat(costly.cost()).containsEntry("CNY", new BigDecimal("12.3456"));
        assertThat(costly.allUnpriced()).isFalse();

        // 次行：全未配价（有用量但无任何已配价分量）——cost 空 Map 不伪装 0、排后且 allUnpriced=true
        var allUnpriced = page.items().get(1);
        assertThat(allUnpriced.projectId()).isEqualTo("3829492005555444");
        assertThat(allUnpriced.cost()).isEmpty();
        assertThat(allUnpriced.allUnpriced()).isTrue();
    }

    // ========== 单项目下钻（byModel/byAgentKind 分解 + unpriced 档位无计数）==========

    @Test
    void given_costWindow_when_getProjectCostDetail_then_wireUrlMirrorsProviderContract() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.costAppServiceWithStubTransport(
                PROJECT_COST_DETAIL_ENVELOPE, wireUrl);

        appService.getProjectCostDetail("3829492007654321", new AiplatformCostQuery(FROM, TO));

        // projectId = 计量 subject 原值（写侧口径 projectId 十进制串，provider 不解释存在性）
        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/costs/projects/3829492007654321"
                        + "?from=2026-09-01T00%3A00%3A00Z"
                        + "&to=2026-09-16T00%3A00%3A00Z");
    }

    @Test
    void given_projectCostDetailEnvelope_when_getProjectCostDetail_then_breakdownsBound() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.costAppServiceWithStubTransport(
                PROJECT_COST_DETAIL_ENVELOPE, wireUrl);

        AiplatformProjectCostDetailResponse detail =
                appService.getProjectCostDetail("3829492007654321", new AiplatformCostQuery(FROM, TO));

        assertThat(detail.projectId()).isEqualTo("3829492007654321");
        assertThat(detail.from()).isEqualTo(FROM);
        assertThat(detail.to()).isEqualTo(TO);
        assertThat(detail.total().cacheRead()).isEqualTo(300L);
        assertThat(detail.cost()).containsEntry("CNY", new BigDecimal("12.3456"));

        // 未配价档位（bySubject 口径无 token 计数——档位用量汇总走全局 unpriced 端点）
        assertThat(detail.unpriced()).hasSize(1);
        var tier = detail.unpriced().get(0);
        assertThat(tier.provider()).isEqualTo("openai");
        assertThat(tier.tokenKind()).isEqualTo(1);
        assertThat(tier.tokenKindName()).isEqualTo("输入");

        // 双分解：byModel（provider+model）/ byAgentKind（裸维度串 + 中文名随行）
        assertThat(detail.byModel()).hasSize(1);
        assertThat(detail.byModel().get(0).tokens().input()).isEqualTo(3000L);
        assertThat(detail.byAgentKind()).hasSize(1);
        assertThat(detail.byAgentKind().get(0).agentKind()).isEqualTo("executor");
        assertThat(detail.byAgentKind().get(0).agentKindName()).isEqualTo("执行智能体");
    }

    // ========== 错误透传（METER_011 → 3011，不映射）==========

    @Test
    void given_meter011ErrorEnvelope_when_getOverview_then_passThroughWithoutMapping() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.costAppServiceWithErrorTransport(
                400, METER_011_ERROR_ENVELOPE, wireUrl);

        // 忠实透传（spec #62）：HTTP 400 + 数字业务码 3011（METER_011＝域码 3×1000＋11）+ provider message 原样
        assertThatThrownBy(() -> appService.overview(new AiplatformCostQuery(FROM, TO)))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(400);
                    assertThat(upstream.envelopeCode()).isEqualTo(3011);
                    assertThat(upstream.getMessage()).isEqualTo("无效的成本查询参数");
                });
    }
}
