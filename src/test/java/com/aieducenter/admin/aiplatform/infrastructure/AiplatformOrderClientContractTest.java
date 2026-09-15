package com.aieducenter.admin.aiplatform.infrastructure;

import java.net.http.HttpHeaders;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aieducenter.admin.aiplatform.application.AiplatformUpstreamException;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformOrderQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformOrderSummaryResponse;
import com.cartisan.openapi.client.BinaryResponse;
import com.cartisan.web.response.PageResponse;

/**
 * aiplatform 订单域读路径的 wire 契约测试（对接 aiplatform #155/#156 已冻结契约，issue #64）。
 *
 * <p>契约事实（对照 aiplatform 源码 {@code BackofficeOrderController} /
 * {@code BackofficeOrderSummaryResponse} / {@code BackofficeOrderDetailResponse}）：</p>
 * <ul>
 *   <li>清单/详情均包 {@code ApiResponse<T>} 信封，须按信封反序列化并取 {@code .data()}；
 *       清单 data 为 {@code PageResponse{items,total,page,size}}（page 1-based）。</li>
 *   <li>清单 {@code status} 多选为<strong>逗号分隔单值</strong>（{@code status=1,5}）——aiplatform
 *       签名协议按 query 参数名去重，重复参数只有末值入签（区别于 payment 的重复参数展开）。</li>
 *   <li>金额 {@code Long}（分）、{@code status} Integer code + {@code statusName} 中文名、
 *       时间 ISO——类型绑定靠真实 Jackson 反序列化钉死。</li>
 *   <li>源码包<strong>无信封</strong>：{@code application/gzip} 二进制 + Content-Disposition
 *       文件名，经 cartisan-openapi {@code download}（cartisan-boot#30）保全原始字节与响应头。</li>
 *   <li>分页（spec #62 平台分页统一决议目标态）：北向 1-based 直传、回显 provider 回报值，
 *       全链零 ±1 换算（区别于 payment 的 page-1）。</li>
 * </ul>
 *
 * <p>本测试 <strong>不</strong> mock {@code AiplatformClient}（方法边界 mock 会绕过 wire 反序列化
 * ——已知反例），经 {@link AiplatformWireTestSupport} 子类化 {@code OpenApiClient} 仅替换 HTTP
 * 传输，真实 {@code TypeReference} 反序列化 + 真实 AppService 映射完整保留。</p>
 *
 * @since 0.1.0
 */
class AiplatformOrderClientContractTest {

