package com.aieducenter.admin.aiplatform.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aieducenter.admin.aiplatform.application.AiplatformUpstreamException;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformProjectQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformConversationEntryResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformProjectSummaryResponse;
import com.cartisan.web.response.PageResponse;

/**
 * aiplatform 项目域核心读路径的 wire 契约测试（对接 aiplatform #159/#162/#164 契约，issue #65）。
 *
 * <p>契约事实（2026-09-15 对照 aiplatform 源码 {@code BackofficeProjectController} /
 * {@code BackofficeProjectAppService} / 各 response DTO 核实）：</p>
 * <ul>
 *   <li>六端点均包 {@code ApiResponse<T>} 信封，按信封反序列化取 {@code .data()}；清单 data 为
 *       {@code PageResponse{items,total,page,size}}（page 1-based、total JSON string——cartisan-web
 *       全局 Long→ToStringSerializer），对话史/版本列表 data 为<strong>裸 List</strong>。</li>
 *   <li>清单 {@code status} 为<strong>三档单选</strong>（1=进行中 3=已归档，缺省＝全部）——单值直传，
 *       与订单域多选拼逗号有意不同；精确维度 {@code projectId}。</li>
 *   <li>详情嵌套订单引用（activeOrder/latestOrder：id/status Integer+statusName 三字段）与成本指针
 *       （costSummary.cost＝Map&lt;币种码, BigDecimal&gt; JSON 数字、unpriced boolean）。</li>
 *   <li>对话史 {@code kind} Integer code + {@code kindName} 中文名随行——<strong>aiplatform#186
 *       已落</strong>（provider f01d984，2026-09-15 源码核实；issue #65 body 所记「provider 暂无
 *       kindName」已过时）：BFF 透传 provider 值，不臆造映射。question/closing/attachments 载荷
 *       JSON 原样（Map/List 透传不解读）。</li>
 *   <li>PRD {@code updatedAt} 为 Instant（ISO-8601 UTC 带 Z）；版本 ref＝hex 40 位 commit hash。</li>
 *   <li>错误透传（数字业务码＝域码×1000＋序号）：PRJ_014→4014（400）、PRJ_001→4001（404）、
 *       PRJ_015→4015（404）、PRJ_028→4028（404）、WSP_002→1002（500）。</li>
 * </ul>
 *
 * <p>本测试 <strong>不</strong> mock {@code AiplatformClient}（方法边界 mock 会绕过 wire 反序列化
 * ——已知反例），经 {@link AiplatformWireTestSupport} 子类化 {@code OpenApiClient} 仅替换 HTTP
 * 传输，真实 {@code TypeReference} 反序列化 + 真实 AppService 映射完整保留。</p>
 *
 * @since 0.1.0
 */
class AiplatformProjectClientContractTest {

    /** 40-hex commit hash（版本详情/回滚锚定用）。 */
    private static final String COMMIT_HASH = "3f9c1a2b7d84e5f6a0b1c2d3e4f5a6b7c8d9e0f1";

    /** 另一 40-hex commit hash（rollbackFrom 锚定源版本用）。 */
    private static final String SOURCE_HASH = "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b";

