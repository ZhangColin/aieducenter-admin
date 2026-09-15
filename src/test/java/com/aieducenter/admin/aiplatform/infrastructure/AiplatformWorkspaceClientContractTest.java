package com.aieducenter.admin.aiplatform.infrastructure;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aieducenter.admin.aiplatform.application.AiplatformUpstreamException;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformWorkspaceQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformWorkspaceDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformWorkspaceSummaryResponse;
import com.cartisan.web.response.PageResponse;

/**
 * aiplatform 沙箱域（观测清单/详情 + 四干预动作）的 wire 契约测试（对接 aiplatform #173/#174 契约，issue #66）。
 *
 * <p>契约事实（2026-09-16 对照 aiplatform 源码 {@code BackofficeWorkspaceController} /
 * {@code BackofficeWorkspaceSummaryResponse} / {@code BackofficeWorkspaceDetailResponse} /
 * {@code WorkspaceMessage} 核实）：</p>
 * <ul>
 *   <li>六端点均包 {@code ApiResponse<T>} 信封，按信封反序列化取 {@code .data()}；清单 data 为
 *       {@code PageResponse{items,total,page,size}}（page 1-based、total JSON string——cartisan-web
 *       全局 Long→ToStringSerializer）；四干预动作无请求体，回执＝详情 DTO（动作后的观测详情）。</li>
 *   <li>清单两维过滤均<strong>单选</strong> Integer code、可组合、可缺省：{@code desired}（1=运行
 *       2=休眠 3=封存，DB 意图侧）、{@code actual}（1=运行中 2=已停止 3=无容器 4=未知，docker 现场
 *       探查后内存过滤——actual=3 即捞漂移清单）。</li>
 *   <li>枚举四对 Integer code + {@code *Name} 中文名随行：kind/kindName（1=开发 2=测试 3=生产）、
 *       status/statusName（1=置备中 2=就绪 3=失败）、desiredState/desiredStateName、
 *       containerState/containerStateName。所属项目引用 {@code project}（projectId/name/archived
 *       三字段）软引用容缺——工作区先于项目存在，无所属项目为 null。</li>
 *   <li>详情另带 networkName/provisionError/封存包寻址键 archivePath/审计列 createdAt/updatedAt +
 *       中间件资源清单 {@code resources}（kind Integer code + containerName + internalUrl 三字段，
 *       连接串原文容器内回环形态——provider 出口无 *Name，忠实镜像不加戏）。</li>
 *   <li>错误透传（数字业务码＝域码 1×1000＋序号）：WSP_014→1014（400，过滤参数绑定失败）、
 *       WSP_001→1001（404，工作区不存在）、WSP_009→1009（400，状态边界：封存态休眠/重建、重复封存）、
 *       WSP_015→1015（409，run 在途拒）、WSP_017→1017（409，收敛任务在途）。</li>
 * </ul>
 *
 * <p>本测试 <strong>不</strong> mock {@code AiplatformClient}（方法边界 mock 会绕过 wire 反序列化
 * ——已知反例），经 {@link AiplatformWireTestSupport} 子类化 {@code OpenApiClient} 仅替换 HTTP
 * 传输，真实 {@code TypeReference} 反序列化 + 真实 AppService 映射完整保留。</p>
 *
 * @since 0.1.0
 */
class AiplatformWorkspaceClientContractTest {