    /**
     * aiplatform GET /api/backoffice/orders 的真实成功响应形状（信封 + PageResponse：两行，一已支付一待报价）。
     * Long 字段（amount/total）为 <strong>JSON string</strong>——cartisan-web 全局
     * {@code Long→ToStringSerializer} 的出口口径（spec #62「金额 Long（分、JSON string）」）。
     */
    private static final String ORDER_PAGE_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "items": [
                  {
                    "id": "3829492001234567",
                    "projectId": "3829492007654321",
                    "projectName": "英语学习助手",
                    "ownerDisplayName": "文野",
                    "status": 3,
                    "statusName": "已支付",
                    "amount": "1999000",
                    "currency": "CNY",
                    "createdAt": "2026-09-10T14:20:00",
                    "quotedAt": "2026-09-11T09:00:00"
                  },
                  {
                    "id": "3829492005555444",
                    "projectId": "3829492007654321",
                    "projectName": "英语学习助手",
                    "ownerDisplayName": null,
                    "status": 1,
                    "statusName": "待报价",
                    "amount": null,
                    "currency": null,
                    "createdAt": "2026-09-14T18:30:00",
                    "quotedAt": null
                  }
                ],
                "total": "42",
                "page": 3,
                "size": 20
              },
              "requestId": "req-1a2b3c",
              "errors": null
            }
            """;

    /** aiplatform GET /api/backoffice/orders/{id} 的真实成功响应形状（已报价单：两次报价留痕，新→旧）。 */
    private static final String ORDER_DETAIL_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "id": "3829492001234567",
                "projectId": "3829492007654321",
                "projectName": "英语学习助手",
                "ownerDisplayName": "文野",
                "status": 2,
                "statusName": "已报价",
                "amount": "2199000",
                "currency": "CNY",
                "note": "含加急费用",
                "priceEntries": [
                  {
                    "id": "3829492011111111",
                    "amount": "2199000",
                    "currency": "CNY",
                    "note": "含加急费用",
                    "operatorId": "1234567890123456",
                    "operatorName": "报价运营",
                    "createdAt": "2026-09-12T10:00:00"
                  },
                  {
                    "id": "3829492009999999",
                    "amount": "1999000",
                    "currency": "CNY",
                    "note": "首次报价",
                    "operatorId": null,
                    "operatorName": null,
                    "createdAt": "2026-09-11T09:00:00"
                  }
                ],
                "prdSnapshot": "# PRD\\n\\n做一个英语学习助手……",
                "createdAt": "2026-09-10T14:20:00",
                "quotedAt": "2026-09-11T09:00:00",
                "paidAt": null,
                "archivedAt": null,
                "archiveOperatorId": null,
                "archiveOperatorName": null,
                "cancelledAt": null,
                "cancelReason": null,
                "cancelOperatorId": null,
                "cancelOperatorName": null
              },
              "requestId": "req-2b3c4d",
              "errors": null
            }
            """;

    /** aiplatform 过滤参数绑定失败的真实错误形状：HTTP 400 + 信封 code=5010（ORD_010 数字业务码）。 */
    private static final String ORD_010_ERROR_ENVELOPE = """
            {
              "code": 5010,
              "message": "无效的订单过滤参数",
              "data": null,
              "requestId": "req-3c4d5e",
              "errors": null
            }
            """;

    /** aiplatform 订单不存在的真实错误形状：HTTP 404 + 信封 code=5001（ORD_001 数字业务码）。 */
    private static final String ORD_001_ERROR_ENVELOPE = """
            {
              "code": 5001,
              "message": "订单不存在",
              "data": null,
              "requestId": "req-4d5e6f",
              "errors": null
            }
            """;

    /** gzip 魔数起始、含非法 UTF-8 字节（0xff/0xfe）与 NUL 的字节流——ofString 解码会损坏的回归锚点。 */
    private static final byte[] TAR_GZ_BYTES = {
            (byte) 0x1f, (byte) 0x8b, 0x08, 0x00, (byte) 0xff, (byte) 0xfe, 0x41, 0x00, 0x0d, 0x0a
    };

    private static final String SOURCE_DISPOSITION = "attachment; filename=\"3829492001234567-source.tar.gz\"";

    // ========== 清单（四维检索 + 分页 1-based 透传）==========

    @Test
    void given_fullFourDimensionFilter_when_listOrders_then_wireUrlMirrorsProviderContract() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.orderAppServiceWithStubTransport(
                ORDER_PAGE_ENVELOPE, null, wireUrl);

        appService.list(new AiplatformOrderQuery(
                List.of(1, 5),
                LocalDateTime.of(2026, 9, 1, 0, 0, 0),
                LocalDateTime.of(2026, 9, 15, 23, 59, 59),
                "auth0|65f2c8a1", "3829492001234567"), 3, 20);

        // 出站 wire 形状逐字镜像 provider 契约：page 1-based 直传（无 ±1）；status 逗号分隔单值
        // （签名按参数名去重——禁用 payment 式重复参数）；时间为全秒 ISO；特殊字符 URL 编码
        assertThat(wireUrl[0]).isEqualTo("http://stub-aiplatform/api/backoffice/orders"
                + "?page=3&size=20"
                + "&status=1%2C5"
                + "&createdFrom=2026-09-01T00%3A00%3A00"
                + "&createdTo=2026-09-15T23%3A59%3A59"
                + "&externalId=auth0%7C65f2c8a1"
                + "&orderId=3829492001234567");
    }

    @Test
    void given_pageEnvelope_when_listOrders_then_envelopeUnwrappedAndTypesBound() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.orderAppServiceWithStubTransport(
                ORDER_PAGE_ENVELOPE, null, wireUrl);

        PageResponse<AiplatformOrderSummaryResponse> page =
                appService.list(new AiplatformOrderQuery(null, null, null, null, null), 1, 20);

        // 信封正确拆开（items 非 null）——证明 ORDER_PAGE_TYPEREF 按 ApiResponse<PageResponse<…>> 反序列化并取 .data()
        assertThat(page.items()).hasSize(2);

        // 分页回显：provider 回报值原样透传（1-based，含 clamp 后值）——BFF 不夹取不换算
        assertThat(page.total()).isEqualTo(42L);
        assertThat(page.page()).isEqualTo(3);
        assertThat(page.size()).isEqualTo(20);

        var paid = page.items().get(0);
        // 类型口径：id String（TSID 十进制串）、status Integer code + statusName 中文名、金额 Long（分）
        assertThat(paid.id()).isEqualTo("3829492001234567");
        assertThat(paid.projectId()).isEqualTo("3829492007654321");
        assertThat(paid.projectName()).isEqualTo("英语学习助手");
        assertThat(paid.ownerDisplayName()).isEqualTo("文野");
        assertThat(paid.status()).isEqualTo(3);
        assertThat(paid.statusName()).isEqualTo("已支付");
        assertThat(paid.amount()).isEqualTo(1999000L);
        assertThat(paid.currency()).isEqualTo("CNY");
        assertThat(paid.createdAt()).isEqualTo(LocalDateTime.of(2026, 9, 10, 14, 20, 0));
        assertThat(paid.quotedAt()).isEqualTo(LocalDateTime.of(2026, 9, 11, 9, 0, 0));

        // 待报价行的 NULL 口径（全局 Jackson 含 null 序列化，反序列化回 null）
        var pending = page.items().get(1);
        assertThat(pending.status()).isEqualTo(1);
        assertThat(pending.statusName()).isEqualTo("待报价");
        assertThat(pending.amount()).isNull();
        assertThat(pending.currency()).isNull();
        assertThat(pending.quotedAt()).isNull();
        assertThat(pending.ownerDisplayName()).isNull();
    }

    @Test
    void given_ord010ErrorEnvelope_when_listOrders_then_passThroughWithoutMapping() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.orderAppServiceWithErrorTransport(
                400, ORD_010_ERROR_ENVELOPE, wireUrl);

        // 忠实透传（spec #62）：HTTP 400 + 数字业务码 5010（ORD_010）+ provider message 原样
        assertThatThrownBy(() -> appService.list(new AiplatformOrderQuery(null, null, null, null, null), 1, 20))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(400);
                    assertThat(upstream.envelopeCode()).isEqualTo(5010);
                    assertThat(upstream.getMessage()).isEqualTo("无效的订单过滤参数");
                });
    }

    // ========== 详情（价目历史 append-only 嵌套）==========

    @Test
    void given_detailEnvelope_when_getDetail_then_priceEntriesBoundWithOperatorLimbs() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.orderAppServiceWithStubTransport(
                ORDER_DETAIL_ENVELOPE, null, wireUrl);

        var detail = appService.getDetail("3829492001234567");

        // 出站路径逐字镜像 provider backoffice 路由
        assertThat(wireUrl[0]).isEqualTo("http://stub-aiplatform/api/backoffice/orders/3829492001234567");

        // 顶层字段：金额 Long（分）、现值取最新价目行、PRD 快照正文
        assertThat(detail.id()).isEqualTo("3829492001234567");
        assertThat(detail.status()).isEqualTo(2);
        assertThat(detail.statusName()).isEqualTo("已报价");
        assertThat(detail.amount()).isEqualTo(2199000L);
        assertThat(detail.note()).isEqualTo("含加急费用");
        assertThat(detail.prdSnapshot()).startsWith("# PRD");

        // 价目历史：append-only 全量，新 → 旧；新行带操作者两肢、存量行操作者为空
        assertThat(detail.priceEntries()).hasSize(2);
        var newest = detail.priceEntries().get(0);
        assertThat(newest.id()).isEqualTo("3829492011111111");
        assertThat(newest.amount()).isEqualTo(2199000L);
        assertThat(newest.currency()).isEqualTo("CNY");
        assertThat(newest.note()).isEqualTo("含加急费用");
        assertThat(newest.operatorId()).isEqualTo("1234567890123456");
        assertThat(newest.operatorName()).isEqualTo("报价运营");
        assertThat(newest.createdAt()).isEqualTo(LocalDateTime.of(2026, 9, 12, 10, 0, 0));
        var oldest = detail.priceEntries().get(1);
        assertThat(oldest.operatorId()).isNull();
        assertThat(oldest.operatorName()).isNull();

        // 未支付/未归档/未取消的 NULL 时点与留痕两肢
        assertThat(detail.quotedAt()).isEqualTo(LocalDateTime.of(2026, 9, 11, 9, 0, 0));
        assertThat(detail.paidAt()).isNull();
        assertThat(detail.archivedAt()).isNull();
        assertThat(detail.archiveOperatorId()).isNull();
        assertThat(detail.cancelledAt()).isNull();
        assertThat(detail.cancelReason()).isNull();
        assertThat(detail.cancelOperatorId()).isNull();
        assertThat(detail.cancelOperatorName()).isNull();
    }

    @Test
    void given_ord001ErrorEnvelope_when_getDetail_then_passThroughWithoutMapping() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.orderAppServiceWithErrorTransport(
                404, ORD_001_ERROR_ENVELOPE, wireUrl);

        assertThatThrownBy(() -> appService.getDetail("3829499999999999"))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(404);
                    assertThat(upstream.envelopeCode()).isEqualTo(5001);
                    assertThat(upstream.getMessage()).isEqualTo("订单不存在");
                });
    }

    // ========== 源码包（二进制无信封，字节与响应头保全）==========

    @Test
    void given_tarGzBinaryResponse_when_downloadSourcePackage_then_bytesAndHeadersPreserved() {
        String[] wireUrl = new String[1];
        HttpHeaders providerHeaders = HttpHeaders.of(Map.of(
                "Content-Type", List.of("application/gzip"),
                "Content-Disposition", List.of(SOURCE_DISPOSITION)), (a, b) -> true);
        var appService = AiplatformWireTestSupport.orderAppServiceWithStubTransport(
                null, new BinaryResponse(200, providerHeaders, TAR_GZ_BYTES), wireUrl);

        var pkg = appService.getSourcePackage("3829492001234567");

        // 出站路径逐字镜像 provider backoffice 路由
        assertThat(wireUrl[0]).isEqualTo(
                "http://stub-aiplatform/api/backoffice/orders/3829492001234567/source-package");

        // gzip 字节逐位相等（含 0xff/0xfe/NUL——ofString 解码损坏的回归锚点），内存中转无信封
        assertThat(pkg.content()).containsExactly(TAR_GZ_BYTES);
        // provider 响应头 raw 值透传（不打解析、不重构文件名）
        assertThat(pkg.contentType()).isEqualTo("application/gzip");
        assertThat(pkg.contentDisposition()).isEqualTo(SOURCE_DISPOSITION);
    }

    @Test
    void given_ord001OnDownload_when_downloadSourcePackage_then_passThroughWithoutMapping() {
        String[] wireUrl = new String[1];
        var appService = AiplatformWireTestSupport.orderAppServiceWithErrorTransport(
                404, ORD_001_ERROR_ENVELOPE, wireUrl);

        // download 的 ≥400 由框架抛 OpenApiClientException（错误信封在 body 里）——AiplatformClient
        // 同款翻译透传，与 JSON 端点错误路径一致
        assertThatThrownBy(() -> appService.getSourcePackage("3829499999999999"))
                .isInstanceOf(AiplatformUpstreamException.class)
                .satisfies(e -> {
                    var upstream = (AiplatformUpstreamException) e;
                    assertThat(upstream.httpStatus()).isEqualTo(404);
                    assertThat(upstream.envelopeCode()).isEqualTo(5001);
                    assertThat(upstream.getMessage()).isEqualTo("订单不存在");
                });
    }
}
