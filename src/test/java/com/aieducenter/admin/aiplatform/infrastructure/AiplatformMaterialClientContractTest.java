package com.aieducenter.admin.aiplatform.infrastructure;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aieducenter.admin.aiplatform.application.AiplatformUpstreamException;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformMaterialQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformMaterialDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformMaterialSummaryResponse;
import com.cartisan.openapi.client.OpenApiClient;
import com.cartisan.openapi.config.CartisanOpenapiProperties;
import com.cartisan.web.response.PageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * aiplatform 知识素材域（清单/详情/停用⇄启用/删除）的 wire 契约测试（对接 aiplatform #166
 * 知识库管理，issue #69；删除出站依赖 cartisan-boot#33 {@code OpenApiClient.delete}）。
 *
 * <p>契约事实（2026-09-16 对照 aiplatform 源码 {@code BackofficeMaterialController} /
 * {@code BackofficeKnowledgeAppService} / {@code BackofficeMaterialSummaryResponse} /
 * {@code BackofficeMaterialDetailResponse} / {@code MaterialStatus} / {@code KnowledgeMessage} /
 * {@code ErrorCodePrefix} 核实）：</p>
 * <ul>
 *   <li>五端点均包 {@code ApiResponse<T>} 信封，按信封反序列化取 {@code .data()}；清单 data 为
 *       {@code PageResponse{items,total,page,size}}（page 1-based、total JSON string——cartisan-web
 *       全局 Long→ToStringSerializer），详情 data 多一 {@code content} 全文字段，三治理动作回执
 *       data 共形＝summary 单行（删除回执＝删除前终态——provider 契约如此，逐字镜像）。</li>
 *   <li>行字段：{@code id} String（TSID 十进制串）；{@code status} Integer code（1=启用 2=停用）
 *       + {@code statusName} 中文名随行；{@code sunkAt} 首沉淀时间（重沉淀与治理动作不改）；
 *       {@code operatorId/operatorName}＝最近管理动作（停用或启用）操作者，未治理过为 null——
 *       删除无行可留、不留痕。</li>
 *   <li>清单三维过滤可组合、均可缺省：status 单选（与订单多选有意不同）、sunkFrom/To 沉淀时间
 *       闭区间（Instant，出站 {@code Instant.toString()} 确定形，':' URL 编码 %3A）、projectId
 *       来源项目精确（查无＝空清单 200）；排序定死沉淀时间倒序；page 1-based 直传，provider
 *       clamp（page≥1、size∈[1,100] 默认 20）行为透传。</li>
 *   <li>三治理动作全无请求体：停用/启用经 {@code post(url, null, …)} 发空 body；删除是 HTTP
 *       <strong>DELETE</strong> 动词（provider {@code @DeleteMapping}），经框架
 *       {@code OpenApiClient.delete}（cartisan-boot#33）出站——get/post 发不出 DELETE。</li>
 *   <li>错误透传（数字业务码＝KNW 域码 2×1000＋序号）：KNW_005→2005（404，素材不存在——含
 *       畸形 id、重复删除）、KNW_006→2006（400，操作者不能为空——缺 X-User-Id/X-User-Name
 *       出站头必被拦，知识治理动作必留痕，与单价表缺头落空有意不同）、KNW_007→2007（400，
 *       过滤参数绑定失败——北向绑定层已拦类型不匹配，仅未知 code 透传到此）。</li>
 * </ul>
 *
 * <p>本测试 <strong>不</strong> mock {@code AiplatformClient}（方法边界 mock 会绕过 wire 反序列化
 * ——已知反例），经 {@link AiplatformWireTestSupport} 子类化 {@code OpenApiClient} 仅替换 HTTP
 * 传输，真实 {@code TypeReference} 反序列化 + 真实 AppService 映射完整保留；删除端点另以
 * 「get/post 即失败」的极简 stub 钉死 DELETE 动词不可替代。</p>
 *
 * @since 0.1.0
 */
class AiplatformMaterialClientContractTest {

