package com.aieducenter.admin.payment.application.dto.wire;

import java.math.BigDecimal;
import java.util.List;

/**
 * payment {@code GET /api/v1/stats/operations/audit} 的 wire 镜像——审核统计仪表盘。
 *
 * <p>审核笔数·通过率·平均审核时长（顶层）+ 按审核人聚合（{@link AuditorStatWireResponse}），
 * 数据源 OperationLog（issue #37 一档统计）。admin 作为 BFF 纯透传——不做 admin 侧聚合/重算
 * （spec「仪表盘」）。最终字段以 payment 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record OperationsAuditWireResponse(

        /** 审核总笔数 */
        Long auditCount,

        /** 审核通过率（小数，0–1 区间；最终精度以 payment 契约为准） */
        BigDecimal approvalRate,

        /** 平均审核时长（秒；退款创建到审核完成的平均间隔，最终单位以 payment 契约为准） */
        Long avgAuditDurationSeconds,

        /** 按审核人聚合的明细（payment 已排好序，admin 透传不改序） */
        List<AuditorStatWireResponse> auditors
) {

    /**
     * 审核人统计——单一审核人的审核笔数·通过率·平均时长。
     *
     * @param auditorId              审核人 ID（OperationLog.operatorId）
     * @param auditorName            审核人姓名（OperationLog.operatorName）
     * @param auditCount             该审核人审核笔数
     * @param approvedCount          该审核人通过笔数
     * @param approvalRate           该审核人通过率（小数，0–1 区间）
     * @param avgAuditDurationSeconds 该审核人平均审核时长（秒）
     */
    public record AuditorStatWireResponse(

            Long auditorId,

            String auditorName,

            Long auditCount,

            Long approvedCount,

            BigDecimal approvalRate,

            Long avgAuditDurationSeconds
    ) {
    }
}
