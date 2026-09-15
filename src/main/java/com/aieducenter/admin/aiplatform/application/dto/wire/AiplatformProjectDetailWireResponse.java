package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * aiplatform 项目详情的 wire 镜像——与 aiplatform {@code BackofficeProjectDetailResponse}
 * （#159 项目域 + #164 成本指针）字段同构：清单字段全量＋归属账号显示名＋订单引用＋成本指针。
 *
 * <p>订单引用（照用户面先例，与订单域互链）：{@code activeOrder}＝未终结订单摘要（有值即冻结
 * 迭代）；{@code latestOrder}＝最近一张任意状态订单（支付归档后承接「完整记录」取单面）；从未
 * 下单两者皆 null。</p>
 *
 * <p>成本指针：{@code costSummary}＝总成本按币种分桶直读不折算（键 = ISO 4217 币种码、
 * 值 BigDecimal）＋{@code unpriced} 有无标记（true＝成本不完整）；无用量项目＝空 cost +
 * false（明确空态）。明细下钻走成本域端点。</p>
 *
 * @since 0.1.0
 */
public record AiplatformProjectDetailWireResponse(

        /** 项目标识（TSID 十进制字符串） */
        String id,

        /** 项目名 */
        String name,

        /** 归属账号显示名（跨 BC 软引用取名；无主/缺档为 null） */
        String ownerDisplayName,

        /** dev 工作区标识（排障时工作区互查的锚点） */
        String workspaceId,

        /** 项目类型（1=官网 2=电商） */
        Integer type,

        /** 项目类型名（provider 出口提供） */
        String typeName,

        /** 派生项目状态（1=进行中 3=已归档，归档优先） */
        Integer status,

        /** 派生状态名（provider 出口提供） */
        String statusName,

        /** 是否已归档（单向终点） */
        Boolean archived,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间（审计列） */
        LocalDateTime updatedAt,

        /** PRD 产出时点（null = 未产出） */
        LocalDateTime prdProducedAt,

        /** 首次生成时点（null = 从未生成） */
        LocalDateTime generatedAt,

        /** 未终结订单摘要（无 = null；跨 BC 软引用） */
        AiplatformOrderBriefWireResponse activeOrder,

        /** 最近一张订单摘要（任意状态；从未下单 = null） */
        AiplatformOrderBriefWireResponse latestOrder,

        /** 成本汇总指针（项目全量口径，明细下钻走成本域端点） */
        CostSummary costSummary
) {

    /**
     * 成本汇总指针（#164）：总成本按币种（token × 事件时点生效单价，分桶直读不折算、
     * 键 = ISO 4217 币种码）＋unpriced 有无标记（有无量但时点无生效价的分量——
     * true 时成本不完整）。无用量项目＝空 cost + false（明确空态）。
     */
    public record CostSummary(
            Map<String, BigDecimal> cost,
            boolean unpriced
    ) {
    }
}
