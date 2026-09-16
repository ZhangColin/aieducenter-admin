package com.aieducenter.admin.aiplatform.application.dto.command;

/**
 * AI 平台订单报价/改价北向命令——运营人员提交的定价意图，逐字镜像 aiplatform
 * {@code SubmitQuoteCommand}（#29 交易环②，spec #62 定稿、issue #70）。「已报价态重复
 * 提交＝改价」语义由 provider 承担（待报价态首次提交＝报价→已报价；已报价态重复提交＝
 * 改价，状态不变、append-only 价目行留痕）——BFF 不解释不预判。
 *
 * <p><strong>刻意不带 bean 校验注解</strong>（与 provider 命令同款形制）：字段合法性
 * （金额非正 ORD_008、备注超长 ORD_009）由 provider 聚合守卫裁决，错误信封原样透传
 * ——BFF 不加戏不重复校验（忠实透传零加戏，spec #62）。操作者身份不在命令体——经框架
 * {@code RequestContext}→{@code X-User-Id/X-User-Name} 自动透传落痕价目行，前端无法
 * 伪造报价归属（认知口径：操作者＝admin 侧管理员，非 aiplatform 平台用户）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformOrderQuoteCommand(

        /** 总价（分，正整数——合法性归 provider 聚合守卫 ORD_008） */
        Long amount,

        /** 报价备注（可空，至多 1000 字——超长归 provider ORD_009） */
        String note
) {
}
