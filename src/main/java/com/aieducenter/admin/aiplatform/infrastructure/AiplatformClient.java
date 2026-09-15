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
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderSummaryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformPrdWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectCostDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectCostWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformProjectSummaryWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformVersionDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformVersionWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformUnpricedUsageWireResponse;
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
 * 六域（订单/项目/沙箱/成本/单价表/知识素材/账号）共用本客户端——同一 downstream、同一签名身份，
 * 各域只加方法。</p>
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
