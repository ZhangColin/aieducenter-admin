package com.aieducenter.admin.payment.application.dto.wire;

import java.time.LocalDateTime;

/**
 * payment {@code PaymentOrder} 详情的 wire 镜像——完整聚合投影。
 *
 * <p>与列表项 {@link PaymentOrderWireResponse} 同源（payment {@code PaymentOrder} 聚合），
 * 但承载<strong>详情全貌</strong>投影：列表项为扫描精简、详情为完整聚合，二者独立演进
 * （payment 契约定型后，详情端可能新增列表不需要的字段）。字段以 payment 实现契约为准。</p>
 *
 * <p>枚举出口规则（ADR-0009）：每个枚举字段以 Integer code 承载、配 {@code *Name} 中文名
 * （{@code status}/{@code statusName}、{@code payMode}/{@code payModeName}、{@code accessType}/
 * {@code accessTypeName}、{@code paymentChannel}/{@code paymentChannelName}）。中文名由 payment 出口提供、admin 透传。</p>
 *
 * <p>金额出口规则（ADR-0011）：{@code amount} 为 <strong>Long（分）</strong>，与 payment
 * {@code PaymentOrderResponse.amount} 同型透传、零换算。</p>
 *
 * @since 0.1.0
 */
public record PaymentOrderDetailWireResponse(

        String paymentOrderNo,

        String businessOrderNo,

        String businessSystemName,

        /** 订单状态（payment BaseEnum code） */
        Integer status,

        /** 订单状态中文名（payment 出口提供） */
        String statusName,

        /** 支付金额（分） */
        Long amount,

        /** 支付方式（payment BaseEnum code） */
        Integer payMode,

        /** 支付方式中文名（payment 出口提供） */
        String payModeName,

        /** 接入类型（payment BaseEnum code） */
        Integer accessType,

        /** 接入类型中文名（payment 出口提供） */
        String accessTypeName,

        /** 支付渠道（payment BaseEnum code） */
        Integer paymentChannel,

        /** 支付渠道中文名（payment 出口提供） */
        String paymentChannelName,

        LocalDateTime paidAt,

        LocalDateTime createdAt
) {
}
