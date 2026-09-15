package com.aieducenter.admin.aiplatform.application.dto.wire;

/**
 * aiplatform 未终结/最近订单摘要的 wire 镜像——项目详情嵌入的订单引用（{@code OrderBriefResponse}
 * 同构，#159 与订单域互链）：项目面知道「挂着一张未终结订单、在哪个态」即可，订单本体经订单域
 * 详情端点取。
 *
 * @since 0.1.0
 */
public record AiplatformOrderBriefWireResponse(

        /** 订单标识（TSID 十进制字符串） */
        String id,

        /** 订单状态（1=待报价 2=已报价＝待支付）；已支付/终态不会作为未终结订单出现 */
        Integer status,

        /** 状态名（provider 出口提供） */
        String statusName
) {
}
