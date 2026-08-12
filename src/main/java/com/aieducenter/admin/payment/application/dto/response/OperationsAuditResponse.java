package com.aieducenter.admin.payment.application.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 审核统计响应——由 {@link com.aieducenter.admin.payment.application.dto.wire.OperationsAuditWireResponse}
 * 映射而来，承载审核笔数·通过率·平均审核时长 + 按审核人聚合。
 *
 * <p>聚合归 payment（spec「仪表盘」）；admin 透传不改序。最终字段以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record OperationsAuditResponse(

        Long auditCount,

        BigDecimal approvalRate,

        Long avgAuditDurationSeconds,

        List<AuditorStat> auditors
) {

    /** 审核人统计——单一审核人的审核笔数·通过率·平均时长。 */
    public record AuditorStat(

            Long auditorId,

            String auditorName,

            Long auditCount,

            Long approvedCount,

            BigDecimal approvalRate,

            Long avgAuditDurationSeconds
    ) {
    }
}
