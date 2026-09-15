package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.math.BigDecimal;
import java.util.Map;

/**
 * aiplatform 项目成本清单行的 wire 镜像——与 aiplatform {@code BackofficeProjectCostResponse}
 * （#164 成本运营）字段同构：窗口内该项目的总量 + 平台成本（币种分桶）+ 全未配价标注。
 *
 * <p>用量驱动：窗口内无用量的项目不出现在清单；{@code allUnpriced} = 有用量但无任何已配价分量
 * ——清单排后标注（成本标量缺失不伪装 0），明细走单项目下钻端点。{@code projectId} = 计量
 * subject 原值（写侧口径 projectId 十进制串，provider 不解释存在性；已删项目的历史花费照列，
 * 项目名等档案信息归 admin 侧按 id 自行互查）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformProjectCostWireResponse(

        /** 项目标识（计量 subject 原值回显，TSID 十进制串） */
        String projectId,

        /** 总量（五档分列） */
        AiplatformTokenUsageWireResponse total,

        /** 平台成本（币种分桶；全未配价时为空 Map） */
        Map<String, BigDecimal> cost,

        /** 全未配价标注（有用量但无任何已配价分量——true 时成本不完整） */
        boolean allUnpriced
) {
}
