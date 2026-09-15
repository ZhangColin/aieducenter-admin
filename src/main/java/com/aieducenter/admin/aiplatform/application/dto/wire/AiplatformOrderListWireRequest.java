package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.time.LocalDateTime;
import java.util.List;

/**
 * aiplatform {@code GET /api/backoffice/orders} 列表查询的 wire 请求——BFF 出站四维过滤的载荷形状。
 *
 * <p>与北向 {@link com.aieducenter.admin.aiplatform.application.dto.query.AiplatformOrderQuery}
 * 字段同构，由 {@code AiplatformOrderAppService} 映射。独立成 wire 类型，是为了让 infrastructure
 * {@link com.aieducenter.admin.aiplatform.infrastructure.AiplatformClient} 只依赖 wire 层
 * （query DTO 是应用层内部概念，不应被基础设施 import——payment 同款约定）。</p>
 *
 * <p>与 payment 的差异（spec #62）：{@code status} 多选经 {@code AiplatformClient} 拼为
 * <strong>逗号分隔单值</strong>（{@code status=1,5}）——aiplatform 签名协议按 query 参数名去重，
 * 同名重复参数（{@code status=1&status=2}）只有末值入签，禁用 payment 式重复参数展开。</p>
 *
 * @since 0.1.0
 */
public record AiplatformOrderListWireRequest(

        /** 状态多选（Integer code）；null/空 = 不限 */
        List<Integer> status,

        /** 创建时间下界（含，ISO-8601） */
        LocalDateTime createdFrom,

        /** 创建时间上界（含，ISO-8601） */
        LocalDateTime createdTo,

        /** 下单账号（对外正身，服务端换算；换算不到＝空清单 200） */
        String externalId,

        /** 订单号精确（TSID 十进制；查无/非数值＝空清单 200） */
        String orderId
) {
}
