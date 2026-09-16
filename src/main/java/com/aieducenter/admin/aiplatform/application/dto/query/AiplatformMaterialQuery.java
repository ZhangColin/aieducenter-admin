package com.aieducenter.admin.aiplatform.application.dto.query;

import java.time.Instant;

/**
 * AI 平台知识素材列表查询参数（BFF 透传 aiplatform）——北向三维过滤，字段名与 aiplatform
 * {@code GET /api/backoffice/materials} 查询参数逐字镜像（spec #62 定稿、issue #69）。
 *
 * <p>治理工作清单口径：三维度可组合、均可缺省（缺省＝全量）——① {@code status} 状态
 * <strong>单选</strong>（1=启用 2=停用；与订单清单状态多选有意不同，照项目面状态单选先例）；
 * ② {@code sunkFrom}/{@code sunkTo} 沉淀时间区间（<strong>首沉淀时间</strong>，闭区间含两端，
 * ISO-8601 Instant UTC 带 Z）；③ {@code projectId} 来源项目 id 精确（登记面字符串，查无＝空清单
 * 200）。不做内容模糊与账号维度（provider 面即无）。</p>
 *
 * <p>枚举筛选项以 aiplatform {@code MaterialStatus}（BaseEnum）的 <strong>Integer code</strong>
 * 透传。绑定裁决分两段（同订单/项目/沙箱/成本口径）：非整数 code / 非 Instant 时间串在
 * <strong>admin 绑定层</strong>即被 {@code handleTypeMismatch} 渲染为 404（框架类型不匹配口径，
 * 不到 provider）；<strong>合法整数但未知 code</strong>（如 7）透传 provider，由其绑定层以
 * 400 KNW_007（数字业务码 2007）裁决、错误信封忠实透传。分页参数 {@code page}/{@code size}
 * 不在此（controller 独立绑定，缺省 1/20 镜像 provider）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformMaterialQuery(

        /** 状态单选（1=启用 2=停用）；null = 全部 */
        Integer status,

        /** 沉淀时间下界（含；首沉淀时间，ISO-8601 Instant UTC 带 Z） */
        Instant sunkFrom,

        /** 沉淀时间上界（含；同上） */
        Instant sunkTo,

        /** 来源项目 id 精确（TSID 十进制串；查无＝空清单 200） */
        String projectId
) {
}