    /**
     * aiplatform GET /api/backoffice/workspaces 的真实成功响应形状（信封 + PageResponse：两行——
     * 一漂移行「期望运行而实态无容器」带项目引用，一正常休眠行无所属项目）。Long 字段
     * （total/volumeSizeBytes/archiveSizeBytes）为 JSON string——cartisan-web 全局
     * {@code Long→ToStringSerializer} 出口口径。
     */
    private static final String WORKSPACE_PAGE_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "items": [
                  {
                    "workspaceId": "3829492009999999",
                    "containerName": "ws-3829492009999999",
                    "kind": 1,
                    "kindName": "开发",
                    "status": 2,
                    "statusName": "就绪",
                    "desiredState": 1,
                    "desiredStateName": "运行",
                    "containerState": 3,
                    "containerStateName": "无容器",
                    "lastTouchAt": "2026-09-14T22:10:00",
                    "volumeSizeBytes": "2147483648",
                    "sealedAt": null,
                    "archiveSizeBytes": null,
                    "project": {
                      "projectId": "3829492007654321",
                      "name": "英语学习助手",
                      "archived": false
                    }
                  },
                  {
                    "workspaceId": "3829492008888888",
                    "containerName": "ws-3829492008888888",
                    "kind": 1,
                    "kindName": "开发",
                    "status": 2,
                    "statusName": "就绪",
                    "desiredState": 2,
                    "desiredStateName": "休眠",
                    "containerState": 3,
                    "containerStateName": "无容器",
                    "lastTouchAt": "2026-09-01T08:00:00",
                    "volumeSizeBytes": null,
                    "sealedAt": null,
                    "archiveSizeBytes": null,
                    "project": null
                  }
                ],
                "total": "17",
                "page": 2,
                "size": 20
              },
              "requestId": "req-w1s2p",
              "errors": null
            }
            """;

    /**
     * aiplatform GET /api/backoffice/workspaces/{id} 的真实成功响应形状：封存态沙箱全量字段——
     * 封存包寻址键 + 卷用量容缺（封存删卷）、中间件资源清单（PostgreSQL + Redis，internalUrl
     * 容器内回环连接串原文）、provisionError null（非 FAILED 态）。
     */
    private static final String WORKSPACE_DETAIL_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "workspaceId": "3829492007777777",
                "containerName": "ws-3829492007777777",
                "networkName": "net-3829492007777777",
                "kind": 1,
                "kindName": "开发",
                "status": 2,
                "statusName": "就绪",
                "provisionError": null,
                "desiredState": 3,
                "desiredStateName": "封存",
                "containerState": 3,
                "containerStateName": "无容器",
                "lastTouchAt": "2026-09-13T18:00:00",
                "volumeSizeBytes": null,
                "sealedAt": "2026-09-13T18:00:00",
                "archivePath": "workspace-archives/3829492007777777.tar.gz",
                "archiveSizeBytes": "89128960",
                "createdAt": "2026-09-08T10:00:00",
                "updatedAt": "2026-09-13T18:00:00",
                "resources": [
                  {
                    "kind": 1,
                    "containerName": "mw-3829492007777777-pg",
                    "internalUrl": "postgresql://aiedu:secret@mw-3829492007777777-pg:5432/aiedu"
                  },
                  {
                    "kind": 2,
                    "containerName": "mw-3829492007777777-redis",
                    "internalUrl": "redis://mw-3829492007777777-redis:6379/0"
                  }
                ],
                "project": {
                  "projectId": "3829492007654321",
                  "name": "英语学习助手",
                  "archived": false
                }
              },
              "requestId": "req-w2d3e",
              "errors": null
            }
            """;

    /** aiplatform 清单过滤参数绑定失败（非法期望态/实态 code、分页值）：HTTP 400 + 信封 code=1014（WSP_014）。 */
    private static final String WSP_014_ERROR_ENVELOPE = """
            {
              "code": 1014,
              "message": "无效的工作区过滤参数",
              "data": null,
              "requestId": "req-w3f4i",
              "errors": null
            }
            """;

    /** aiplatform 工作区不存在（含畸形 id）：HTTP 404 + 信封 code=1001（WSP_001）。 */
    private static final String WSP_001_ERROR_ENVELOPE = """
            {
              "code": 1001,
              "message": "工作区不存在",
              "data": null,
              "requestId": "req-w4l5t",
              "errors": null
            }
            """;

    /** aiplatform 状态边界（封存态休眠/重建、重复封存、置备在途）：HTTP 400 + 信封 code=1009（WSP_009）。 */
    private static final String WSP_009_ERROR_ENVELOPE = """
            {
              "code": 1009,
              "message": "工作区置备状态不合法",
              "data": null,
              "requestId": "req-w5e6r",
              "errors": null
            }
            """;

    /** aiplatform run 在途拒（管理员操作资源面，不打断用户生成）：HTTP 409 + 信封 code=1015（WSP_015）。 */
    private static final String WSP_015_ERROR_ENVELOPE = """
            {
              "code": 1015,
              "message": "编码 run 进行中，沙箱动作被拒（先取消 run 或等收口）",
              "data": null,
              "requestId": "req-w7u8n",
              "errors": null
            }
            """;

    // ========== 清单（期望态/实态两维过滤 + 分页 1-based 透传）==========

    @Test
    void given_desiredAndActualFilter_when_listWorkspaces_then_wireUrlMirrorsProviderContract() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.workspaceAppServiceWithStubTransport(
                WORKSPACE_PAGE_ENVELOPE, wireUrl);

        // actual=3（无容器）即捞漂移清单——与 desired=1（运行）组合＝「期望运行而实态已亡」
        appService.list(new AiplatformWorkspaceQuery(1, 3), 2, 20);

        // 出站 wire 形状逐字镜像 provider 契约：page 1-based 直传（无 ±1）；两维均单选单值直传
        assertThat(wireUrl[0]).isEqualTo("http://stub-aiplatform/api/backoffice/workspaces"
                + "?page=2&size=20"
                + "&desired=1"
                + "&actual=3");
    }

    @Test
    void given_noFilter_when_listWorkspaces_then_filterParamsOmittedFromWireUrl() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.workspaceAppServiceWithStubTransport(
                WORKSPACE_PAGE_ENVELOPE, wireUrl);

        appService.list(new AiplatformWorkspaceQuery(null, null), 1, 20);

        // 两维均可缺省（缺省＝全量）——null 过滤不出现在出站 URL（缺省语义由 provider 兜底）
        assertThat(wireUrl[0]).isEqualTo("http://stub-aiplatform/api/backoffice/workspaces"
                + "?page=1&size=20");
    }

    @Test
    void given_pageEnvelope_when_listWorkspaces_then_envelopeUnwrappedAndTypesBound() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.workspaceAppServiceWithStubTransport(
                WORKSPACE_PAGE_ENVELOPE, wireUrl);

        PageResponse<AiplatformWorkspaceSummaryResponse> page =
                appService.list(new AiplatformWorkspaceQuery(null, null), 1, 20);

        // 信封正确拆开——证明 WORKSPACE_PAGE_TYPEREF 按 ApiResponse<PageResponse<…>> 反序列化并取 .data()
        assertThat(page.items()).hasSize(2);

        // 分页回显：provider 回报值原样透传（1-based，含 clamp 后值）——BFF 不夹取不换算
        assertThat(page.total()).isEqualTo(17L);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(20);

        // 漂移行：期望态/实态两列如实分示（desired=1 运行 vs containerState=3 无容器）；四对
        // Integer code + *Name 中文名；卷用量 Long（JSON string → Long）；项目引用三字段
        var drift = page.items().get(0);
        assertThat(drift.workspaceId()).isEqualTo("3829492009999999");
        assertThat(drift.containerName()).isEqualTo("ws-3829492009999999");
        assertThat(drift.kind()).isEqualTo(1);
        assertThat(drift.kindName()).isEqualTo("开发");
        assertThat(drift.status()).isEqualTo(2);
        assertThat(drift.statusName()).isEqualTo("就绪");
        assertThat(drift.desiredState()).isEqualTo(1);
        assertThat(drift.desiredStateName()).isEqualTo("运行");
        assertThat(drift.containerState()).isEqualTo(3);
        assertThat(drift.containerStateName()).isEqualTo("无容器");
        assertThat(drift.lastTouchAt()).isEqualTo(LocalDateTime.of(2026, 9, 14, 22, 10, 0));
        assertThat(drift.volumeSizeBytes()).isEqualTo(2147483648L);
        assertThat(drift.sealedAt()).isNull();
        assertThat(drift.archiveSizeBytes()).isNull();
        assertThat(drift.project().projectId()).isEqualTo("3829492007654321");
        assertThat(drift.project().name()).isEqualTo("英语学习助手");
        assertThat(drift.project().archived()).isFalse();

        // 正常休眠行：期望休眠而实态无容器是正常收敛态（非漂移）；无所属项目为 null（工作区先于
        // 项目存在、软引用容缺）；封存容缺 volumeSizeBytes null
        var hibernated = page.items().get(1);
        assertThat(hibernated.desiredState()).isEqualTo(2);
        assertThat(hibernated.desiredStateName()).isEqualTo("休眠");
        assertThat(hibernated.containerState()).isEqualTo(3);
        assertThat(hibernated.volumeSizeBytes()).isNull();
        assertThat(hibernated.project()).isNull();
    }

    @Test
    void given_wsp014ErrorEnvelope_when_listWorkspaces_then_passThroughWithoutMapping() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.workspaceAppServiceWithErrorTransport(
                400, WSP_014_ERROR_ENVELOPE, wireUrl);

        // 忠实透传（spec #62）：HTTP 400 + 数字业务码 1014（WSP_014）+ provider message 原样
        assertThatThrownBy(() -> appService.list(new AiplatformWorkspaceQuery(99, 99), 1, 20))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(400);
                    assertThat(upstream.envelopeCode()).isEqualTo(1014);
                    assertThat(upstream.getMessage()).isEqualTo("无效的工作区过滤参数");
                });
    }

    // ========== 详情（全量字段 + 资源观测清单 + 项目引用）==========

    @Test
    void given_detailEnvelope_when_getDetail_then_fullFieldsAndResourcesBound() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.workspaceAppServiceWithStubTransport(
                WORKSPACE_DETAIL_ENVELOPE, wireUrl);

        AiplatformWorkspaceDetailResponse detail = appService.getDetail("3829492007777777");

        // 出站路径逐字镜像 provider backoffice 路由
        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/workspaces/3829492007777777");

        // 清单行超集：networkName/provisionError/封存包寻址键/审计列
        assertThat(detail.workspaceId()).isEqualTo("3829492007777777");
        assertThat(detail.containerName()).isEqualTo("ws-3829492007777777");
        assertThat(detail.networkName()).isEqualTo("net-3829492007777777");
        assertThat(detail.kind()).isEqualTo(1);
        assertThat(detail.kindName()).isEqualTo("开发");
        assertThat(detail.status()).isEqualTo(2);
        assertThat(detail.statusName()).isEqualTo("就绪");
        assertThat(detail.provisionError()).isNull();
        assertThat(detail.desiredState()).isEqualTo(3);
        assertThat(detail.desiredStateName()).isEqualTo("封存");
        assertThat(detail.containerState()).isEqualTo(3);
        assertThat(detail.containerStateName()).isEqualTo("无容器");
        assertThat(detail.sealedAt()).isEqualTo(LocalDateTime.of(2026, 9, 13, 18, 0, 0));
        assertThat(detail.archivePath()).isEqualTo("workspace-archives/3829492007777777.tar.gz");
        assertThat(detail.archiveSizeBytes()).isEqualTo(89128960L);
        assertThat(detail.volumeSizeBytes()).isNull();
        assertThat(detail.createdAt()).isEqualTo(LocalDateTime.of(2026, 9, 8, 10, 0, 0));
        assertThat(detail.updatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 13, 18, 0, 0));

        // 中间件资源清单：kind Integer code（1=PostgreSQL 2=Redis，provider 出口无 *Name——忠实镜像）
        // + 容器名 + 容器内回环连接串原文（全量如实呈现，机机签名面无脱敏）
        assertThat(detail.resources()).hasSize(2);
        var pg = detail.resources().get(0);
        assertThat(pg.kind()).isEqualTo(1);
        assertThat(pg.containerName()).isEqualTo("mw-3829492007777777-pg");
        assertThat(pg.internalUrl()).isEqualTo(
                "postgresql://aiedu:secret@mw-3829492007777777-pg:5432/aiedu");
        var redis = detail.resources().get(1);
        assertThat(redis.kind()).isEqualTo(2);
        assertThat(redis.internalUrl()).isEqualTo("redis://mw-3829492007777777-redis:6379/0");

        // 所属项目引用（清单行同形）
        assertThat(detail.project().projectId()).isEqualTo("3829492007654321");
        assertThat(detail.project().archived()).isFalse();
    }

    @Test
    void given_wsp001ErrorEnvelope_when_getDetail_then_passThroughWithoutMapping() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.workspaceAppServiceWithErrorTransport(
                404, WSP_001_ERROR_ENVELOPE, wireUrl);

        assertThatThrownBy(() -> appService.getDetail("3829499999999999"))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(404);
                    assertThat(upstream.envelopeCode()).isEqualTo(1001);
                    assertThat(upstream.getMessage()).isEqualTo("工作区不存在");
                });
    }

    // ========== 四干预动作（无请求体 POST，回执＝详情 DTO）==========

    @Test
    void given_detailEnvelope_when_wake_then_postUrlMirrorsProviderAndReceiptIsDetail() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.workspaceAppServiceWithStubTransport(
                WORKSPACE_DETAIL_ENVELOPE, wireUrl);

        AiplatformWorkspaceDetailResponse receipt = appService.wake("3829492007777777");

        // 出站路径逐字镜像 provider backoffice 路由（POST、无请求体）
        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/workspaces/3829492007777777/wake");

        // 回执＝动作后的观测详情（新事实）：全量字段 + 资源观测列表（同详情 DTO）
        assertThat(receipt.workspaceId()).isEqualTo("3829492007777777");
        assertThat(receipt.desiredState()).isEqualTo(3);
        assertThat(receipt.desiredStateName()).isEqualTo("封存");
        assertThat(receipt.resources()).hasSize(2);
        assertThat(receipt.project().projectId()).isEqualTo("3829492007654321");
    }

    @Test
    void given_detailEnvelope_when_hibernateRebuildSeal_then_postUrlsMirrorProvider() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.workspaceAppServiceWithStubTransport(
                WORKSPACE_DETAIL_ENVELOPE, wireUrl);

        appService.hibernate("3829492007777777");
        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/workspaces/3829492007777777/hibernate");

        appService.rebuild("3829492007777777");
        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/workspaces/3829492007777777/rebuild");

        appService.seal("3829492007777777");
        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/workspaces/3829492007777777/seal");
    }

    @Test
    void given_wsp009ErrorEnvelope_when_hibernate_then_passThroughWithoutMapping() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.workspaceAppServiceWithErrorTransport(
                400, WSP_009_ERROR_ENVELOPE, wireUrl);

        // 封存态休眠（卷已删，先唤醒）——HTTP 400 + 数字业务码 1009（WSP_009）原样透传
        assertThatThrownBy(() -> appService.hibernate("3829492007777777"))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(400);
                    assertThat(upstream.envelopeCode()).isEqualTo(1009);
                    assertThat(upstream.getMessage()).isEqualTo("工作区置备状态不合法");
                });
    }

    @Test
    void given_wsp015ErrorEnvelope_when_rebuild_then_passThroughWithoutMapping() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.workspaceAppServiceWithErrorTransport(
                409, WSP_015_ERROR_ENVELOPE, wireUrl);

        // run 在途拒（不打断用户正在进行的生成）——HTTP 409 + 数字业务码 1015（WSP_015）原样透传
        assertThatThrownBy(() -> appService.rebuild("3829492007777777"))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(409);
                    assertThat(upstream.envelopeCode()).isEqualTo(1015);
                    assertThat(upstream.getMessage()).isEqualTo(
                            "编码 run 进行中，沙箱动作被拒（先取消 run 或等收口）");
                });
    }
}
