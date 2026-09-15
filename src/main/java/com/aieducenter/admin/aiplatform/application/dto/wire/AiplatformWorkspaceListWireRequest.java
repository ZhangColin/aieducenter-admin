package com.aieducenter.admin.aiplatform.application.dto.wire;

/**
 * aiplatform {@code GET /api/backoffice/workspaces} 列表查询的 wire 请求——BFF 出站期望态/实态
 * 两维过滤的载荷形状（issue #66 沙箱域）。
 *
 * <p>与北向 {@link com.aieducenter.admin.aiplatform.application.dto.query.AiplatformWorkspaceQuery}
 * 字段同构，由 {@code AiplatformWorkspaceAppService} 映射。独立成 wire 类型，是为了让 infrastructure
 * {@link com.aieducenter.admin.aiplatform.infrastructure.AiplatformClient} 只依赖 wire 层
 * （query DTO 是应用层内部概念，不应被基础设施 import——payment/订单/项目同款约定）。</p>
 *
 * <p>两维均为<strong>单选</strong> Integer code、可组合、可缺省（null＝该维不过滤）：{@code desired}
 * 期望态（1=运行 2=休眠 3=封存，DB 意图侧）；{@code actual} 容器实态（1=运行中 2=已停止 3=无容器
 * 4=未知，docker 现场探查后内存过滤——actual=3 即捞「期望运行而实态已亡」漂移清单）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformWorkspaceListWireRequest(

        /** 期望态单选（1=运行 2=休眠 3=封存）；null = 全量 */
        Integer desired,

        /** 容器实态单选（1=运行中 2=已停止 3=无容器 4=未知）；null = 全量 */
        Integer actual
) {
}
