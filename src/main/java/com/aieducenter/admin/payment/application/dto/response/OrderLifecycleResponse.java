package com.aieducenter.admin.payment.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单生命周期响应——由 {@link com.aieducenter.admin.payment.application.dto.wire.OrderLifecycleWireResponse}
 * 映射而来，承载 payment 已合并（按时间排序）的时间线。
 *
 * <p>合并在 payment 完成（ADR-0002），admin 透传不改序、不本地合并。每个事件以 {@link LifecycleEvent#source}
 * 区分来源，前端按 source 渲染网关交互或行为者操作。展示文案（i18n）由前端按枚举名映射。</p>
 *
 * @since 0.1.0
 */
public record OrderLifecycleResponse(

        String orderNo,

        List<LifecycleEvent> events
) {

    /**
     * 生命周期事件——平表投影，网关字段组与操作字段组共存；非生效组字段为 null
     * （与全局 Jackson 默认一致——出 JSON {@code null}，契约稳定不省略，见 CONTEXT 记忆「全局 Jackson 含 null」）。
     */
    public record LifecycleEvent(

            String source,

            LocalDateTime createdAt,

            // ===== PaymentLog 字段（source=PAYMENT_LOG 时有效，否则 null） =====
            String logType,

            String paymentOrderNo,

            String refundOrderNo,

            String bankInterface,

            String returnCode,

            String returnMsg,

            Long executionTime,

            Boolean success,

            // ===== OperationLog 字段（source=OPERATION_LOG 时有效，否则 null） =====
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
