package com.aieducenter.admin.aiplatform.infrastructure;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import com.aieducenter.admin.aiplatform.application.AiplatformUpstreamException;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformAccountProfileWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformOrderSummaryWireResponse;
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

    private static void appendParam(StringBuilder url, String name, Object value) {
        if (value != null) {
            if (value instanceof LocalDateTime time) {
                url.append('&').append(name).append('=').append(encode(time.format(ISO_SECONDS)));
            } else {
                url.append('&').append(name).append('=').append(encode(value.toString()));
            }
        }
    }

    private static String encode(String value) {
        // 与 PaymentClient / AccountClient 一致：简单 URL 编码，避免特殊字符问题（OIDC sub 可含 | 等）；
        // 编码后值与 provider 签名侧（request.getQueryString() raw 值）同形入签
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
