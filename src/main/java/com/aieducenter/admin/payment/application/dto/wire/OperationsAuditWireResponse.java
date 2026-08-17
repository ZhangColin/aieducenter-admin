package com.aieducenter.admin.payment.application.dto.wire;

import java.math.BigDecimal;
import java.util.List;

/**
 * payment {@code GET /api/v1/stats/operations/audit} 的 wire 镜像——退款审核工作情况。
 *
 * <p>逐字镜像 payment 的 {@code OperationsAuditResponse}（ADR-0011 / issue #60）：顶层为
 * {@code totalAudits/approvedCount/rejectedCount/approvalRate/avgAuditDurationMinutes}（均值单位<strong>分钟</strong>、
 * {@code BigDecimal}）+ {@code byAuditor} 按审核人聚合。数据源拆分：笔数/通过率/按审核人 ← OperationLog；
 * 平均审核时长 ← RefundOrder（{@code audited_at - created_at}，{@code audit_type=MANUAL}）；
 * {@code byAuditor} 不含人均时长（payment 侧避免跨聚合归属歧义）。聚合归 payment（issue #37 一档统计）；
 * admin 纯透传——不做 admin 侧聚合/重算（spec「仪表盘」）。</p>
 *
 * @param totalAudits            审核笔数（AUDIT_APPROVE + AUDIT_REJECT）
 * @param approvedCount          通过数
 * @param rejectedCount          拒绝数
 * @param approvalRate           通过率 [0,1] 4 位小数（比率 BigDecimal——provider 契约）
 * @param avgAuditDurationMinutes 平均审核时长（分钟，2 位小数，BigDecimal——provider 契约）
 * @param byAuditor              按审核人聚合的明细（payment 已排好序，admin 透传不改序）
 * @since 0.1.0
 */
public record OperationsAuditWireResponse(

        Long totalAudits,

        Long approvedCount,

        Long rejectedCount,

        BigDecimal approvalRate,

        BigDecimal avgAuditDurationMinutes,

        List<AuditorBreakdownWireResponse> byAuditor
) {

    /**
     * 审核人维度统计——单一审核人的审核笔数·通过/拒绝数·通过率（无人均时长）。
     *
     * @param auditorId     审核人 ID（OperationLog.operatorId）
     * @param auditorName   审核人姓名（OperationLog.operatorName）
     * @param count         该审核人审核笔数
     * @param approvedCount 该审核人通过笔数
     * @param rejectedCount 该审核人拒绝笔数
     * @param approvalRate  该审核人通过率（小数，0–1 区间）
     */
    public record AuditorBreakdownWireResponse(

            Long auditorId,

            String auditorName,

            Long count,

            Long approvedCount,

            Long rejectedCount,

            BigDecimal approvalRate
    ) {
    }
}
