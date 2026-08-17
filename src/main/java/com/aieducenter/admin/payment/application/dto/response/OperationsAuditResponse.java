package com.aieducenter.admin.payment.application.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 审核统计响应——由 {@link com.aieducenter.admin.payment.application.dto.wire.OperationsAuditWireResponse}
 * 映射而来，逐字镜像 payment 的 {@code OperationsAuditResponse} 形状（ADR-0011 / issue #60）：
 * 审核笔数·通过/拒绝数·通过率·平均审核时长（分钟，BigDecimal）+ 按审核人聚合。
 *
 * <p>聚合归 payment（spec「仪表盘」）；admin 透传不改序。比率/均值为 {@code BigDecimal}——provider 契约。</p>
 *
 * @since 0.1.0
 */
public record OperationsAuditResponse(

        Long totalAudits,

        Long approvedCount,

        Long rejectedCount,

        BigDecimal approvalRate,

        BigDecimal avgAuditDurationMinutes,

        List<AuditorBreakdown> byAuditor
) {

    /** 审核人维度统计——单一审核人的审核笔数·通过/拒绝数·通过率（无人均时长）。 */
    public record AuditorBreakdown(

            Long auditorId,

            String auditorName,

            Long count,

            Long approvedCount,

            Long rejectedCount,

            BigDecimal approvalRate
    ) {
    }
}
