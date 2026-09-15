package com.aieducenter.admin.aiplatform.application.dto.query;

import java.time.LocalDateTime;

/**
 * AI 平台项目列表查询参数（BFF 透传 aiplatform）——北向四维过滤，字段名与 aiplatform
 * {@code GET /api/backoffice/projects} 查询参数逐字镜像（spec #62 定稿、issue #65）。
 *
 * <p>与订单清单的关键差异：{@code status} 是<strong>三档单选</strong>（1=进行中 3=已归档；
 * 缺省＝全部、归档项目缺省含——照用户面状态过滤先例），非订单式逗号分隔多选；
 * 精确维度是 {@code projectId}（非订单 {@code orderId}）。</p>
 *
 * <p>枚举筛选项以 aiplatform {@code ProjectStatusFilter}（BaseEnum）的 <strong>Integer code</strong>
 * 透传。绑定裁决分两段（同订单口径）：非整数取值（{@code status=abc}、非法时间串）在
 * <strong>admin 绑定层</strong>即 400（框架默认信封，不到 provider）；<strong>合法整数但未知
 * code</strong>（如 2——码位已注销不复用、或 99）透传 provider，由其绑定层以 400 PRJ_014
 * （数字业务码 4014）裁决、错误信封忠实透传。分页参数 {@code page}/{@code size} 不在此
 * （controller 独立绑定，缺省 1/20 镜像 provider）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformProjectQuery(

        /** 状态三档单选（1=进行中 3=已归档）；null = 全部（归档项目缺省含） */
        Integer status,

        /** 创建时间下界（含，ISO-8601） */
        LocalDateTime createdFrom,

        /** 创建时间上界（含，ISO-8601） */
        LocalDateTime createdTo,

        /** 归属账号 externalId（对外正身，provider 换算；换算不到＝空清单 200） */
        String externalId,

        /** 项目 id 精确（TSID 十进制；查无/非数值＝空清单 200） */
        String projectId
) {
}
