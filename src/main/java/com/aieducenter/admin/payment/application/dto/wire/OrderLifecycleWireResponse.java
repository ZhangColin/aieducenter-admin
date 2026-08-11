package com.aieducenter.admin.payment.application.dto.wire;

import java.time.LocalDateTime;
import java.util.List;

/**
 * payment {@code GET /api/v1/orders/{orderNo}/lifecycle} 的 wire 镜像——订单生命周期读模型。
 *
 * <p>payment 侧按 {@code orderNo} 把 {@code PaymentLog}（网关交互）与 {@code OperationLog}
 * （行为者操作）两表记录 <strong>union 后按时间排序</strong>返回（payment ADR-0002：不合表、合视图；
 * 合并在 payment 的 {@code OrderLifecycleAppService} 完成）。admin 作为 BFF 透传此<strong>已合并</strong>
 * 的时间线，不本地再合并——避免与 payment 双逻辑不一致。</p>
 *
 * <p>每个事件以 {@link LifecycleEventWireResponse#source} 区分来源；当 source=PAYMENT_LOG 时网关字段有效、
 * source=OPERATION_LOG 时操作字段有效，另一组为 null（union 类型的平表投影）。最终字段以 payment
 * 实现契约为准（issue #37）。</p>
 *
 * @since 0.1.0
 */
public record OrderLifecycleWireResponse(

        String orderNo,

        List<LifecycleEventWireResponse> events
) {

    /**
     * 生命周期事件——PaymentLog 或 OperationLog 经 payment 合并后的单条时间线条目。
     *
     * <p>平表投影：网关字段组（source=PAYMENT_LOG 时有效）与操作字段组（source=OPERATION_LOG 时有效）
     * 共存于同一记录，非生效组为 null。</p>
     *
     * @param source          事件来源（PAYMENT_LOG / OPERATION_LOG）
     * @param createdAt       发生时间（合并排序键，由 payment 排好序）
     * @param logType         网关交互类型（PAYMENT_REQUEST / PAYMENT_QUERY / PAYMENT_CANCEL /
     *                        REFUND_REQUEST / REFUND_QUERY / PAYMENT_CALLBACK）——source=PAYMENT_LOG 时有效
     * @param paymentOrderNo  关联支付单号——source=PAYMENT_LOG 时有效
     * @param refundOrderNo   关联退款单号——source=PAYMENT_LOG 时有效
     * @param bankInterface   银行接口——source=PAYMENT_LOG 时有效
     * @param returnCode      返回码——source=PAYMENT_LOG 时有效
     * @param returnMsg       返回消息——source=PAYMENT_LOG 时有效
     * @param executionTime   执行耗时（毫秒）——source=PAYMENT_LOG 时有效
     * @param success         是否成功——source=PAYMENT_LOG 时有效
     * @param targetType      操作目标类型（PAYMENT / REFUND）——source=OPERATION_LOG 时有效
     * @param targetNo        操作目标单号——source=OPERATION_LOG 时有效
     * @param operation       操作（AUDIT_APPROVE / AUDIT_REJECT / NOTIFY_RESEND / …）——source=OPERATION_LOG 时有效
     * @param operatorId      操作人 ID——source=OPERATION_LOG 时有效
     * @param operatorName    操作人姓名——source=OPERATION_LOG 时有效
     * @param operatorSystem  操作来源系统——source=OPERATION_LOG 时有效
     * @param result          操作结果——source=OPERATION_LOG 时有效
     * @param remark          备注——source=OPERATION_LOG 时有效
     */
    public record LifecycleEventWireResponse(

            String source,

            LocalDateTime createdAt,

            // ===== PaymentLog 字段（source=PAYMENT_LOG 时有效） =====
            String logType,

            String paymentOrderNo,

            String refundOrderNo,

            String bankInterface,

            String returnCode,

            String returnMsg,

            Long executionTime,

            Boolean success,

            // ===== OperationLog 字段（source=OPERATION_LOG 时有效） =====
            String targetType,

            String targetNo,

            String operation,

            Long operatorId,

            String operatorName,

            String operatorSystem,

            String result,

            String remark
    ) {
    }
}
