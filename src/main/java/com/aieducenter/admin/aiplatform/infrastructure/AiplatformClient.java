package com.aieducenter.admin.aiplatform.infrastructure;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import com.aieducenter.admin.aiplatform.application.AiplatformUpstreamException;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformAccountProfileWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformConversationEntryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformCostOverviewWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformCostWindowWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformMaterialDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformMaterialListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformMaterialSummaryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderCancelWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderQuoteWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderSummaryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformPriceEntryListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformPriceEntryRepriceWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformPrdWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectCostDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectCostWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectSummaryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformUnitPriceEntryRepriceWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformUnitPriceEntryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformUnpricedUsageWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformVersionDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformVersionWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformWorkspaceDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformWorkspaceListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformWorkspaceSummaryWireResponse;
import com.cartisan.openapi.client.BinaryResponse;
import com.cartisan.openapi.client.OpenApiClient;
import com.cartisan.openapi.client.OpenApiClientException;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.response.PageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * aiplatform 签名 HTTP 客户端——封装 {@link OpenApiClient}，屏蔽 wire 层细节（issue #63 T1 基座）。
 *
 * <p>所有对 aiplatform 后台管理面（{@code /api/backoffice/**}，机机签名）的调用都经此客户端发起，
 * 自动签名（admin 自身 apiKey/admin-console，复用框架 {@link OpenApiClient} 既有 HMAC-SHA256
 * 五头签名，无需新凭据——见 ADR-0007）。操作者身份经框架 {@code RequestContext}→
 * {@code X-User-Id/X-User-Name} 自动透传，aiplatform 据此落审计列。</p>
 *
 * <p>为 infrastructure 包内裸 {@code @Component}（BFF 出站客户端，不走 {@code @Port/@Adapter}，
 * 详见 ADR-0007，与 {@code PaymentClient} / {@code AccountClient} / {@code AppRegistryClient} 同款）。
 * 六域（订单/项目/沙箱/成本/单价表/知识素材）外加账号读口共用本客户端——同一 downstream、
 * 同一签名身份，各域只加方法。</p>
 *
 * <p>信封与错误契约（spec #62 定稿）：aiplatform 出口统一 {@code ApiResponse<T>}，本客户端解包取
 * {@code .data()}；下游 ≥400 时框架抛 {@link OpenApiClientException}，此处统一翻译为
 * {@link AiplatformUpstreamException}（provider 数字业务码 + HTTP 状态 + message 原样透传，
 * 不做 BaseCodeMessage 映射）——六域 AppService 无需逐处 try/catch。</p>
 *
 * @since 0.1.0
 */
@Component
public class AiplatformClient {

    private static final Logger log = LoggerFactory.getLogger(AiplatformClient.class);

    // aiplatform 账号读口返回 ApiResponse<BackofficeAccountProfileResponse> 信封（{code,message,data}），
    // 须按信封反序列化并取 .data()（与 payment/identity 列表端点同款，#63 wire 契约测试钉死）。
    private static final TypeReference<ApiResponse<AiplatformAccountProfileWireResponse>> ACCOUNT_PROFILE_TYPEREF =
            new TypeReference<>() {};

    // 订单清单返回 ApiResponse<PageResponse<BackofficeOrderSummaryResponse>> 信封（data 内 {items,total,page,size}，
    // page 1-based）——须按信封反序列化并取 .data()（payment PAYMENT_PAGE_TYPEREF 同款隐患口径）。
    private static final TypeReference<ApiResponse<PageResponse<AiplatformOrderSummaryWireResponse>>> ORDER_PAGE_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<AiplatformOrderDetailWireResponse>> ORDER_DETAIL_TYPEREF =
            new TypeReference<>() {};

    // 订单写路径（#29 交易环②报价改价 + #157 运营取消 + #158 重试归档）三操作共用回执：
    // ApiResponse<OrderResponse>（provider 用户面同构 DTO——五字段价目行，区别于后台详情七字段行）
    private static final TypeReference<ApiResponse<AiplatformOrderWireResponse>> ORDER_RECEIPT_TYPEREF =
            new TypeReference<>() {};

    // 项目域（#159/#162）同为 ApiResponse<T> 信封；对话史/版本列表的 data 是裸 List（非分页载体）
    private static final TypeReference<ApiResponse<PageResponse<AiplatformProjectSummaryWireResponse>>> PROJECT_PAGE_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<AiplatformProjectDetailWireResponse>> PROJECT_DETAIL_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<List<AiplatformConversationEntryWireResponse>>> CONVERSATION_LIST_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<AiplatformPrdWireResponse>> PROJECT_PRD_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<List<AiplatformVersionWireResponse>>> VERSION_LIST_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<AiplatformVersionDetailWireResponse>> VERSION_DETAIL_TYPEREF =
            new TypeReference<>() {};

    // 沙箱域（#173 观测 + #174 动作）同为 ApiResponse<T> 信封；四干预动作无请求体、回执＝详情 DTO
    private static final TypeReference<ApiResponse<PageResponse<AiplatformWorkspaceSummaryWireResponse>>> WORKSPACE_PAGE_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<AiplatformWorkspaceDetailWireResponse>> WORKSPACE_DETAIL_TYPEREF =
            new TypeReference<>() {};

    // 成本域（#161/#164 观测）同为 ApiResponse<T> 信封；四端点均为读口（时间窗 + 可选分页）
    private static final TypeReference<ApiResponse<AiplatformCostOverviewWireResponse>> COST_OVERVIEW_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<AiplatformUnpricedUsageWireResponse>> UNPRICED_USAGE_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<PageResponse<AiplatformProjectCostWireResponse>>> PROJECT_COST_PAGE_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<AiplatformProjectCostDetailWireResponse>> PROJECT_COST_DETAIL_TYPEREF =
            new TypeReference<>() {};

    // 单价表域（#160 成本运营＋#165 写口唯一化）同为 ApiResponse<T> 信封；清单 data 为 PageResponse
    // （page 1-based、total JSON string），改价回执为 {closed, opened} 双行——provider 的「开行」端点
    // 不建北向（种子脚本通道，spec #62），本客户端也不加对应方法
    private static final TypeReference<ApiResponse<PageResponse<AiplatformUnitPriceEntryWireResponse>>> PRICE_ENTRY_PAGE_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<AiplatformUnitPriceEntryWireResponse>> PRICE_ENTRY_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<AiplatformUnitPriceEntryRepriceWireResponse>> PRICE_ENTRY_REPRICE_TYPEREF =
            new TypeReference<>() {};

    // 知识素材域（#166 知识库管理）同为 ApiResponse<T> 信封；清单 data 为 PageResponse（page 1-based），
    // 详情含素材全文，三治理动作（停用/启用/删除）回执共形＝summary 单行（删除回执＝删除前终态）
    private static final TypeReference<ApiResponse<PageResponse<AiplatformMaterialSummaryWireResponse>>> MATERIAL_PAGE_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<AiplatformMaterialDetailWireResponse>> MATERIAL_DETAIL_TYPEREF =
            new TypeReference<>() {};

    private static final TypeReference<ApiResponse<AiplatformMaterialSummaryWireResponse>> MATERIAL_SUMMARY_TYPEREF =
            new TypeReference<>() {};

    // 出站时间参数定长格式（秒恒在场）：LocalDateTime.toString() 会省略零秒（"T00:00"），
    // provider 的 @DateTimeFormat(iso=DATE_TIME) 可解析两种形式，但 wire 形状取确定性的全秒形
    private static final DateTimeFormatter ISO_SECONDS = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");

    private final OpenApiClient openApiClient;
    private final String baseUrl;

    public AiplatformClient(OpenApiClient openApiClient,
                            @Value("${admin.aiplatform.base-url}") String baseUrl) {
        this.openApiClient = openApiClient;
        this.baseUrl = baseUrl;
    }

    /**
     * 查询账号极简档案（透传 aiplatform）——按 externalId 查 id/externalId/displayName/createdAt 四字段。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/accounts/{externalId}}（#154 已冻结）：返回
     * {@code ApiResponse<BackofficeAccountProfileResponse>}；externalId 未命中时 HTTP 404 +
     * 数字业务码 6004（IDN_004）——原样经 {@link AiplatformUpstreamException} 透传北向。</p>
     *
     * @param externalId 外部身份标识（OIDC sub＝identity 账户 Id）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（404 IDN_004 等，不做映射）
     */
    public AiplatformAccountProfileWireResponse getAccountProfile(String externalId) {
        String url = baseUrl + "/api/backoffice/accounts/" + encode(externalId);
        log.debug("AiplatformClient.getAccountProfile: {}", url);
        try {
            ApiResponse<AiplatformAccountProfileWireResponse> resp = openApiClient.get(url, ACCOUNT_PROFILE_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 分页查询订单清单（透传 aiplatform，四维检索）。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/orders}（#156 四维已冻结）：返回
     * {@code ApiResponse<PageResponse<BackofficeOrderSummaryResponse>>}。四维可组合、均可缺省；
     * externalId 换算不到/非数值 orderId 时 provider 如实返回空清单 200（无命非错误）；
     * 过滤参数绑定失败 400 ORD_010（数字业务码 5010）原样透传。</p>
     *
     * <p>与 payment 的分页差异（spec #62，平台分页统一决议目标态）：aiplatform wire 本就
     * <strong>1-based</strong>，page 直传 <strong>零换算</strong>（payment 是 page-1 还原 0-based）；
     * provider 侧 {@code BackofficePages} clamp（page≥1、size∈[1,100]）行为透传，本客户端不重复夹取。
     * {@code status} 多选拼为逗号分隔单值（{@code status=1,5}）——签名协议按参数名去重，
     * 禁用重复参数展开。</p>
     *
     * @param filter wire 层过滤参数（由应用层从 {@code AiplatformOrderQuery} 映射而来）
     * @param page   页码，<strong>1-based</strong>（北向原样直传）
     * @param size   每页大小
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（400 ORD_010 等，不做映射）
     */
    public PageResponse<AiplatformOrderSummaryWireResponse> listOrders(AiplatformOrderListWireRequest filter,
                                                                       int page, int size) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/api/backoffice/orders?page=").append(page)
                .append("&size=").append(size);
        if (filter.status() != null && !filter.status().isEmpty()) {
            // 逗号分隔单值（provider 签名按参数名去重，status=1&status=2 只有末值入签——禁用）
            appendParam(url, "status",
                    filter.status().stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
        appendParam(url, "createdFrom", filter.createdFrom());
        appendParam(url, "createdTo", filter.createdTo());
        appendParam(url, "externalId", filter.externalId());
        appendParam(url, "orderId", filter.orderId());
        log.debug("AiplatformClient.listOrders: {}", url);
        try {
            ApiResponse<PageResponse<AiplatformOrderSummaryWireResponse>> resp =
                    openApiClient.get(url.toString(), ORDER_PAGE_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 查询订单详情（透传 aiplatform）——报价依据全量：PRD 快照、价目历史（append-only 带操作者）、
     * 状态时点组。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/orders/{id}}（#155 已冻结）：返回
     * {@code ApiResponse<BackofficeOrderDetailResponse>}；订单不存在时 HTTP 404 + 数字业务码
     * 5001（ORD_001）原样透传。</p>
     *
     * @param id 订单标识（TSID 十进制字符串，provider 侧 lenient 解析）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（404 ORD_001 等，不做映射）
     */
    public AiplatformOrderDetailWireResponse getOrder(String id) {
        String url = baseUrl + "/api/backoffice/orders/" + encode(id);
        log.debug("AiplatformClient.getOrder: {}", url);
        try {
            ApiResponse<AiplatformOrderDetailWireResponse> resp = openApiClient.get(url, ORDER_DETAIL_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 下载订单源码包（透传 aiplatform）——tar.gz 二进制流，经项目工作区实时打包。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/orders/{id}/source-package}：<strong>无
     * ApiResponse 信封</strong>（{@code application/gzip} + Content-Disposition 文件名），经
     * cartisan-openapi {@link OpenApiClient#download}（cartisan-boot#30）取回原始字节 + 响应头——
     * ofByteArray 保全 gzip 字节（ofString 会经 UTF-8 解码不可逆损坏）。内存中转、不落盘。</p>
     *
     * <p>订单不存在 404 ORD_001（5001）、打包失败 500 WSP_002——错误信封在 body 里，
     * {@code download} 内部抛 {@link OpenApiClientException}，此处同款翻译透传。</p>
     *
     * @param id 订单标识（TSID 十进制字符串）
     * @return 二进制响应载体（HTTP 状态 + 响应头 raw 值 + 原始字节）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（404 ORD_001 / 500 WSP_002 等，不做映射）
     */
    public BinaryResponse downloadSourcePackage(String id) {
        String url = baseUrl + "/api/backoffice/orders/" + encode(id) + "/source-package";
        log.debug("AiplatformClient.downloadSourcePackage: {}", url);
        try {
            return openApiClient.download(url);
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 提交报价/改价（透传 aiplatform）——「已报价态重复提交＝改价」语义由 provider 承担
     * （待报价态首次提交＝报价→已报价；已报价态重复提交＝改价，状态不变、append-only 价目行
     * 留痕、订单现值取最新行），BFF 不解释不预判。
     *
     * <p>对接 aiplatform {@code POST /api/backoffice/orders/{id}/quote}（#29 交易环②）：请求体
     * {@code {amount, note}} 逐字镜像（amount Long 分 → JSON string，全局 Long→ToStringSerializer
     * 出口口径——provider 默认 string→Long 强转可回读；note 可空在场），返回
     * {@code ApiResponse<OrderResponse>}（用户面同构回执，含 append-only 改价历史）。操作者身份
     * 经框架 {@code OpenApiClient} 自动带 {@code X-User-Id/X-User-Name} 头（RequestContext→
     * provider 落痕价目行，缺头落空）。订单不存在 404 ORD_001（5001）；已支付/已终结
     * 409 ORD_007（5007）；金额非正 400 ORD_008（5008）；备注超长 400 ORD_009（5009）
     * ——均原样透传。</p>
     *
     * @param id      订单标识（TSID 十进制字符串，provider 侧 lenient 解析：非数值同 404 ORD_001）
     * @param command 报价命令体（由应用层从北向命令映射而来，字段合法性归 provider 聚合守卫）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（ORD_001/007/008/009 等，不做映射）
     */
    public AiplatformOrderWireResponse quoteOrder(String id, AiplatformOrderQuoteWireRequest command) {
        String url = baseUrl + "/api/backoffice/orders/" + encode(id) + "/quote";
        log.debug("AiplatformClient.quoteOrder: {}", url);
        try {
            ApiResponse<AiplatformOrderWireResponse> resp = openApiClient.post(url, command, ORDER_RECEIPT_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 运营取消订单（透传 aiplatform）——限未支付态，语义与用户取消完全一致：订单落已取消、
     * 项目解冻回迭代、用户可继续对话与再次下单。取消原因必填（运营内部口径留档，不呈现
     * 任何用户面读面）。
     *
     * <p>对接 aiplatform {@code POST /api/backoffice/orders/{id}/cancel}（#157）：请求体
     * {@code {reason}} 逐字镜像，返回 {@code ApiResponse<OrderResponse>}（已取消终态回执，
     * cancelledAt 落定）。操作者身份经框架 {@code OpenApiClient} 自动带
     * {@code X-User-Id/X-User-Name} 头（RequestContext→provider 落痕订单行，缺头落空）。
     * 订单不存在 404 ORD_001（5001）；已支付/已归档/已取消 409 ORD_005（5005，退款/售后
     * 另议）；原因缺失 400 ORD_013（5013）；原因超长（至多 1000 字）400 ORD_014（5014）
     * ——均原样透传。</p>
     *
     * @param id      订单标识（TSID 十进制字符串，provider 侧 lenient 解析）
     * @param command 取消命令体（由应用层从北向命令映射而来，字段合法性归 provider 聚合守卫）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（ORD_001/005/013/014 等，不做映射）
     */
    public AiplatformOrderWireResponse cancelOrder(String id, AiplatformOrderCancelWireRequest command) {
        String url = baseUrl + "/api/backoffice/orders/" + encode(id) + "/cancel";
        log.debug("AiplatformClient.cancelOrder: {}", url);
        try {
            ApiResponse<AiplatformOrderWireResponse> resp = openApiClient.post(url, command, ORDER_RECEIPT_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 重试归档（透传 aiplatform）——已支付未归档卡单的手动补完结：一事务内订单落已归档＋
     * 项目归档，成功后补发「已归档」通知并触发知识沉淀（成交 PRD 入知识库，best-effort
     * 不炸主流程）。幂等由既有守卫保证。
     *
     * <p>对接 aiplatform {@code POST /api/backoffice/orders/{id}/retry-archive}（#158）：
     * <strong>无请求体</strong>（框架 {@code OpenApiClient.post} 对 null body 发空 body，
     * provider 端只读路径参数——同沙箱四动作/单价表停用先例），返回
     * {@code ApiResponse<OrderResponse>}（已归档终态回执，paidAt/archivedAt 双时点）。
     * 操作者身份经框架 {@code OpenApiClient} 自动带 {@code X-User-Id/X-User-Name} 头
     * （RequestContext→provider 落痕订单行，缺头落空；支付链自动归档操作者为空）。订单
     * 不存在 404 ORD_001（5001）；重复触发/非已支付态 409 ORD_012（5012）；项目已归档
     * 409 <strong>PRJ_013</strong>（4013，跨 BC 既有码透传——不产生重复素材）——均原样
     * 透传。</p>
     *
     * @param id 订单标识（TSID 十进制字符串，provider 侧 lenient 解析）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（ORD_001/012、PRJ_013 等，不做映射）
     */
    public AiplatformOrderWireResponse retryArchiveOrder(String id) {
        String url = baseUrl + "/api/backoffice/orders/" + encode(id) + "/retry-archive";
        log.debug("AiplatformClient.retryArchiveOrder: {}", url);
        try {
            ApiResponse<AiplatformOrderWireResponse> resp = openApiClient.post(url, null, ORDER_RECEIPT_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 分页查询项目清单（透传 aiplatform，四维检索——项目域口径）。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/projects}（#159 已冻结）：返回
     * {@code ApiResponse<PageResponse<BackofficeProjectSummaryResponse>>}。与订单清单的差异：
     * {@code status} 为<strong>三档单选</strong>（1=进行中 3=已归档；缺省＝全部、归档项目缺省含），
     * 单值直传无逗号拼接；精确维度是 {@code projectId}。externalId 换算不到/非数值 projectId 时
     * provider 如实返回空清单 200（无命非错误）；过滤参数绑定失败 400 PRJ_014（数字业务码 4014）
     * 原样透传。归档项目照读、已删项目不可见（provider 真删无墓碑——语义透传，本客户端不二次过滤）。</p>
     *
     * <p>page 1-based 直传零换算（同订单），provider clamp（page≥1、size∈[1,100]）行为透传。</p>
     *
     * @param filter wire 层过滤参数（由应用层从 {@code AiplatformProjectQuery} 映射而来）
     * @param page   页码，<strong>1-based</strong>（北向原样直传）
     * @param size   每页大小
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（400 PRJ_014 等，不做映射）
     */
    public PageResponse<AiplatformProjectSummaryWireResponse> listProjects(AiplatformProjectListWireRequest filter,
                                                                           int page, int size) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/api/backoffice/projects?page=").append(page)
                .append("&size=").append(size);
        // status 单选（1=进行中 3=已归档）：单值直传——与订单域多选拼逗号有意不同
        appendParam(url, "status", filter.status());
        appendParam(url, "createdFrom", filter.createdFrom());
        appendParam(url, "createdTo", filter.createdTo());
        appendParam(url, "externalId", filter.externalId());
        appendParam(url, "projectId", filter.projectId());
        log.debug("AiplatformClient.listProjects: {}", url);
        try {
            ApiResponse<PageResponse<AiplatformProjectSummaryWireResponse>> resp =
                    openApiClient.get(url.toString(), PROJECT_PAGE_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 查询项目详情（透传 aiplatform）——清单字段全量 + 订单引用（activeOrder/latestOrder）
     * + 成本指针（costSummary：按币种 BigDecimal + unpriced 标记）。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/projects/{id}}（#159 + #164 成本指针）：
     * 返回 {@code ApiResponse<BackofficeProjectDetailResponse>}；项目不存在（含已删——真删无墓碑）
     * 时 HTTP 404 + 数字业务码 4001（PRJ_001）原样透传。归档项目照读。</p>
     *
     * @param id 项目标识（TSID 十进制字符串，provider 侧 lenient 解析：非数值同 404 PRJ_001）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（404 PRJ_001 等，不做映射）
     */
    public AiplatformProjectDetailWireResponse getProject(String id) {
        String url = baseUrl + "/api/backoffice/projects/" + encode(id);
        log.debug("AiplatformClient.getProject: {}", url);
        try {
            ApiResponse<AiplatformProjectDetailWireResponse> resp = openApiClient.get(url, PROJECT_DETAIL_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 读项目对话史（透传 aiplatform）——对话面全量同序（用户发言/智能体回复/问答卡/问答作答/
     * 收尾卡/平台轻引导，id 升序＝对话序），过程明细不在其中。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/projects/{id}/conversation}（#162 深读三组）：
     * 返回 {@code ApiResponse<List<ConversationEntryResponse>>}（data 为裸 List，非分页载体）。
     * {@code kind} 为 Integer code + {@code kindName} 中文名随行（aiplatform#186 已落，provider
     * 出口提供——BFF 透传 provider 值）；{@code question}/{@code closing} 载荷 JSON 原样透传。
     * 项目不存在 404 PRJ_001（4001）原样透传。</p>
     *
     * @param id 项目标识（TSID 十进制字符串，provider 侧 lenient 解析）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（404 PRJ_001 等，不做映射）
     */
    public List<AiplatformConversationEntryWireResponse> getConversation(String id) {
        String url = baseUrl + "/api/backoffice/projects/" + encode(id) + "/conversation";
        log.debug("AiplatformClient.getConversation: {}", url);
        try {
            ApiResponse<List<AiplatformConversationEntryWireResponse>> resp =
                    openApiClient.get(url, CONVERSATION_LIST_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 读项目 PRD（透传 aiplatform）——直读项目 dev 工作区 {@code docs/PRD.md}（事实源，
     * v1 无版本链只最新版），markdown 正文 + updatedAt（文件 mtime，秒精度 Instant）。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/projects/{id}/prd}（#162 深读三组，口径照
     * 用户面 #41）：返回 {@code ApiResponse<PrdResponse>}。未产出（工作区无该文件）HTTP 404 +
     * 数字业务码 4015（PRJ_015，与项目不存在 PRJ_001 区分）；环境故障 500 WSP_002（1002）——
     * 均原样透传。</p>
     *
     * @param id 项目标识（TSID 十进制字符串，provider 侧 lenient 解析）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（404 PRJ_001/PRJ_015、500 WSP_002 等，不做映射）
     */
    public AiplatformPrdWireResponse getPrd(String id) {
        String url = baseUrl + "/api/backoffice/projects/" + encode(id) + "/prd";
        log.debug("AiplatformClient.getPrd: {}", url);
        try {
            ApiResponse<AiplatformPrdWireResponse> resp = openApiClient.get(url, PROJECT_PRD_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 读项目版本列表（透传 aiplatform）——正本＝容器内 git log（无库表），每轮编码 run 收口
     * 自动成版；排序新→旧定死；零版本（尚无收口）＝空列表非错误。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/projects/{id}/versions}（#162 深读三组，
     * 口径照用户面 #91/#93）：返回 {@code ApiResponse<List<VersionResponse>>}（data 为裸 List）。
     * 项目不存在 404 PRJ_001（4001）；环境故障 500 WSP_002（1002）——均原样透传。</p>
     *
     * @param id 项目标识（TSID 十进制字符串，provider 侧 lenient 解析）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（404 PRJ_001、500 WSP_002 等，不做映射）
     */
    public List<AiplatformVersionWireResponse> listVersions(String id) {
        String url = baseUrl + "/api/backoffice/projects/" + encode(id) + "/versions";
        log.debug("AiplatformClient.listVersions: {}", url);
        try {
            ApiResponse<List<AiplatformVersionWireResponse>> resp = openApiClient.get(url, VERSION_LIST_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 读项目版本详情（透传 aiplatform）——版本元数据 + 锚定收尾卡载荷（Run-Id 联接对话史
     * closing 条目，#88 同载荷；收尾卡缺位时 closing 为 null）。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/projects/{id}/versions/{ref}}（#162 深读三组）：
     * 返回 {@code ApiResponse<VersionDetailResponse>}。ref＝commit hash（hex 40 位）——非 hash
     * 形态 HTTP 404 + 数字业务码 4028（PRJ_028，provider 侧裁决且不触工作区，shell 注入防线）；
     * 项目不存在 404 PRJ_001（4001）；环境故障 500 WSP_002（1002）——均原样透传。</p>
     *
     * @param id  项目标识（TSID 十进制字符串，provider 侧 lenient 解析）
     * @param ref 版本 ref（commit hash，hex 40 位；形态裁决归 provider）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（404 PRJ_001/PRJ_028、500 WSP_002 等，不做映射）
     */
    public AiplatformVersionDetailWireResponse getVersion(String id, String ref) {
        String url = baseUrl + "/api/backoffice/projects/" + encode(id) + "/versions/" + encode(ref);
        log.debug("AiplatformClient.getVersion: {}", url);
        try {
            ApiResponse<AiplatformVersionDetailWireResponse> resp = openApiClient.get(url, VERSION_DETAIL_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 分页查询沙箱清单（透传 aiplatform，期望态/实态两维过滤——漂移发现口径）。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/workspaces}（#173 观测面已冻结）：返回
     * {@code ApiResponse<PageResponse<BackofficeWorkspaceSummaryResponse>>}。两维均<strong>单选</strong>
     * Integer code、可组合、可缺省（null＝该维不过滤）：{@code desired} 期望态（1=运行 2=休眠 3=封存，
     * DB 意图侧）、{@code actual} 容器实态（1=运行中 2=已停止 3=无容器 4=未知——docker 现场探查、
     * 探查后内存过滤，total 如实＝筛后计数；{@code actual=3} 即捞「期望运行而实态已亡」漂移清单）。
     * 过滤参数绑定失败 400 WSP_014（数字业务码 1014）原样透传。</p>
     *
     * <p>page 1-based 直传零换算（同订单/项目），provider clamp（page≥1、size∈[1,100]）行为透传。
     * 实态与卷大小逐行现场探查（docker 子进程）——页越大越慢，provider 侧语义、本客户端不干预。</p>
     *
     * @param filter wire 层过滤参数（由应用层从 {@code AiplatformWorkspaceQuery} 映射而来）
     * @param page   页码，<strong>1-based</strong>（北向原样直传）
     * @param size   每页大小
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（400 WSP_014 等，不做映射）
     */
    public PageResponse<AiplatformWorkspaceSummaryWireResponse> listWorkspaces(
            AiplatformWorkspaceListWireRequest filter, int page, int size) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/api/backoffice/workspaces?page=").append(page)
                .append("&size=").append(size);
        appendParam(url, "desired", filter.desired());
        appendParam(url, "actual", filter.actual());
        log.debug("AiplatformClient.listWorkspaces: {}", url);
        try {
            ApiResponse<PageResponse<AiplatformWorkspaceSummaryWireResponse>> resp =
                    openApiClient.get(url.toString(), WORKSPACE_PAGE_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 查询沙箱详情（透传 aiplatform）——清单行超集：全量字段＋所属项目引用＋中间件资源清单
     * （连接串原文，容器内回环形态）＋置备失败原因/封存包寻址键/审计列。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/workspaces/{id}}（#173 观测面）：返回
     * {@code ApiResponse<BackofficeWorkspaceDetailResponse>}；工作区不存在（含畸形 id——provider 侧
     * lenient 解析）时 HTTP 404 + 数字业务码 1001（WSP_001）原样透传。</p>
     *
     * @param id 工作区标识（TSID 十进制字符串，provider 侧 lenient 解析：非数值同 404 WSP_001）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（404 WSP_001 等，不做映射）
     */
    public AiplatformWorkspaceDetailWireResponse getWorkspace(String id) {
        String url = baseUrl + "/api/backoffice/workspaces/" + encode(id);
        log.debug("AiplatformClient.getWorkspace: {}", url);
        try {
            ApiResponse<AiplatformWorkspaceDetailWireResponse> resp = openApiClient.get(url, WORKSPACE_DETAIL_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 唤醒沙箱（透传 aiplatform）——收敛到 READY＋（已生成项目）应用在服，同步等结果；容器缺失/
     * 被杀走幂等重建（卷保留）；封存态走深度唤醒（解包回卷＋依赖重装，分钟级）。
     *
     * <p>对接 aiplatform {@code POST /api/backoffice/workspaces/{id}/wake}（#174 动作面）：<strong>无
     * 请求体</strong>，返回 {@code ApiResponse<BackofficeWorkspaceDetailResponse>}（动作后的观测详情）。
     * 操作者身份经框架 {@code OpenApiClient} 自动带 {@code X-User-Id/X-User-Name} 头（RequestContext
     * →provider 落痕动作行，缺头落空）。工作区不存在 404 WSP_001（1001）、非 DEV 400 WSP_007（1007）、
     * 封存包不可读 404 WSP_016（1016）、置备等待超时 500 WSP_011（1011）——均原样透传。</p>
     *
     * @param id 工作区标识（TSID 十进制字符串）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（404 WSP_001 等，不做映射）
     */
    public AiplatformWorkspaceDetailWireResponse wakeWorkspace(String id) {
        return postWorkspaceAction(id, "wake");
    }

    /**
     * 强制休眠沙箱（透传 aiplatform）——管理员的即时止损口：删容器保卷、期望态置休眠（不等闲置
     * 阈值）；已休眠＝幂等成功（补删残留容器）。
     *
     * <p>对接 aiplatform {@code POST /api/backoffice/workspaces/{id}/hibernate}（#174）：无请求体，
     * 回执＝动作后的观测详情。封存态拒 400 WSP_009（1009，卷已删先唤醒）、run 在途拒 409
     * WSP_015（1015）、收敛任务在途 409 WSP_017（1017）——均原样透传。操作者透传头落痕同唤醒。</p>
     *
     * @param id 工作区标识（TSID 十进制字符串）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（400 WSP_009、409 WSP_015 等，不做映射）
     */
    public AiplatformWorkspaceDetailWireResponse hibernateWorkspace(String id) {
        return postWorkspaceAction(id, "hibernate");
    }

    /**
     * 强制重建沙箱（透传 aiplatform）——「预览死了」型漂移的标准化处置（替代手工 docker 拉）：
     * 在跑但坏了的容器也杀（卷保留、数据不动），走唤醒内核同一重建路径收敛回 READY。
     *
     * <p>对接 aiplatform {@code POST /api/backoffice/workspaces/{id}/rebuild}（#174）：无请求体，
     * 回执＝动作后的观测详情。封存态拒 400 WSP_009（1009，空卷重建＝掩埋数据丢失——先唤醒）、
     * run 在途拒 409 WSP_015（1015）、收敛任务在途 409 WSP_017（1017）、重建重试上限落 FAILED
     * 时 500 WSP_010（1010，可再触发）——均原样透传。操作者透传头落痕同唤醒。</p>
     *
     * @param id 工作区标识（TSID 十进制字符串）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（400 WSP_009、500 WSP_010 等，不做映射）
     */
    public AiplatformWorkspaceDetailWireResponse rebuildWorkspace(String id) {
        return postWorkspaceAction(id, "rebuild");
    }

    /**
     * 封存沙箱（透传 aiplatform）——动作序同闲置满期的自动封存：删容器→整卷打包（仅排可重建缓存）
     * 落平台存储→期望态置封存＋包元数据→删卷（失败由扫描下轮收敛）；RUNNING 起点可用（即时深回收）。
     *
     * <p>对接 aiplatform {@code POST /api/backoffice/workspaces/{id}/seal}（#174）：无请求体，回执＝
     * 动作后的观测详情（含封存包元数据）。重复封存拒 400 WSP_009（1009，走「唤醒→休眠→封存」周期）、
     * run 在途拒 409 WSP_015（1015）、收敛任务在途 409 WSP_017（1017）——均原样透传。
     * 操作者透传头落痕同唤醒。</p>
     *
     * @param id 工作区标识（TSID 十进制字符串）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（400 WSP_009、409 WSP_015 等，不做映射）
     */
    public AiplatformWorkspaceDetailWireResponse sealWorkspace(String id) {
        return postWorkspaceAction(id, "seal");
    }

    /**
     * 四干预动作共用的出站形状（#174）：{@code POST /api/backoffice/workspaces/{id}/{action}}——
     * 无请求体（框架 {@code OpenApiClient.post} 对 null body 发空 body），返回
     * {@code ApiResponse<BackofficeWorkspaceDetailResponse>} 信封取 {@code .data()}；操作者身份由
     * 框架经 {@code RequestContext}→{@code X-User-Id/X-User-Name} 自动透传（identity 同款）。
     */
    private AiplatformWorkspaceDetailWireResponse postWorkspaceAction(String id, String action) {
        String url = baseUrl + "/api/backoffice/workspaces/" + encode(id) + "/" + action;
        log.debug("AiplatformClient.workspaceAction[{}]: {}", action, url);
        try {
            // 无请求体：框架 OpenApiClient.post 对 null body 发空 body（POST 仍带 application/json），
            // provider 端只读路径参数（同 payment 主动查行先例）
            ApiResponse<AiplatformWorkspaceDetailWireResponse> resp =
                    openApiClient.post(url, null, WORKSPACE_DETAIL_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 平台成本全局总览（透传 aiplatform）——全平台跨项目观测模型开销构成：五档总量 + 平台成本
     * （token × 事件时点生效单价，币种分桶直读不折算）+ 分模型 + 分智能体（agentKind 裸维度串
     * + agentKindName 随行——aiplatform#186 已落）。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/costs/overview}（#161 成本运营）：返回
     * {@code ApiResponse<BackofficeCostOverviewResponse>}。空窗/无数据返回全零 total 与空分桶
     * （不是错误）；查询参数绑定失败 400 METER_011（数字业务码 3011）原样透传。</p>
     *
     * <p>时间窗 {@code [from, to)} 半开、ISO-8601 Instant（UTC 带 Z）；provider 侧可缺省，北向
     * <strong>必填</strong>（issue #67：不设默认窗口）——出站恒带双参。Instant 出站取
     * {@code Instant.toString()}（ISO_INSTANT 确定形，秒恒在场、UTC 带 Z），provider 的
     * {@code @RequestParam Instant} 同形解析。</p>
     *
     * @param window wire 层时间窗（由应用层从 {@code AiplatformCostQuery} 映射而来）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（400 METER_011 等，不做映射）
     */
    public AiplatformCostOverviewWireResponse getCostOverview(AiplatformCostWindowWireRequest window) {
        StringBuilder url = new StringBuilder(baseUrl).append("/api/backoffice/costs/overview");
        appendWindow(url, window);
        log.debug("AiplatformClient.getCostOverview: {}", url);
        try {
            ApiResponse<AiplatformCostOverviewWireResponse> resp =
                    openApiClient.get(url.toString(), COST_OVERVIEW_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * unpriced 全局警示（透传 aiplatform，用量驱动）——窗口内有 token 用量且事件时点无生效单价
     * 的 (provider, model, 档位) 按档位汇总 token（只计无价分量）。静态配价缺口不做（无用量＝
     * 无实际损失）；据此发现漏配价并及时补价（历史成本不漂移）。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/costs/unpriced}（#161）：返回
     * {@code ApiResponse<BackofficeUnpricedUsageResponse>}；空窗/无未配价用量返回空 items
     * （不是错误）；绑定失败 400 METER_011（3011）原样透传。</p>
     *
     * @param window wire 层时间窗
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（400 METER_011 等，不做映射）
     */
    public AiplatformUnpricedUsageWireResponse getUnpricedUsage(AiplatformCostWindowWireRequest window) {
        StringBuilder url = new StringBuilder(baseUrl).append("/api/backoffice/costs/unpriced");
        appendWindow(url, window);
        log.debug("AiplatformClient.getUnpricedUsage: {}", url);
        try {
            ApiResponse<AiplatformUnpricedUsageWireResponse> resp =
                    openApiClient.get(url.toString(), UNPRICED_USAGE_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 分页查询项目成本清单（透传 aiplatform，成本降序）——窗口内有 token 用量的各项目成本汇总；
     * 排序服务端定死成本降序（全未配价项目排后且 allUnpriced=true、同序按 projectId 升序稳定）；
     * 已删项目的历史花费照列（成本观测不抹历史）。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/costs/projects}（#164）：返回
     * {@code ApiResponse<PageResponse<BackofficeProjectCostResponse>>}（page 1-based、total JSON
     * string——cartisan-web 全局 Long→ToStringSerializer）。page 1-based 直传零换算（同订单/
     * 项目/沙箱），provider clamp（page≥1、size∈[1,100] 默认 20）行为透传、本客户端不重复夹取。</p>
     *
     * @param window wire 层时间窗
     * @param page   页码，<strong>1-based</strong>（北向原样直传）
     * @param size   每页大小
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（400 METER_011 等，不做映射）
     */
    public PageResponse<AiplatformProjectCostWireResponse> listProjectCosts(
            AiplatformCostWindowWireRequest window, int page, int size) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/api/backoffice/costs/projects?page=").append(page)
                .append("&size=").append(size);
        appendWindow(url, window);
        log.debug("AiplatformClient.listProjectCosts: {}", url);
        try {
            ApiResponse<PageResponse<AiplatformProjectCostWireResponse>> resp =
                    openApiClient.get(url.toString(), PROJECT_COST_PAGE_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 单项目成本下钻（透传 aiplatform）——byModel/byAgentKind 分解 + 未配价档位（与 cost 互补
     * 不重叠，bySubject 口径无 token 计数）。{@code projectId} 为计量 subject 原值（写侧口径
     * projectId 十进制串，provider 不解释存在性）：无用量/查无此号返回全零 total 与空结构
     * （明确空态，非错误、不 404）。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/costs/projects/{projectId}}（#164）：返回
     * {@code ApiResponse<BackofficeProjectCostDetailResponse>}；绑定失败 400 METER_011（3011）
     * 原样透传。</p>
     *
     * @param projectId 项目标识（计量 subject 原值，provider 不解释存在性）
     * @param window    wire 层时间窗
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（400 METER_011 等，不做映射）
     */
    public AiplatformProjectCostDetailWireResponse getProjectCostDetail(
            String projectId, AiplatformCostWindowWireRequest window) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/api/backoffice/costs/projects/").append(encode(projectId));
        appendWindow(url, window);
        log.debug("AiplatformClient.getProjectCostDetail: {}", url);
        try {
            ApiResponse<AiplatformProjectCostDetailWireResponse> resp =
                    openApiClient.get(url.toString(), PROJECT_COST_DETAIL_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 分页查询单价行清单（透传 aiplatform，含历史行——价史全貌）。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/price-entries}（#160 已冻结）：返回
     * {@code ApiResponse<PageResponse<UnitPriceEntryResponse>>}。provider/model 均为匹配键成分＝
     * 精确等值过滤、均可缺省（缺省＝全量行）；排序服务端定死＝生效起点倒序（新段在前，同起点
     * id 倒序稳定）；effectiveTo 为 null 即当前行。行带操作者两列（最近管理动作留痕；存量行/
     * 种子脚本种入行落 null）。绑定裁决两段：非整数 page/size 在<strong>北向绑定层</strong>即 404
     * （框架类型不匹配口径，到不了本方法）；provider 侧绑定失败 400 METER_009（数字业务码 3009）
     * 仅在 provider 契约演进（如过滤维度加类型化标量）时可能出现——错误信封忠实透传。</p>
     *
     * <p>page 1-based 直传零换算（同订单/项目/沙箱/成本），provider clamp（page≥1、size∈[1,100]
     * 默认 20）行为透传、本客户端不重复夹取。</p>
     *
     * @param filter wire 层过滤参数（由应用层从 {@code AiplatformPriceEntryQuery} 映射而来）
     * @param page   页码，<strong>1-based</strong>（北向原样直传）
     * @param size   每页大小
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（400 METER_009 等，不做映射）
     */
    public PageResponse<AiplatformUnitPriceEntryWireResponse> listPriceEntries(
            AiplatformPriceEntryListWireRequest filter, int page, int size) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/api/backoffice/price-entries?page=").append(page)
                .append("&size=").append(size);
        appendParam(url, "provider", filter.provider());
        appendParam(url, "model", filter.model());
        log.debug("AiplatformClient.listPriceEntries: {}", url);
        try {
            ApiResponse<PageResponse<AiplatformUnitPriceEntryWireResponse>> resp =
                    openApiClient.get(url.toString(), PRICE_ENTRY_PAGE_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 原子改价（透传 aiplatform）——单调用关当前行＋开新行（同事务，中途任一守卫失败两行都不动）。
     *
     * <p>对接 aiplatform {@code POST /api/backoffice/price-entries/{id}/reprice}（#160）：请求体
     * {@code {unitPrice, currency, effectiveFrom}} 逐字镜像（unitPrice BigDecimal→JSON 明文小数；
     * effectiveFrom 可空＝缺省即时、含未来时点＝预发布——预发布与重叠校验语义由 provider 承担，
     * BFF 不代填时点不预判），返回 {@code ApiResponse<UnitPriceEntryRepriceResponse>}——
     * {@code {closed, opened}} 双行回执（被关行落 effectiveTo＝新起点且保留其原开行留痕，新行
     * 沿用匹配键、带改价操作者）。操作者身份经框架 {@code OpenApiClient} 自动带
     * {@code X-User-Id/X-User-Name} 头（RequestContext→provider 落痕新行，缺头落空）。</p>
     *
     * <p>行不存在（含畸形 id——provider 侧 lenient 解析）404 METER_006（3006）；字段不完整/
     * 单价负数 400 METER_004（3004）；币种非 ISO 4217 400 METER_010（3010）；起点早于被关行起点
     * 400 METER_005（3005）；目标非当前行 409 METER_007（3007）；区间重叠（跨区间或同起点）
     * 409 METER_008（3008）——均原样透传。</p>
     *
     * @param id      单价行标识（TSID 十进制字符串）
     * @param command 改价命令体（由应用层从北向命令映射而来，字段合法性归 provider 聚合守卫）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（METER_004/005/006/007/008/010 等，不做映射）
     */
    public AiplatformUnitPriceEntryRepriceWireResponse repricePriceEntry(
            String id, AiplatformPriceEntryRepriceWireRequest command) {
        String url = baseUrl + "/api/backoffice/price-entries/" + encode(id) + "/reprice";
        log.debug("AiplatformClient.repricePriceEntry: {}", url);
        try {
            ApiResponse<AiplatformUnitPriceEntryRepriceWireResponse> resp =
                    openApiClient.post(url, command, PRICE_ENTRY_REPRICE_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 停用单价行（透传 aiplatform）——即时生效：关当前行（effectiveTo＝现在）不接新行，此后该匹配键
     * 用量进 unpriced（缺价不伪装 0、不阻断聚合）。对未生效的预发布行停用＝钳到自身起点成空区间
     * （从未生效）。
     *
     * <p>对接 aiplatform {@code POST /api/backoffice/price-entries/{id}/deactivate}（#160）：<strong>无
     * 请求体</strong>（框架 {@code OpenApiClient.post} 对 null body 发空 body，provider 端只读路径参数——
     * 同沙箱四动作先例），返回 {@code ApiResponse<UnitPriceEntryResponse>}（被关行单行回执）。操作者
     * 透传头自动落痕被关行（停用不接新行，被关行是唯一落点；缺头落空）。行不存在（含畸形 id）
     * 404 METER_006（3006）；目标非当前行 409 METER_007（3007）——均原样透传。</p>
     *
     * @param id 单价行标识（TSID 十进制字符串）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（404 METER_006、409 METER_007 等，不做映射）
     */
    public AiplatformUnitPriceEntryWireResponse deactivatePriceEntry(String id) {
        String url = baseUrl + "/api/backoffice/price-entries/" + encode(id) + "/deactivate";
        log.debug("AiplatformClient.deactivatePriceEntry: {}", url);
        try {
            ApiResponse<AiplatformUnitPriceEntryWireResponse> resp =
                    openApiClient.post(url, null, PRICE_ENTRY_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 分页查询知识素材清单（透传 aiplatform，三维过滤——治理工作清单）。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/materials}（#166 知识库管理）：返回
     * {@code ApiResponse<PageResponse<BackofficeMaterialSummaryResponse>>}。三维度可组合、均可缺省
     * （缺省＝全量）：{@code status} 状态<strong>单选</strong> Integer code（1=启用 2=停用——与订单
     * 多选有意不同）；{@code sunkFrom}/{@code sunkTo} 沉淀时间闭区间（首沉淀时间，ISO-8601
     * Instant UTC 带 Z——{@code appendParam} 默认分支取 {@code Instant.toString()} 确定形）；
     * {@code projectId} 来源项目 id 精确（查无＝空清单 200）。排序服务端定死＝沉淀时间倒序
     * （新沉淀在前，id 倒序稳定）。绑定裁决两段：非整数 status/非 Instant 时间在<strong>北向
     * 绑定层</strong>即 404（框架类型不匹配口径，到不了本方法）；provider 侧绑定失败
     * 400 KNW_007（数字业务码 2007，含数值但未知的状态 code）原样透传。</p>
     *
     * <p>page 1-based 直传零换算（同订单/项目/沙箱/成本/单价表），provider clamp
     * （page≥1、size∈[1,100] 默认 20）行为透传、本客户端不重复夹取。</p>
     *
     * @param filter wire 层过滤参数（由应用层从 {@code AiplatformMaterialQuery} 映射而来）
     * @param page   页码，<strong>1-based</strong>（北向原样直传）
     * @param size   每页大小
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（400 KNW_007 等，不做映射）
     */
    public PageResponse<AiplatformMaterialSummaryWireResponse> listMaterials(
            AiplatformMaterialListWireRequest filter, int page, int size) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/api/backoffice/materials?page=").append(page)
                .append("&size=").append(size);
        // status 单选（1=启用 2=停用）：单值直传（项目/沙箱同款），无订单域多选拼逗号
        appendParam(url, "status", filter.status());
        appendParam(url, "sunkFrom", filter.sunkFrom());
        appendParam(url, "sunkTo", filter.sunkTo());
        appendParam(url, "projectId", filter.projectId());
        log.debug("AiplatformClient.listMaterials: {}", url);
        try {
            ApiResponse<PageResponse<AiplatformMaterialSummaryWireResponse>> resp =
                    openApiClient.get(url.toString(), MATERIAL_PAGE_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 查询知识素材详情（透传 aiplatform）——元数据（与清单行同形）＋素材全文：{@code content}
     * ＝块按 seq 以空行拼接（段落级重组，内容无损）。来源项目引用容缺直读登记面。
     *
     * <p>对接 aiplatform {@code GET /api/backoffice/materials/{id}}（#166）：返回
     * {@code ApiResponse<BackofficeMaterialDetailResponse>}；素材不存在（含畸形 id——provider
     * 侧 lenient 解析）时 HTTP 404 + 数字业务码 2005（KNW_005）原样透传。</p>
     *
     * @param id 素材标识（TSID 十进制字符串，provider 侧 lenient 解析：非数值同 404 KNW_005）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（404 KNW_005 等，不做映射）
     */
    public AiplatformMaterialDetailWireResponse getMaterial(String id) {
        String url = baseUrl + "/api/backoffice/materials/" + encode(id);
        log.debug("AiplatformClient.getMaterial: {}", url);
        try {
            ApiResponse<AiplatformMaterialDetailWireResponse> resp =
                    openApiClient.get(url, MATERIAL_DETAIL_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 停用素材（透传 aiplatform）——可逆开关：素材全部块退出生成命中（重沉淀不复活），误伤可经
     * enable 恢复；重复停用幂等（操作者留最近一次）。
     *
     * <p>对接 aiplatform {@code POST /api/backoffice/materials/{id}/disable}（#166）：<strong>无
     * 请求体</strong>（框架 {@code OpenApiClient.post} 对 null body 发空 body，provider 端只读
     * 路径参数——同沙箱四动作/单价表停用先例），返回 {@code ApiResponse<BackofficeMaterialSummaryResponse>}
     * （provider 重读登记行的最新状态与操作者）。操作者身份经框架 {@code OpenApiClient} 自动带
     * {@code X-User-Id/X-User-Name} 头（RequestContext→provider 落痕素材级）——<strong>缺头
     * 400 KNW_006</strong>（数字业务码 2006，知识治理动作必留痕，与单价表缺头落空有意不同——
     * 知识治理无种子脚本无头通道）。素材不存在（含畸形 id）404 KNW_005（2005）——均原样透传。</p>
     *
     * @param id 素材标识（TSID 十进制字符串）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（400 KNW_006、404 KNW_005 等，不做映射）
     */
    public AiplatformMaterialSummaryWireResponse disableMaterial(String id) {
        return postMaterialAction(id, "disable");
    }

    /**
     * 启用素材（透传 aiplatform）——停用的可逆侧：素材全部块恢复参与生成命中；重复启用幂等。
     *
     * <p>对接 aiplatform {@code POST /api/backoffice/materials/{id}/enable}（#166）：无请求体，
     * 回执＝summary（同停用口径）。操作者透传头落痕同停用（缺头 400 KNW_006＝2006）；
     * 素材不存在（含畸形 id）404 KNW_005（2005）——均原样透传。</p>
     *
     * @param id 素材标识（TSID 十进制字符串）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（400 KNW_006、404 KNW_005 等，不做映射）
     */
    public AiplatformMaterialSummaryWireResponse enableMaterial(String id) {
        return postMaterialAction(id, "enable");
    }

    /**
     * 删除素材（透传 aiplatform）——治理移除、不可逆：登记行与全部块同事务移除、不动来源项目
     * （管理删除与项目删除级联正交）；无行可留、不留痕。回执＝<strong>删除前终态</strong>
     * summary（provider 契约如此，逐字镜像——确认移除了什么）。
     *
     * <p>对接 aiplatform {@code DELETE /api/backoffice/materials/{id}}（#166）：HTTP <strong>DELETE</strong>
     * 动词——经框架 {@link OpenApiClient#delete}（cartisan-boot#33：空 body digest + query 入签
     * + RequestContext 透传，与 {@code get()} 同形），返回 {@code ApiResponse<BackofficeMaterialSummaryResponse>}。
     * 素材不存在（含畸形 id、重复删除）404 KNW_005（2005）原样透传。删除不落操作者
     * （无行可留），操作者透传头在发（无操作者参数可落）。</p>
     *
     * @param id 素材标识（TSID 十进制字符串）
     * @throws AiplatformUpstreamException aiplatform 错误信封透传（404 KNW_005 等，不做映射）
     */
    public AiplatformMaterialSummaryWireResponse deleteMaterial(String id) {
        String url = baseUrl + "/api/backoffice/materials/" + encode(id);
        log.debug("AiplatformClient.deleteMaterial: {}", url);
        try {
            ApiResponse<AiplatformMaterialSummaryWireResponse> resp =
                    openApiClient.delete(url, MATERIAL_SUMMARY_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 停用/启用两治理动作共用的出站形状（#166）：{@code POST /api/backoffice/materials/{id}/{action}}
     * ——无请求体（框架 {@code OpenApiClient.post} 对 null body 发空 body，provider 端只读路径参数），
     * 返回 {@code ApiResponse<BackofficeMaterialSummaryResponse>} 信封取 {@code .data()}（provider
     * 重读登记行的最新状态与操作者）；操作者身份由框架经 {@code RequestContext}→
     * {@code X-User-Id/X-User-Name} 自动透传（缺头会被 provider 域面守卫 KNW_006 拦截——
     * 治理动作必留痕）。
     */
    private AiplatformMaterialSummaryWireResponse postMaterialAction(String id, String action) {
        String url = baseUrl + "/api/backoffice/materials/" + encode(id) + "/" + action;
        log.debug("AiplatformClient.materialAction[{}]: {}", action, url);
        try {
            ApiResponse<AiplatformMaterialSummaryWireResponse> resp =
                    openApiClient.post(url, null, MATERIAL_SUMMARY_TYPEREF);
            return resp.data();
        } catch (OpenApiClientException e) {
            throw AiplatformUpstreamException.from(e);
        }
    }

    /**
     * 成本域四读口共用的时间窗拼接：{@code from}/{@code to} 半开 [from, to)。纯委托
     * {@link #appendParam}——Instant 取其默认分支 {@code Instant.toString()}（ISO_INSTANT：
     * 秒恒在场、UTC 带 Z），null 统一省略；总览/unpriced/单项目下钻无既有查询参数（首参前缀
     * {@code ?}）、项目成本清单在 page/size 之后（前缀 {@code &}），分隔符由 appendParam 裁决。
     */
    private static void appendWindow(StringBuilder url, AiplatformCostWindowWireRequest window) {
        appendParam(url, "from", window.from());
        appendParam(url, "to", window.to());
    }

    private static void appendParam(StringBuilder url, String name, Object value) {
        if (value != null) {
            // 首个查询参数前缀 ?、后续前缀 &（各域清单端点 page/size 恒在先，成本域时间窗可在首）
            url.append(url.indexOf("?") < 0 ? '?' : '&');
            if (value instanceof LocalDateTime time) {
                url.append(name).append('=').append(encode(time.format(ISO_SECONDS)));
            } else {
                url.append(name).append('=').append(encode(value.toString()));
            }
        }
    }

    private static String encode(String value) {
        // 与 PaymentClient / AccountClient 一致：简单 URL 编码，避免特殊字符问题（OIDC sub 可含 | 等）；
        // 编码后值与 provider 签名侧（request.getQueryString() raw 值）同形入签
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
