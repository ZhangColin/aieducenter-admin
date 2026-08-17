package com.aieducenter.admin.payment.application.dto.wire;

import java.time.LocalDateTime;

/**
 * payment {@code GET /api/v1/orders/{orderNo}/lifecycle} 的 wire 镜像——订单生命周期统一事件的<strong>单项</strong>。
 *
 * <p>payment 实返 {@code ApiResponse<List<OrderLifecycleResponse>>}：{@code data} 为<strong>扁平事件数组</strong>
 * （orderNo 是路径参数，响应无包装——issue #57 / ADR-0011 逐字镜像）。事件为跨来源的语义 9 字段抽象，
 * {@code PaymentLog}（网关交互，{@code source=GATEWAY}）与 {@code OperationLog}（行为者操作，
 * {@code source=OPERATION}）在 payment 侧按 {@code createdAt} 合并排序后逐条投出（payment ADR-0002：
 * 不合表、合视图）；admin 透传此<strong>已合并</strong>的时间线，不本地再合并。</p>
 *
 * <p>逐字镜像 payment 的 {@code OrderLifecycleResponse}（9 字段），无增删改（BFF 忠实透传，ADR-0011）。
 * {@code id} 为 {@code Long}——payment 框架 {@code ToStringSerializer} 使其上 wire 为 string 形态
 * （{@code "1001"}），Jackson 反序列化回 {@code Long}。</p>
 *
 * @param id              源记录主键（PaymentLog.id / OperationLog.id；同时间排序的稳定键）
 * @param source          来源标签：{@code "GATEWAY"}（网关交互）/ {@code "OPERATION"}（行为者操作）
 * @param createdAt       事件时间（合并后的主排序键，payment 已排好序）
 * @param action          动作稳定 token：GATEWAY→{@code logType}（PAYMENT_REQUEST…）；
 *                        OPERATION→{@code operation} 枚举名（AUDIT_APPROVE…）
 * @param actionName      动作显示名：GATEWAY→{@code logType} 原值；OPERATION→中文名（审核通过…）
 * @param outcome         结果 token（SUCCESS/FAILED）：GATEWAY 由 success 派生；OPERATION 取 result
 * @param performer       执行方：GATEWAY→{@code bankInterface}；OPERATION→{@code operatorName}
 * @param performerSystem 执行方系统：GATEWAY→{@code bankCode}；OPERATION→{@code operatorSystem}
 * @param detail          补充说明：GATEWAY→success 时 returnMsg / 失败时 errorMessage；OPERATION→remark
 * @since 0.1.0
 */
public record OrderLifecycleWireResponse(

        Long id,

        String source,

        LocalDateTime createdAt,

        String action,

        String actionName,

        String outcome,

        String performer,

        String performerSystem,

        String detail
) {
}