    /** 停用治理动作后的行（治理留痕面）：status=2 停用 + 操作者两列在场。 */
    private static final String SUMMARY_GOVERNED = """
            {
              "id": "3840600001111111",
              "kind": "PRD",
              "projectId": "3829492007654321",
              "projectName": "英语学习助手",
              "title": "英语学习助手 · PRD",
              "status": 2,
              "statusName": "停用",
              "sunkAt": "2026-09-01T08:30:00Z",
              "operatorId": "3829492001234567",
              "operatorName": "内容治理运营"
            }
            """;

    /**
     * aiplatform GET /api/backoffice/materials 的真实成功响应形状（信封 + PageResponse）：
     * 沉淀时间倒序两行——首行已停用（带治理操作者留痕），次行未治理（status=1 启用、
     * 操作者两列 null——如实绑定）；total 为 JSON string。
     */
    private static final String MATERIAL_PAGE_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "items": [
                  %s,
                  {
                    "id": "3840600002222222",
                    "kind": "PRD",
                    "projectId": "3829492005555444",
                    "projectName": "待办清单应用",
                    "title": "待办清单应用 · PRD",
                    "status": 1,
                    "statusName": "启用",
                    "sunkAt": "2026-08-20T12:00:00Z",
                    "operatorId": null,
                    "operatorName": null
                  }
                ],
                "total": "2",
                "page": 1,
                "size": 20
              },
              "requestId": "req-m1a2t",
              "errors": null
            }
            """.formatted(SUMMARY_GOVERNED);

    /**
     * aiplatform GET /api/backoffice/materials/{id} 的真实成功响应形状：
     * 元数据（与清单行同形）＋素材全文 content（块按 seq 以空行拼接——多段形态代表面）。
     */
    private static final String MATERIAL_DETAIL_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "id": "3840600001111111",
                "kind": "PRD",
                "projectId": "3829492007654321",
                "projectName": "英语学习助手",
                "title": "英语学习助手 · PRD",
                "status": 1,
                "statusName": "启用",
                "sunkAt": "2026-09-01T08:30:00Z",
                "operatorId": null,
                "operatorName": null,
                "content": "# PRD\\n\\n做一个英语学习助手……\\n\\n## 功能范围\\n\\n1. 单词卡片\\n2. 复习计划"
              },
              "requestId": "req-m3d4e",
              "errors": null
            }
            """;

    /** aiplatform POST /api/backoffice/materials/{id}/disable 的真实成功响应形状：重读登记行的最新状态。 */
    private static final String DISABLE_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": %s,
              "requestId": "req-m5d6i",
              "errors": null
            }
            """.formatted(SUMMARY_GOVERNED);

    /** aiplatform POST /api/backoffice/materials/{id}/enable 的真实成功响应形状：恢复启用 + 操作者留痕。 */
    private static final String ENABLE_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "id": "3840600001111111",
                "kind": "PRD",
                "projectId": "3829492007654321",
                "projectName": "英语学习助手",
                "title": "英语学习助手 · PRD",
                "status": 1,
                "statusName": "启用",
                "sunkAt": "2026-09-01T08:30:00Z",
                "operatorId": "3829492001234567",
                "operatorName": "内容治理运营"
              },
              "requestId": "req-m7e8n",
              "errors": null
            }
            """;

    /** aiplatform DELETE /api/backoffice/materials/{id} 的真实成功响应形状：删除前终态（确认移除了什么）。 */
    private static final String DELETE_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": %s,
              "requestId": "req-m9d0l",
              "errors": null
            }
            """.formatted(SUMMARY_GOVERNED);

    /** 操作者缺（缺 X-User-Id/X-User-Name 出站头）：HTTP 400 + 信封 code=2006（KNW_006，域码 2）。 */
    private static final String KNW_006_ERROR_ENVELOPE = """
            {
              "code": 2006,
              "message": "操作者不能为空",
              "data": null,
              "requestId": "req-k6o0p",
              "errors": null
            }
            """;

    /** 素材不存在（含畸形 id、重复删除）：HTTP 404 + 信封 code=2005（KNW_005）。 */
    private static final String KNW_005_ERROR_ENVELOPE = """
            {
              "code": 2005,
              "message": "知识素材不存在",
              "data": null,
              "requestId": "req-k5n0f",
              "errors": null
            }
            """;

    private static final String MATERIAL_ID = "3840600001111111";

    // ========== 清单（三维过滤 + Instant 出站形态 + 1-based 回显）==========

    @Test
    void given_filtersAndPage_when_listMaterials_then_wireUrlMirrorsProviderContract() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.materialAppServiceWithStubTransport(
                MATERIAL_PAGE_ENVELOPE, wireUrl, new String[1]);

        appService.list(new AiplatformMaterialQuery(
                2, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-15T23:59:59Z"),
                "3829492007654321"), 1, 20);

        // 出站 wire 形状逐字镜像 provider 契约：page 1-based 直传零换算，status 单选单值，
        // sunkFrom/To Instant（':' URL 编码 %3A——与 provider 签名侧 raw query 同形入签），projectId 精确
        assertThat(wireUrl[0]).isEqualTo("http://stub-aiplatform/api/backoffice/materials"
                + "?page=1&size=20&status=2"
                + "&sunkFrom=2026-09-01T00%3A00%3A00Z&sunkTo=2026-09-15T23%3A59%3A59Z"
                + "&projectId=3829492007654321");
    }

    @Test
    void given_nullFilters_when_listMaterials_then_filtersOmittedFromWireUrl() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.materialAppServiceWithStubTransport(
                MATERIAL_PAGE_ENVELOPE, wireUrl, new String[1]);

        appService.list(new AiplatformMaterialQuery(null, null, null, null), 2, 50);

        // 缺省＝全量：null 过滤维度不出站；page 直传
        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/materials?page=2&size=50");
    }

    @Test
    void given_materialPageEnvelope_when_list_then_envelopeUnwrappedAndRowsBound() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.materialAppServiceWithStubTransport(
                MATERIAL_PAGE_ENVELOPE, wireUrl, new String[1]);

        PageResponse<AiplatformMaterialSummaryResponse> page = appService.list(
                new AiplatformMaterialQuery(2, null, null, null), 1, 20);

        // 信封正确拆开——证明 MATERIAL_PAGE_TYPEREF 按 ApiResponse<PageResponse<…>> 反序列化；
        // total JSON string→long、分页回显 provider 回报值（1-based）——BFF 不夹取不换算
        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);

        // 首行：已治理行——status Integer code + statusName 随行、操作者留痕两肢、sunkAt 首沉淀时间
        var governed = page.items().get(0);
        assertThat(governed.id()).isEqualTo(MATERIAL_ID);
        assertThat(governed.kind()).isEqualTo("PRD");
        assertThat(governed.projectId()).isEqualTo("3829492007654321");
        assertThat(governed.projectName()).isEqualTo("英语学习助手");
        assertThat(governed.title()).isEqualTo("英语学习助手 · PRD");
        assertThat(governed.status()).isEqualTo(2);
        assertThat(governed.statusName()).isEqualTo("停用");
        assertThat(governed.sunkAt()).isEqualTo(Instant.parse("2026-09-01T08:30:00Z"));
        assertThat(governed.operatorId()).isEqualTo("3829492001234567");
        assertThat(governed.operatorName()).isEqualTo("内容治理运营");

        // 次行：未治理行——status=1 启用、操作者两列 null 如实绑定（全局 Jackson 含 null）
        var untreated = page.items().get(1);
        assertThat(untreated.id()).isEqualTo("3840600002222222");
        assertThat(untreated.status()).isEqualTo(1);
        assertThat(untreated.statusName()).isEqualTo("启用");
        assertThat(untreated.operatorId()).isNull();
        assertThat(untreated.operatorName()).isNull();
    }

    // ========== 详情（元数据 + PRD 全文）==========

    @Test
    void given_materialId_when_getDetail_then_wireUrlMirrorsProvider() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.materialAppServiceWithStubTransport(
                MATERIAL_DETAIL_ENVELOPE, wireUrl, new String[1]);

        appService.getDetail(MATERIAL_ID);

        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/materials/" + MATERIAL_ID);
    }

    @Test
    void given_detailEnvelope_when_getDetail_then_metadataAndFullContentBound() {
        var appService = AiplatformWireTestSupport.materialAppServiceWithStubTransport(
                MATERIAL_DETAIL_ENVELOPE, new String[1], new String[1]);

        AiplatformMaterialDetailResponse detail = appService.getDetail(MATERIAL_ID);

        // 详情＝清单行同形元数据＋素材全文：content 块按 seq 以空行拼接（多段形态原样到达，
        // 不经任何截断/改写）；未治理过的操作者两列 null 如实绑定
        assertThat(detail.id()).isEqualTo(MATERIAL_ID);
        assertThat(detail.kind()).isEqualTo("PRD");
        assertThat(detail.status()).isEqualTo(1);
        assertThat(detail.statusName()).isEqualTo("启用");
        assertThat(detail.sunkAt()).isEqualTo(Instant.parse("2026-09-01T08:30:00Z"));
        assertThat(detail.operatorId()).isNull();
        assertThat(detail.content()).startsWith("# PRD");
        assertThat(detail.content()).contains("\n\n## 功能范围\n\n1. 单词卡片\n2. 复习计划");
    }

    // ========== 停用⇄启用（无请求体 + summary 回执）==========

    @Test
    void given_materialId_when_disable_then_wireUrlMirrorsProviderAndNoBody() {
        String[] wireUrl = new String[1];
        String[] wireBody = new String[1];
        var appService = AiplatformWireTestSupport.materialAppServiceWithStubTransport(
                DISABLE_ENVELOPE, wireUrl, wireBody);

        appService.disable(MATERIAL_ID);

        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/materials/" + MATERIAL_ID + "/disable");
        // 无请求体（provider 端只读路径参数——同沙箱四动作/单价表停用先例）
        assertThat(wireBody[0]).isNull();
    }

    @Test
    void given_disableEnvelope_when_disable_then_reReadSummaryReceiptBound() {
        var appService = AiplatformWireTestSupport.materialAppServiceWithStubTransport(
                DISABLE_ENVELOPE, new String[1], new String[1]);

        AiplatformMaterialSummaryResponse receipt = appService.disable(MATERIAL_ID);

        // 回执＝provider 重读登记行的最新状态：已停用 + 治理操作者落素材级
        assertThat(receipt.id()).isEqualTo(MATERIAL_ID);
        assertThat(receipt.status()).isEqualTo(2);
        assertThat(receipt.statusName()).isEqualTo("停用");
        assertThat(receipt.operatorId()).isEqualTo("3829492001234567");
        assertThat(receipt.operatorName()).isEqualTo("内容治理运营");
    }

    @Test
    void given_enableEnvelope_when_enable_then_wireUrlAndReceiptMirrorProvider() {
        String[] wireUrl = new String[1];
        String[] wireBody = new String[1];
        var appService = AiplatformWireTestSupport.materialAppServiceWithStubTransport(
                ENABLE_ENVELOPE, wireUrl, wireBody);

        AiplatformMaterialSummaryResponse receipt = appService.enable(MATERIAL_ID);

        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/materials/" + MATERIAL_ID + "/enable");
        assertThat(wireBody[0]).isNull();
        // 回执＝恢复启用 + 操作者留痕（可逆开关的另一侧）
        assertThat(receipt.status()).isEqualTo(1);
        assertThat(receipt.statusName()).isEqualTo("启用");
        assertThat(receipt.operatorName()).isEqualTo("内容治理运营");
    }

    // ========== 删除（HTTP DELETE 动词 + 删除前终态回执）==========

    @Test
    void given_deleteOnlyTransport_when_delete_then_deleteVerbIsTheOnlyReachablePath() {
        // 极简 stub：get/post 一律失败，仅 delete 可达——钉死删除出站走框架 OpenApiClient.delete
        // （cartisan-boot#33）：provider 是 @DeleteMapping，get/post 发不出 DELETE 动词
        String[] wireUrl = new String[1];
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        OpenApiClient deleteOnlyTransport = new OpenApiClient(new CartisanOpenapiProperties(), null, mapper) {
            @Override
            public <T> T get(String url, TypeReference<T> typeReference) {
                throw new AssertionError("素材删除必须走 DELETE 动词，不应发出 GET: " + url);
            }

            @Override
            public <T> T post(String url, Object body, TypeReference<T> typeReference) {
                throw new AssertionError("素材删除必须走 DELETE 动词，不应发出 POST: " + url);
            }

            @Override
            public <T> T delete(String url, TypeReference<T> typeReference) {
                wireUrl[0] = url;
                try {
                    return mapper.readValue(DELETE_ENVELOPE, typeReference);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        var appService = new com.aieducenter.admin.aiplatform.application.AiplatformMaterialAppService(
                new AiplatformClient(deleteOnlyTransport, "http://stub-aiplatform"));

        AiplatformMaterialSummaryResponse receipt = appService.delete(MATERIAL_ID);

        // DELETE 动词唯一可达 + wire 路径逐字镜像 provider（无请求体——delete 方法无 body 参数）
        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/materials/" + MATERIAL_ID);
        // 回执＝删除前终态（provider 契约如此，逐字镜像——确认移除了什么）
        assertThat(receipt.id()).isEqualTo(MATERIAL_ID);
        assertThat(receipt.status()).isEqualTo(2);
        assertThat(receipt.statusName()).isEqualTo("停用");
    }

    @Test
    void given_deleteEnvelope_when_delete_then_preDeleteTerminalStateBound() {
        var appService = AiplatformWireTestSupport.materialAppServiceWithStubTransport(
                DELETE_ENVELOPE, new String[1], new String[1]);

        AiplatformMaterialSummaryResponse receipt = appService.delete(MATERIAL_ID);

        // 删除前终态回执：本例删除的是一行已停用素材（status=2 + 此前治理留痕原样在场）
        assertThat(receipt.status()).isEqualTo(2);
        assertThat(receipt.operatorId()).isEqualTo("3829492001234567");
        assertThat(receipt.sunkAt()).isEqualTo(Instant.parse("2026-09-01T08:30:00Z"));
    }

    // ========== 错误透传（KNW_ 数字业务码，不映射）==========

    @Test
    void given_knw006ErrorEnvelope_when_disable_then_passThroughWithoutMapping() {
        var appService = AiplatformWireTestSupport.materialAppServiceWithErrorTransport(
                400, KNW_006_ERROR_ENVELOPE, new String[1]);

        // 忠实透传（spec #62）：HTTP 400 + 数字业务码 2006（KNW_006＝域码 2×1000＋6）+ provider
        // message 原样——缺 X-User-Id/X-User-Name 出站头时 provider 域面守卫的必拦口径
        assertThatThrownBy(() -> appService.disable(MATERIAL_ID))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(400);
                    assertThat(upstream.envelopeCode()).isEqualTo(2006);
                    assertThat(upstream.getMessage()).isEqualTo("操作者不能为空");
                });
    }

    @Test
    void given_knw005ErrorEnvelope_when_delete_then_passThroughWithoutMapping() {
        var appService = AiplatformWireTestSupport.materialAppServiceWithErrorTransport(
                404, KNW_005_ERROR_ENVELOPE, new String[1]);

        // 重复删除（首次删除后行已不在）：HTTP 404 + 数字业务码 2005（KNW_005）+ message 原样
        assertThatThrownBy(() -> appService.delete(MATERIAL_ID))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(404);
                    assertThat(upstream.envelopeCode()).isEqualTo(2005);
                    assertThat(upstream.getMessage()).isEqualTo("知识素材不存在");
                });
    }
}
