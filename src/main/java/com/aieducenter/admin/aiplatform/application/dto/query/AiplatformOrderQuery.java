package com.aieducenter.admin.aiplatform.application.dto.query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 平台订单列表查询参数（BFF 透传 aiplatform）——北向四维过滤，字段名与 aiplatform
 * {@code GET /api/backoffice/orders} 查询参数逐字镜像（spec #62 定稿）。
 *
 * <p>与 payment 的差异：多选参数名就是 {@code status}（单数——provider 契约名），北向收
 * 逗号分隔单值（{@code status=1,5}），非 payment 式 {@code statuses}。</p>
 *
 * <p>枚举筛选项以 aiplatform {@code OrderStatus}（BaseEnum）的 <strong>Integer code</strong>
 * 透传。绑定裁决分两段：非整数取值（{@code status=abc}、非法时间串）在 <strong>admin 绑定层</strong>
 * 即 400（框架默认信封，不到 provider）；<strong>合法整数但未知 code</strong>（如 99）透传 provider，
 * 由其绑定层以 400 ORD_010（数字业务码 5010）裁决、错误信封忠实透传。分页参数
 * {@code page}/{@code size} 不在此（controller 独立绑定，缺省 1/20 镜像 provider）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformOrderQuery(

        /** 状态多选（1=待报价 2=已报价 3=已支付 4=已归档 5=已取消）；null/空 = 不限 */
        List<Integer> status,

        /** 创建时间下界（含，ISO-8601） */
        LocalDateTime createdFrom,

        /** 创建时间上界（含，ISO-8601） */
        LocalDateTime createdTo,

        /** 下单账号 externalId（对外正身，provider 换算；换算不到＝空清单 200） */
        String externalId,

        /** 订单号精确（TSID 十进制；查无/非数值＝空清单 200） */
        String orderId
) {
}