    /**
     * aiplatform GET /api/backoffice/projects 的真实成功响应形状（信封 + PageResponse：两行，
     * 一进行中一已归档）。Long 字段（total）为 JSON string——cartisan-web 全局
     * {@code Long→ToStringSerializer} 出口口径；costSummary 的 BigDecimal 是 JSON 数字（同 payment
     * successRate 先例）。
     */
    private static final String PROJECT_PAGE_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "items": [
                  {
                    "id": "3829492007654321",
                    "name": "英语学习助手",
                    "ownerDisplayName": "文野",
                    "type": 1,
                    "typeName": "官网",
                    "status": 1,
                    "statusName": "进行中",
                    "archived": false,
                    "createdAt": "2026-09-10T14:20:00",
                    "updatedAt": "2026-09-14T18:30:00"
                  },
                  {
                    "id": "3829492001111111",
                    "name": "老官网",
                    "ownerDisplayName": null,
                    "type": 1,
                    "typeName": "官网",
                    "status": 3,
                    "statusName": "已归档",
                    "archived": true,
                    "createdAt": "2026-08-01T09:00:00",
                    "updatedAt": "2026-09-01T12:00:00"
                  }
                ],
                "total": "42",
                "page": 3,
                "size": 20
              },
              "requestId": "req-5e6f7a",
              "errors": null
            }
            """;

    /**
     * aiplatform GET /api/backoffice/projects/{id} 的真实成功响应形状：activeOrder（未终结已报价）
     * + latestOrder（最近一张已支付）+ costSummary（单币种 CNY + unpriced true——成本不完整标记）。
     */
    private static final String PROJECT_DETAIL_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "id": "3829492007654321",
                "name": "英语学习助手",
                "ownerDisplayName": "文野",
                "workspaceId": "3829492009999999",
                "type": 1,
                "typeName": "官网",
                "status": 1,
                "statusName": "进行中",
                "archived": false,
                "createdAt": "2026-09-10T14:20:00",
                "updatedAt": "2026-09-14T18:30:00",
                "prdProducedAt": "2026-09-10T20:00:00",
                "generatedAt": "2026-09-11T08:00:00",
                "activeOrder": {
                  "id": "3829492001234567",
                  "status": 2,
                  "statusName": "已报价"
                },
                "latestOrder": {
                  "id": "3829492005555444",
                  "status": 4,
                  "statusName": "已归档"
                },
                "costSummary": {
                  "cost": {"CNY": 12.3456},
                  "unpriced": true
                }
              },
              "requestId": "req-6f7a8b",
              "errors": null
            }
            """;

    /**
     * aiplatform GET /api/backoffice/projects/{id}/conversation 的真实成功响应形状（data 裸 List）：
     * 用户发言（带圈注附件）/问答卡（挂起待答）/收尾卡（run-finish 载荷）三条，id 升序＝对话序。
     * kindName 随行——aiplatform#186 已落（f01d984），BFF 透传 provider 值。
     */
    private static final String CONVERSATION_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": [
                {
                  "id": "90001",
                  "kind": 1,
                  "kindName": "用户发言",
                  "runId": null,
                  "text": "首页加一个轮播图",
                  "question": null,
                  "closing": null,
                  "attachments": [
                    {"type": "circle", "note": "这个位置", "dom": {"selector": "div.hero"}}
                  ],
                  "answered": false,
                  "at": "2026-09-12T10:00:00"
                },
                {
                  "id": "90002",
                  "kind": 3,
                  "kindName": "问答卡",
                  "runId": null,
                  "text": null,
                  "question": {"questionId": "q-77", "prompt": "轮播图要几张？", "options": ["3 张", "5 张"]},
                  "closing": null,
                  "attachments": null,
                  "answered": false,
                  "at": "2026-09-12T10:01:00"
                },
                {
                  "id": "90003",
                  "kind": 5,
                  "kindName": "收尾卡",
                  "runId": "run-abc123",
                  "text": null,
                  "question": null,
                  "closing": {"runId": "run-abc123", "summary": "首页轮播图已上线", "commitHash": "%s"},
                  "attachments": null,
                  "answered": false,
                  "at": "2026-09-12T11:30:00"
                }
              ],
              "requestId": "req-7a8b9c",
              "errors": null
            }
            """.formatted(COMMIT_HASH);

    /** aiplatform GET /api/backoffice/projects/{id}/prd 的真实成功响应形状（updatedAt 为 Instant）。 */
    private static final String PRD_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "projectId": "3829492007654321",
                "content": "# PRD\\n\\n做一个英语学习助手……",
                "updatedAt": "2026-09-12T08:30:00Z"
              },
              "requestId": "req-8b9c0d",
              "errors": null
            }
            """;

    /** aiplatform GET /api/backoffice/projects/{id}/versions 的真实成功响应形状（新→旧，run 版本＋回滚版本）。 */
    private static final String VERSIONS_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": [
                {
                  "commitHash": "%s",
                  "subject": "轮播图上线",
                  "runId": "run-abc123",
                  "rollbackFrom": null,
                  "committedAt": "2026-09-12T11:30:00"
                },
                {
                  "commitHash": "%s",
                  "subject": "回滚至轮播图上线前",
                  "runId": null,
                  "rollbackFrom": "%s",
                  "committedAt": "2026-09-13T09:00:00"
                }
              ],
              "requestId": "req-9c0d1e",
              "errors": null
            }
            """.formatted(COMMIT_HASH, SOURCE_HASH, COMMIT_HASH);

    /** aiplatform GET /api/backoffice/projects/{id}/versions/{ref} 的真实成功响应形状（closing 载荷原样）。 */
    private static final String VERSION_DETAIL_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "commitHash": "%s",
                "subject": "轮播图上线",
                "runId": "run-abc123",
                "rollbackFrom": null,
                "committedAt": "2026-09-12T11:30:00",
                "closing": {"runId": "run-abc123", "summary": "首页轮播图已上线", "commitHash": "%s"}
              },
              "requestId": "req-0d1e2f",
              "errors": null
            }
            """.formatted(COMMIT_HASH, COMMIT_HASH);

    /** aiplatform 清单过滤参数绑定失败：HTTP 400 + 信封 code=4014（PRJ_014 数字业务码＝域码 4×1000＋14）。 */
    private static final String PRJ_014_ERROR_ENVELOPE = """
            {
              "code": 4014,
              "message": "无效的项目过滤参数",
              "data": null,
              "requestId": "req-1e2f3a",
              "errors": null
            }
            """;

    /** aiplatform 项目不存在：HTTP 404 + 信封 code=4001（PRJ_001，含已删项目——真删无墓碑）。 */
    private static final String PRJ_001_ERROR_ENVELOPE = """
            {
              "code": 4001,
              "message": "项目不存在",
              "data": null,
              "requestId": "req-2f3a4b",
              "errors": null
            }
            """;

    /** aiplatform PRD 未产出：HTTP 404 + 信封 code=4015（PRJ_015，区别于 PRJ_001 的项目不存在）。 */
    private static final String PRJ_015_ERROR_ENVELOPE = """
            {
              "code": 4015,
              "message": "PRD 尚未产出",
              "data": null,
              "requestId": "req-3a4b5c",
              "errors": null
            }
            """;

    /** aiplatform 版本 ref 非 commit hash 形态：HTTP 404 + 信封 code=4028（PRJ_028）。 */
    private static final String PRJ_028_ERROR_ENVELOPE = """
            {
              "code": 4028,
              "message": "版本不存在",
              "data": null,
              "requestId": "req-4b5c6d",
              "errors": null
            }
            """;

    /** aiplatform 环境故障（docker exec 自身失败）：HTTP 500 + 信封 code=1002（WSP_002＝域码 1×1000＋2，跨前缀透传）。 */
    private static final String WSP_002_ERROR_ENVELOPE = """
            {
              "code": 1002,
              "message": "环境后端操作失败",
              "data": null,
              "requestId": "req-5c6d7e",
              "errors": null
            }
            """;

    // ========== 清单（四维检索单选 status + 分页 1-based 透传）==========

    @Test
    void given_fullFourDimensionFilter_when_listProjects_then_wireUrlMirrorsProviderContract() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.projectAppServiceWithStubTransport(
                PROJECT_PAGE_ENVELOPE, wireUrl);

        appService.list(new AiplatformProjectQuery(
                3,
                LocalDateTime.of(2026, 9, 1, 0, 0, 0),
                LocalDateTime.of(2026, 9, 15, 23, 59, 59),
                "auth0|65f2c8a1", "3829492007654321"), 3, 20);

        // 出站 wire 形状逐字镜像 provider 契约：page 1-based 直传（无 ±1）；status 单选单值直传
        // （1=进行中 3=已归档——与订单域多选拼逗号有意不同）；时间为全秒 ISO；特殊字符 URL 编码
        assertThat(wireUrl[0]).isEqualTo("http://stub-aiplatform/api/backoffice/projects"
                + "?page=3&size=20"
                + "&status=3"
                + "&createdFrom=2026-09-01T00%3A00%3A00"
                + "&createdTo=2026-09-15T23%3A59%3A59"
                + "&externalId=auth0%7C65f2c8a1"
                + "&projectId=3829492007654321");
    }

    @Test
    void given_pageEnvelope_when_listProjects_then_envelopeUnwrappedAndTypesBound() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.projectAppServiceWithStubTransport(
                PROJECT_PAGE_ENVELOPE, wireUrl);

        PageResponse<AiplatformProjectSummaryResponse> page =
                appService.list(new AiplatformProjectQuery(null, null, null, null, null), 1, 20);

        // 信封正确拆开（items 非 null）——证明 PROJECT_PAGE_TYPEREF 按 ApiResponse<PageResponse<…>> 反序列化并取 .data()
        assertThat(page.items()).hasSize(2);

        // 分页回显：provider 回报值原样透传（1-based，含 clamp 后值）——BFF 不夹取不换算
        assertThat(page.total()).isEqualTo(42L);
        assertThat(page.page()).isEqualTo(3);
        assertThat(page.size()).isEqualTo(20);

        var active = page.items().get(0);
        // 类型口径：id String（TSID 十进制串）、type/status Integer code + *Name 中文名、archived Boolean
        assertThat(active.id()).isEqualTo("3829492007654321");
        assertThat(active.name()).isEqualTo("英语学习助手");
        assertThat(active.ownerDisplayName()).isEqualTo("文野");
        assertThat(active.type()).isEqualTo(1);
        assertThat(active.typeName()).isEqualTo("官网");
        assertThat(active.status()).isEqualTo(1);
        assertThat(active.statusName()).isEqualTo("进行中");
        assertThat(active.archived()).isFalse();
        assertThat(active.createdAt()).isEqualTo(LocalDateTime.of(2026, 9, 10, 14, 20, 0));
        assertThat(active.updatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 14, 18, 30, 0));

        // 归档行照读（provider 缺省含归档、BFF 不二次过滤）：派生 status=3 + archived 原始事实位；
        // 无主项目 ownerDisplayName null
        var archived = page.items().get(1);
        assertThat(archived.status()).isEqualTo(3);
        assertThat(archived.statusName()).isEqualTo("已归档");
        assertThat(archived.archived()).isTrue();
        assertThat(archived.ownerDisplayName()).isNull();
    }

    @Test
    void given_prj014ErrorEnvelope_when_listProjects_then_passThroughWithoutMapping() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.projectAppServiceWithErrorTransport(
                400, PRJ_014_ERROR_ENVELOPE, wireUrl);

        // 忠实透传（spec #62）：HTTP 400 + 数字业务码 4014（PRJ_014）+ provider message 原样
        assertThatThrownBy(() -> appService.list(
                new AiplatformProjectQuery(2, null, null, null, null), 1, 20))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(400);
                    assertThat(upstream.envelopeCode()).isEqualTo(4014);
                    assertThat(upstream.getMessage()).isEqualTo("无效的项目过滤参数");
                });
    }

    // ========== 详情（订单引用 + 成本指针嵌套）==========

    @Test
    void given_detailEnvelope_when_getDetail_then_orderRefsAndCostSummaryBound() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.projectAppServiceWithStubTransport(
                PROJECT_DETAIL_ENVELOPE, wireUrl);

        AiplatformProjectDetailResponse detail = appService.getDetail("3829492007654321");

        // 出站路径逐字镜像 provider backoffice 路由
        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/projects/3829492007654321");

        // 清单字段全量 + 详情独有（workspaceId/时点组）
        assertThat(detail.id()).isEqualTo("3829492007654321");
        assertThat(detail.workspaceId()).isEqualTo("3829492009999999");
        assertThat(detail.type()).isEqualTo(1);
        assertThat(detail.typeName()).isEqualTo("官网");
        assertThat(detail.status()).isEqualTo(1);
        assertThat(detail.statusName()).isEqualTo("进行中");
        assertThat(detail.prdProducedAt()).isEqualTo(LocalDateTime.of(2026, 9, 10, 20, 0, 0));
        assertThat(detail.generatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 11, 8, 0, 0));

        // 订单引用：activeOrder＝未终结（已报价，有值即冻结迭代）、latestOrder＝最近一张任意状态（已归档）
        assertThat(detail.activeOrder().id()).isEqualTo("3829492001234567");
        assertThat(detail.activeOrder().status()).isEqualTo(2);
        assertThat(detail.activeOrder().statusName()).isEqualTo("已报价");
        assertThat(detail.latestOrder().id()).isEqualTo("3829492005555444");
        assertThat(detail.latestOrder().status()).isEqualTo(4);
        assertThat(detail.latestOrder().statusName()).isEqualTo("已归档");

        // 成本指针：cost 按币种（JSON 数字 → BigDecimal 直读不折算）、unpriced 标记
        assertThat(detail.costSummary().cost())
                .containsOnlyKeys("CNY")
                .containsEntry("CNY", new BigDecimal("12.3456"));
        assertThat(detail.costSummary().unpriced()).isTrue();
    }

    @Test
    void given_prj001ErrorEnvelope_when_getDetail_then_passThroughWithoutMapping() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.projectAppServiceWithErrorTransport(
                404, PRJ_001_ERROR_ENVELOPE, wireUrl);

        assertThatThrownBy(() -> appService.getDetail("3829499999999999"))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(404);
                    assertThat(upstream.envelopeCode()).isEqualTo(4001);
                    assertThat(upstream.getMessage()).isEqualTo("项目不存在");
                });
    }

    // ========== 对话史（kind + kindName 随行 + 载荷原样透传）==========

    @Test
    void given_conversationEnvelope_when_getConversation_then_kindsAndPayloadsBoundVerbatim() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.projectAppServiceWithStubTransport(
                CONVERSATION_ENVELOPE, wireUrl);

        List<AiplatformConversationEntryResponse> entries = appService.getConversation("3829492007654321");

        // 出站路径逐字镜像 provider backoffice 路由
        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/projects/3829492007654321/conversation");

        // 全量同序（id 升序＝对话序）
        assertThat(entries).hasSize(3);

        // 用户发言（kind=1）：kindName 中文名随行（aiplatform#186 已落——provider 出口提供，BFF 透传）；
        // id 为 Long（provider Long→JSON string 出口、反序列化回 Long）；圈注附件数组原样
        var user = entries.get(0);
        assertThat(user.id()).isEqualTo(90001L);
        assertThat(user.kind()).isEqualTo(1);
        assertThat(user.kindName()).isEqualTo("用户发言");
        assertThat(user.runId()).isNull();
        assertThat(user.text()).isEqualTo("首页加一个轮播图");
        assertThat(user.question()).isNull();
        assertThat(user.closing()).isNull();
        assertThat(user.attachments()).hasSize(1);
        assertThat(user.attachments().get(0))
                .containsEntry("type", "circle")
                .containsEntry("note", "这个位置");
        assertThat(user.at()).isEqualTo(LocalDateTime.of(2026, 9, 12, 10, 0, 0));

        // 问答卡（kind=3）：question 载荷 Map 原样透传（answered=false 即挂起待答）——BFF 不解读内部结构
        var question = entries.get(1);
        assertThat(question.kind()).isEqualTo(3);
        assertThat(question.kindName()).isEqualTo("问答卡");
        assertThat(question.text()).isNull();
        assertThat(question.question())
                .containsEntry("questionId", "q-77")
                .containsEntry("prompt", "轮播图要几张？");
        assertThat(question.answered()).isFalse();

        // 收尾卡（kind=5）：run-finish 收口扩载同载荷（版本详情锚定的权威事实）
        var closing = entries.get(2);
        assertThat(closing.kind()).isEqualTo(5);
        assertThat(closing.kindName()).isEqualTo("收尾卡");
        assertThat(closing.runId()).isEqualTo("run-abc123");
        assertThat(closing.closing())
                .containsEntry("runId", "run-abc123")
                .containsEntry("commitHash", COMMIT_HASH);
    }

    // ========== PRD（Instant 秒精度）==========

    @Test
    void given_prdEnvelope_when_getPrd_then_instantAndContentBound() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.projectAppServiceWithStubTransport(
                PRD_ENVELOPE, wireUrl);

        var prd = appService.getPrd("3829492007654321");

        // 出站路径逐字镜像 provider backoffice 路由
        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/projects/3829492007654321/prd");

        // 正文原样 + updatedAt Instant（文件 mtime，秒精度 UTC）
        assertThat(prd.projectId()).isEqualTo("3829492007654321");
        assertThat(prd.content()).startsWith("# PRD");
        assertThat(prd.updatedAt()).isEqualTo(Instant.parse("2026-09-12T08:30:00Z"));
    }

    @Test
    void given_prj015ErrorEnvelope_when_getPrd_then_passThroughWithoutMapping() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.projectAppServiceWithErrorTransport(
                404, PRJ_015_ERROR_ENVELOPE, wireUrl);

        // PRD 未产出（工作区无 docs/PRD.md）——4015 与项目不存在 4001 区分透传
        assertThatThrownBy(() -> appService.getPrd("3829492007654321"))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(404);
                    assertThat(upstream.envelopeCode()).isEqualTo(4015);
                    assertThat(upstream.getMessage()).isEqualTo("PRD 尚未产出");
                });
    }

    // ========== 版本列表（run 版本与回滚版本互斥）==========

    @Test
    void given_versionsEnvelope_when_listVersions_then_runAndRollbackShapesBound() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.projectAppServiceWithStubTransport(
                VERSIONS_ENVELOPE, wireUrl);

        var versions = appService.listVersions("3829492007654321");

        // 出站路径逐字镜像 provider backoffice 路由
        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/projects/3829492007654321/versions");

        // 新→旧定死；run 版本（runId 非空、rollbackFrom null）与回滚版本（runId null、rollbackFrom 锚定源）互斥
        assertThat(versions).hasSize(2);
        var run = versions.get(0);
        assertThat(run.commitHash()).isEqualTo(COMMIT_HASH);
        assertThat(run.subject()).isEqualTo("轮播图上线");
        assertThat(run.runId()).isEqualTo("run-abc123");
        assertThat(run.rollbackFrom()).isNull();
        assertThat(run.committedAt()).isEqualTo(LocalDateTime.of(2026, 9, 12, 11, 30, 0));
        var rollback = versions.get(1);
        assertThat(rollback.commitHash()).isEqualTo(SOURCE_HASH);
        assertThat(rollback.runId()).isNull();
        assertThat(rollback.rollbackFrom()).isEqualTo(COMMIT_HASH);
    }

    // ========== 版本详情（锚定收尾卡）==========

    @Test
    void given_versionDetailEnvelope_when_getVersion_then_closingPayloadBoundVerbatim() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.projectAppServiceWithStubTransport(
                VERSION_DETAIL_ENVELOPE, wireUrl);

        var version = appService.getVersion("3829492007654321", COMMIT_HASH);

        // 出站路径逐字镜像 provider backoffice 路由（ref＝hex 40 位 commit hash 路径段）
        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/projects/3829492007654321/versions/" + COMMIT_HASH);

        assertThat(version.commitHash()).isEqualTo(COMMIT_HASH);
        assertThat(version.subject()).isEqualTo("轮播图上线");
        assertThat(version.runId()).isEqualTo("run-abc123");
        assertThat(version.committedAt()).isEqualTo(LocalDateTime.of(2026, 9, 12, 11, 30, 0));

        // 收尾卡载荷 Map 原样透传（Run-Id 联接对话史 closing 条目，#88 同载荷）
        assertThat(version.closing())
                .containsEntry("runId", "run-abc123")
                .containsEntry("summary", "首页轮播图已上线")
                .containsEntry("commitHash", COMMIT_HASH);
    }

    @Test
    void given_prj028ErrorEnvelope_when_getVersion_then_passThroughWithoutMapping() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.projectAppServiceWithErrorTransport(
                404, PRJ_028_ERROR_ENVELOPE, wireUrl);

        // 非 hash 形态 ref——provider 裁决 404 PRJ_028（不触工作区，shell 注入防线），原样透传
        assertThatThrownBy(() -> appService.getVersion("3829492007654321", "main"))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(404);
                    assertThat(upstream.envelopeCode()).isEqualTo(4028);
                    assertThat(upstream.getMessage()).isEqualTo("版本不存在");
                });
    }

    @Test
    void given_wsp002ErrorEnvelope_when_listVersions_then_passThroughWithoutMapping() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.projectAppServiceWithErrorTransport(
                500, WSP_002_ERROR_ENVELOPE, wireUrl);

        // 环境故障跨前缀透传（WSP_002 照订单源码包先例）：HTTP 500 + 数字业务码 1002 + message 原样
        assertThatThrownBy(() -> appService.listVersions("3829492007654321"))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(500);
                    assertThat(upstream.envelopeCode()).isEqualTo(1002);
                    assertThat(upstream.getMessage()).isEqualTo("环境后端操作失败");
                });
    }
}
