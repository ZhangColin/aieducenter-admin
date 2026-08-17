package com.aieducenter.admin.payment.application.dto.query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付订单列表查询参数（BFF 透传 payment）。
 *
 * <p>筛选字段对齐 payment {@code GET /api/v1/payments} 查询参数；{@code statuses} 为多选，
 * 经 {@link com.aieducenter.admin.payment.infrastructure.PaymentClient} 展开为重复的 {@code status} 参数。
 * 字段以 payment 实现契约为准。</p>
 *
 * <p>枚举筛选项（{@code statuses} / {@code payMode} / {@code accessType} / {@code paymentChannel}）以 payment
 * {@code BaseEnum} 的 <strong>Integer code</strong> 传递——payment 按 code 绑定枚举（cartisan-web BaseEnum↔code），
 * 非 enum name。金额筛选 {@code amountMin/Max} 为 <strong>Long（分）</strong>，与 payment 同型透传、零换算
 * （ADR-0011——前端已按分提交，admin 不做分↔元换算）。</p>
 *
 * @since 0.1.0
 */
public record PaymentOrderQuery(

        /** 支付订单号 */
        String paymentOrderNo,

        /** 业务订单号 */
        String businessOrderNo,

        /** 业务系统名（精确或模糊，由 payment 决定） */
        String businessSystemName,

        /** 订单状态多选（payment BaseEnum code）；空 = 不限 */
        List<Integer> statuses,

        /** 支付方式（payment BaseEnum code） */
        Integer payMode,

        /** 接入类型（payment BaseEnum code） */
        Integer accessType,

        /** 支付通道（payment BaseEnum code） */
        Integer paymentChannel,

        /** 金额下限（分，含） */
        Long amountMin,

        /** 金额上限（分，含） */
        Long amountMax,

        /** 创建时间起（含） */
        LocalDateTime createdAtFrom,

        /** 创建时间止（含） */
        LocalDateTime createdAtTo,

        /** 支付时间起（含） */
        LocalDateTime paidAtFrom,

        /** 支付时间止（含） */
        LocalDateTime paidAtTo
) {
}
