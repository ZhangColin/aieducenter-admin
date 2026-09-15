package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.time.LocalDateTime;

/**
 * aiplatform {@code GET /api/backoffice/projects} 列表查询的 wire 请求——BFF 出站四维过滤的载荷形状。
 *
 * <p>与北向 {@link com.aieducenter.admin.aiplatform.application.dto.query.AiplatformProjectQuery}
 * 字段同构，由 {@code AiplatformProjectAppService} 映射。独立成 wire 类型，是为了让 infrastructure
 * {@link com.aieducenter.admin.aiplatform.infrastructure.AiplatformClient} 只依赖 wire 层
 * （query DTO 是应用层内部概念，不应被基础设施 import——payment/订单同款约定）。</p>
 *
 * <p>{@code status} 为<strong>单选</strong> Integer code（1=进行中 3=已归档），单值直传——
 * 无订单域的逗号拼接（多选拼接只在多选维度存在）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformProjectListWireRequest(

        /** 状态三档单选（1=进行中 3=已归档）；null = 全部（归档项目缺省含） */
        Integer status,

        /** 创建时间下界（含，ISO-8601） */
        LocalDateTime createdFrom,

        /** 创建时间上界（含，ISO-8601） */
        LocalDateTime createdTo,

        /** 归属账号（对外正身，服务端换算；换算不到＝空清单 200） */
        String externalId,

        /** 项目 id 精确（TSID 十进制；查无/非数值＝空清单 200） */
        String projectId
) {
}
