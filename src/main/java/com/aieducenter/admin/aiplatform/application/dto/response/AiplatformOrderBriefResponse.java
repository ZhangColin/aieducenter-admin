package com.aieducenter.admin.aiplatform.application.dto.response;

/**
 * AI 平台订单摘要——北向出口，逐字镜像 aiplatform {@code OrderBriefResponse}（项目详情嵌入的
 * 订单引用，#159 与订单域互链），无增删字段、无换型（spec #62 忠实透传）。
 *
 * @since 0.1.0
 */
public record AiplatformOrderBriefResponse(

        /** 订单标识（TSID 十进制字符串） */
        String id,

        /** 订单状态（1=待报价 2=已报价＝待支付）；已支付/终态不会作为未终结订单出现 */
        Integer status,

        /** 状态名（provider 出口提供） */
        String statusName
) {
}
