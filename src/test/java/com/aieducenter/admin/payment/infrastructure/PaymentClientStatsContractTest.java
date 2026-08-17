package com.aieducenter.admin.payment.infrastructure;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * payment 统计端点 wire 形状契约测试（issue #60 / ADR-0011）。
 *
 * <p>钉住 8 个 stats 端点（overview / status-distribution / gateway-health / operations-audit /
 * by-business-system / by-channel / anomalies / operations-activity）的北向形状 = payment 源码
 * {@code application/dto/response/*.java} 的<strong>逐字镜像</strong>：字段名、类型、嵌套不增删不改
 * （零加戏）。金额一律 {@code Long}（分）、比率/均值为 {@code BigDecimal}——金额与比率分型是 provider
 * 契约本身的决定。</p>
 *
 * <p>fixture 取 payment <strong>真实</strong>序列化形状：框架全局 Long→{@code ToStringSerializer}，
 * 故 Long 字段（含金额、笔数、id）上 wire 为 <strong>string</strong> 形态（{@code "9900"}，ADR-0011 §2）；
 * Integer 枚举 code、BigDecimal 比率为 number。此前 #37 按 spec 文档理想化撰写 wire（amount 作
 * BigDecimal、backlog 作扁平字段），导致与 payment 实返形状三层对不上——本测试即纠偏后的对接权威
 * （web #54 联动以此为准）。</p>
 *
 * <p>测试方式经 {@link PaymentWireTestSupport} 子类化 {@code OpenApiClient} 仅替换 HTTP 传输，其余全真实
 * （真实 {@code PaymentClient} + 真实 typeres + 真实 Jackson 反序列化 + 真实
 * {@code PaymentManagementAppService} 映射）。与 {@link PaymentClientStatsQueryContractTest}（请求查询串）
 * 互补：彼处验出站请求，此处验入站响应形状。</p>
 *
 * @since 0.1.0
 */
class PaymentClientStatsContractTest {

    // 窗口端点 from/to 必填（issue #51）——stub 传输忽略 URL，但 PaymentClient 构建查询串需要非空值
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 7, 14, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 8, 13, 23, 59, 59);

    private static final String ENVELOPE_HEAD = """
            {"code": 0, "message": "ok", "data": """;
    private static final String ENVELOPE_TAIL = """
            , "requestId": null, "errors": null}""";

    private static String envelope(String dataJson) {
        return ENVELOPE_HEAD + dataJson + ENVELOPE_TAIL;
    }

    // ========== overview：嵌套 Summary（笔数·金额·成功笔数·成功金额·成功率）+ 净额 + 9 字段趋势分桶 ==========

    @Test
    void given_paymentOverviewWithNestedSummaries_when_getPaymentOverview_then_mapSummariesNetAmountAndTrend() {
        // payment PaymentOverviewResponse：payment/refund 为嵌套 Summary（非扁平字段）、金额 long 分、比率 BigDecimal；
        // TrendBucket 为 9 字段（含成功子集 paid*/refunded*）。故意让 trend 顺序与时间序相反——admin 透传不重排。
        String data = """
                {
                  "payment": {"count": "1200", "amount": "9800000", "successCount": "1182", "successAmount": "9650000", "successRate": 0.9850},
                  "refund": {"count": "30", "amount": "240000", "successCount": "25", "successAmount": "200000", "successRate": 0.8333},
                  "netAmount": "9450000",
                  "trend": [
                    {"bucket": "2026-08-12T00:00:00", "paymentCount": "20", "paymentAmount": "160000", "paidCount": "19", "paidAmount": "152000",
                     "refundCount": "1", "refundAmount": "8000", "refundedCount": "1", "refundedAmount": "8000"},
                    {"bucket": "2026-08-11T00:00:00", "paymentCount": "18", "paymentAmount": "144000", "paidCount": "18", "paidAmount": "144000",
                     "refundCount": "0", "refundAmount": "0", "refundedCount": "0", "refundedAmount": "0"}
                  ]
                }""";
        var resp = PaymentWireTestSupport.appServiceWithStubTransport(envelope(data))
                .getPaymentOverview(FROM, TO, null);

        // 支付摘要：笔数/金额（分，string 进 Long 出零换算）/成功子集/比率
        assertThat(resp.payment().count()).isEqualTo(1200L);
        assertThat(resp.payment().amount()).isEqualTo(9800000L);
        assertThat(resp.payment().successCount()).isEqualTo(1182L);
        assertThat(resp.payment().successAmount()).isEqualTo(9650000L);
        assertThat(resp.payment().successRate()).isEqualByComparingTo("0.9850");
        // 退款摘要
        assertThat(resp.refund().count()).isEqualTo(30L);
        assertThat(resp.refund().amount()).isEqualTo(240000L);
        assertThat(resp.refund().successRate()).isEqualByComparingTo("0.8333");
        // 净额（分）
        assertThat(resp.netAmount()).isEqualTo(9450000L);
        // 趋势分桶：9 字段逐字透传 + payment 给的顺序原样保留（b12 在前，admin 不重排为 b11 在前）
        assertThat(resp.trend()).hasSize(2);
        var first = resp.trend().get(0);
        assertThat(first.bucket()).isEqualTo(LocalDateTime.of(2026, 8, 12, 0, 0));
        assertThat(first.paymentCount()).isEqualTo(20L);
        assertThat(first.paymentAmount()).isEqualTo(160000L);
        assertThat(first.paidCount()).isEqualTo(19L);
        assertThat(first.paidAmount()).isEqualTo(152000L);
        assertThat(first.refundCount()).isEqualTo(1L);
        assertThat(first.refundAmount()).isEqualTo(8000L);
        assertThat(first.refundedCount()).isEqualTo(1L);
        assertThat(first.refundedAmount()).isEqualTo(8000L);
        assertThat(resp.trend().get(1).paymentAmount()).isEqualTo(144000L);
    }

    // ========== status-distribution：分桶 count/amount Long + 嵌套 refundBacklog{pendingCount, pendingAmount} ==========

    @Test
    void given_statusDistributionWithNestedBacklog_when_getOrderStatusDistribution_then_mapBucketsAndBacklogAmount() {
        // payment StatusDistributionResponse：分桶 (status, statusName, long count, long amount)，积压为嵌套
        // Backlog(pendingCount, pendingAmount)——非扁平 refundPendingAuditCount（#60 前恒 null，积压金额整个丢失）
        String data = """
                {
                  "paymentStatuses": [
                    {"status": 1, "statusName": "待支付", "count": "50", "amount": "400000"},
                    {"status": 2, "statusName": "已支付", "count": "800", "amount": "6400000"}
                  ],
                  "refundStatuses": [
                    {"status": 1, "statusName": "待审核", "count": "3", "amount": "30000"},
                    {"status": 5, "statusName": "退款成功", "count": "25", "amount": "200000"}
                  ],
                  "refundBacklog": {"pendingCount": "3", "pendingAmount": "30000"}
                }""";
        var resp = PaymentWireTestSupport.appServiceWithStubTransport(envelope(data))
                .getOrderStatusDistribution();

        // 支付/退款分桶：枚举 Integer code + *Name + Long 笔数·金额（分），顺序原样
        assertThat(resp.paymentStatuses()).hasSize(2);
        assertThat(resp.paymentStatuses().get(0).status()).isEqualTo(1);
        assertThat(resp.paymentStatuses().get(0).statusName()).isEqualTo("待支付");
        assertThat(resp.paymentStatuses().get(0).count()).isEqualTo(50L);
        assertThat(resp.paymentStatuses().get(0).amount()).isEqualTo(400000L);
        assertThat(resp.paymentStatuses().get(1).status()).isEqualTo(2);
        assertThat(resp.paymentStatuses().get(1).amount()).isEqualTo(6400000L);
        assertThat(resp.refundStatuses()).hasSize(2);
        assertThat(resp.refundStatuses().get(0).statusName()).isEqualTo("待审核");
        assertThat(resp.refundStatuses().get(1).amount()).isEqualTo(200000L);
        // 退款待审核积压：嵌套 refundBacklog——积压金额 pendingAmount 首次透出（#60）
        assertThat(resp.refundBacklog().pendingCount()).isEqualTo(3L);
        assertThat(resp.refundBacklog().pendingAmount()).isEqualTo(30000L);
    }

    // ========== gateway-health：interfaces[].bankCode + totalCount + BigDecimal avgExecutionTimeMs ==========

    @Test
    void given_gatewayHealthInterfaces_when_getGatewayHealth_then_mapInterfacesWithBankCodeAndMsAverages() {
        // payment GatewayHealthResponse：列表名 interfaces、每项含 bankCode；次数为 long、比率/平均耗时为 BigDecimal
        String data = """
                {
                  "interfaces": [
                    {"bankCode": "ICBC", "bankInterface": "ICBC_PAY", "totalCount": "1000", "successCount": "980",
                     "successRate": 0.9800, "avgExecutionTimeMs": 120.50,
                     "returnCodes": [{"returnCode": "000000", "count": "980"}, {"returnCode": "9999", "count": "20"}]},
                    {"bankCode": "WECHAT", "bankInterface": "WECHAT_QUERY", "totalCount": "500", "successCount": "495",
                     "successRate": 0.9900, "avgExecutionTimeMs": 80.00, "returnCodes": []}
                  ]
                }""";
        var resp = PaymentWireTestSupport.appServiceWithStubTransport(envelope(data))
                .getGatewayHealth(FROM, TO);

        assertThat(resp.interfaces()).hasSize(2);
        var first = resp.interfaces().get(0);
        assertThat(first.bankCode()).isEqualTo("ICBC");
        assertThat(first.bankInterface()).isEqualTo("ICBC_PAY");
        assertThat(first.totalCount()).isEqualTo(1000L);
        assertThat(first.successCount()).isEqualTo(980L);
        assertThat(first.successRate()).isEqualByComparingTo("0.9800");
        assertThat(first.avgExecutionTimeMs()).isEqualByComparingTo("120.50");
        // 返回码分布映射 + 顺序保留；第二个接口空列表透传为空、非 null
        assertThat(first.returnCodes()).hasSize(2);
        assertThat(first.returnCodes().get(0).returnCode()).isEqualTo("000000");
        assertThat(first.returnCodes().get(0).count()).isEqualTo(980L);
        assertThat(first.returnCodes().get(1).returnCode()).isEqualTo("9999");
        assertThat(resp.interfaces().get(1).bankCode()).isEqualTo("WECHAT");
        assertThat(resp.interfaces().get(1).returnCodes()).isEmpty();
    }

    // ========== operations-audit：totalAudits/approvedCount/rejectedCount + BigDecimal avgAuditDurationMinutes + byAuditor ==========

    @Test
    void given_operationsAuditWithByAuditor_when_getOperationsAudit_then_mapTopLevelAndAuditorBreakdowns() {
        // payment OperationsAuditResponse：顶层五字段（均值单位为分钟、BigDecimal）+ byAuditor
        // （AuditorBreakdown 六字段——无人均时长，payment 侧避免跨聚合归属歧义）
        String data = """
                {
                  "totalAudits": "60",
                  "approvedCount": "54",
                  "rejectedCount": "6",
                  "approvalRate": 0.9000,
                  "avgAuditDurationMinutes": 30.50,
                  "byAuditor": [
                    {"auditorId": "1001", "auditorName": "alice", "count": "40", "approvedCount": "38", "rejectedCount": "2", "approvalRate": 0.9500},
                    {"auditorId": "2002", "auditorName": "bob", "count": "20", "approvedCount": "16", "rejectedCount": "4", "approvalRate": 0.8000}
                  ]
                }""";
        var resp = PaymentWireTestSupport.appServiceWithStubTransport(envelope(data))
                .getOperationsAudit(FROM, TO);

        assertThat(resp.totalAudits()).isEqualTo(60L);
        assertThat(resp.approvedCount()).isEqualTo(54L);
        assertThat(resp.rejectedCount()).isEqualTo(6L);
        assertThat(resp.approvalRate()).isEqualByComparingTo("0.9000");
        assertThat(resp.avgAuditDurationMinutes()).isEqualByComparingTo("30.50");
        assertThat(resp.byAuditor()).hasSize(2);
        assertThat(resp.byAuditor().get(0).auditorId()).isEqualTo(1001L);
        assertThat(resp.byAuditor().get(0).auditorName()).isEqualTo("alice");
        assertThat(resp.byAuditor().get(0).count()).isEqualTo(40L);
        assertThat(resp.byAuditor().get(0).approvedCount()).isEqualTo(38L);
        assertThat(resp.byAuditor().get(0).rejectedCount()).isEqualTo(2L);
        assertThat(resp.byAuditor().get(0).approvalRate()).isEqualByComparingTo("0.9500");
        assertThat(resp.byAuditor().get(1).auditorName()).isEqualTo("bob");
        assertThat(resp.byAuditor().get(1).approvalRate()).isEqualByComparingTo("0.8000");
    }

    // ========== by-business-system：businessSystems[].{businessSystemName, payment Summary, refund Summary, refundRate} ==========

    @Test
    void given_businessSystemBreakdowns_when_getByBusinessSystem_then_mapNestedSummariesAndRefundRate() {
        // payment ByBusinessSystemResponse：列表名 businessSystems，每系统 payment/refund 为嵌套 Summary（非扁平字段）
        String data = """
                {
                  "businessSystems": [
                    {
                      "businessSystemName": "course-svc",
                      "payment": {"count": "800", "amount": "6400000", "successCount": "784", "successAmount": "6272000", "successRate": 0.9800},
                      "refund": {"count": "20", "amount": "160000", "successCount": "16", "successAmount": "128000", "successRate": 0.8000},
                      "refundRate": 0.0204
                    },
                    {
                      "businessSystemName": "membership-svc",
                      "payment": {"count": "400", "amount": "3200000", "successCount": "380", "successAmount": "3040000", "successRate": 0.9500},
                      "refund": {"count": "10", "amount": "80000", "successCount": "10", "successAmount": "80000", "successRate": 1.0000},
                      "refundRate": 0.0263
                    }
                  ]
                }""";
        var resp = PaymentWireTestSupport.appServiceWithStubTransport(envelope(data))
                .getByBusinessSystem(FROM, TO);

        assertThat(resp.businessSystems()).hasSize(2);
        var first = resp.businessSystems().get(0);
        assertThat(first.businessSystemName()).isEqualTo("course-svc");
        assertThat(first.payment().count()).isEqualTo(800L);
        assertThat(first.payment().amount()).isEqualTo(6400000L);
        assertThat(first.payment().successRate()).isEqualByComparingTo("0.9800");
        assertThat(first.refund().count()).isEqualTo(20L);
        assertThat(first.refund().amount()).isEqualTo(160000L);
        assertThat(first.refundRate()).isEqualByComparingTo("0.0204");
        assertThat(resp.businessSystems().get(1).businessSystemName()).isEqualTo("membership-svc");
        assertThat(resp.businessSystems().get(1).refund().successRate()).isEqualByComparingTo("1.0000");
    }

    // ========== by-channel：byPayMode/byAccessType 同构 ChannelBreakdown（channelCode Integer + channelName） ==========

    @Test
    void given_channelBreakdowns_when_getByChannel_then_mapBothDimensionsWithEnumCodeAndName() {
        // payment ByChannelResponse：两维度同一 ChannelBreakdown record——渠道为枚举 Integer code + channelName
        // （PayMode WECHAT=9/微信、ALIPAY=10/支付宝；AccessType H5=4），金额 long 分、比率 BigDecimal
        String data = """
                {
                  "byPayMode": [
                    {"channelCode": 9, "channelName": "微信", "count": "600", "amount": "4800000", "successCount": "588", "successAmount": "4704000", "successRate": 0.9800},
                    {"channelCode": 10, "channelName": "支付宝", "count": "400", "amount": "3200000", "successCount": "384", "successAmount": "3072000", "successRate": 0.9600}
                  ],
                  "byAccessType": [
                    {"channelCode": 4, "channelName": "H5", "count": "700", "amount": "5600000", "successCount": "679", "successAmount": "5432000", "successRate": 0.9700}
                  ]
                }""";
        var resp = PaymentWireTestSupport.appServiceWithStubTransport(envelope(data))
                .getByChannel(FROM, TO);

        assertThat(resp.byPayMode()).hasSize(2);
        assertThat(resp.byPayMode().get(0).channelCode()).isEqualTo(9);
        assertThat(resp.byPayMode().get(0).channelName()).isEqualTo("微信");
        assertThat(resp.byPayMode().get(0).count()).isEqualTo(600L);
        assertThat(resp.byPayMode().get(0).amount()).isEqualTo(4800000L);
        assertThat(resp.byPayMode().get(0).successCount()).isEqualTo(588L);
        assertThat(resp.byPayMode().get(0).successAmount()).isEqualTo(4704000L);
        assertThat(resp.byPayMode().get(0).successRate()).isEqualByComparingTo("0.9800");
        assertThat(resp.byPayMode().get(1).channelName()).isEqualTo("支付宝");
        assertThat(resp.byAccessType()).hasSize(1);
        assertThat(resp.byAccessType().get(0).channelCode()).isEqualTo(4);
        assertThat(resp.byAccessType().get(0).channelName()).isEqualTo("H5");
        assertThat(resp.byAccessType().get(0).amount()).isEqualTo(5600000L);
        assertThat(resp.byAccessType().get(0).successRate()).isEqualByComparingTo("0.9700");
    }

    // ========== anomalies：嵌套 StuckOrders{count, amount} ×2 + RecentFailures{totalCount, byType[]} ==========

    @Test
    void given_anomaliesWithStuckOrdersAndFailures_when_getAnomalies_then_mapNestedCountsAndAmounts() {
        // payment AnomaliesResponse：长时滞留为 StuckOrders(count, amount) 嵌套（笔数+金额），近期失败为
        // RecentFailures(totalCount, byType[{logType, failureCount}]) 嵌套——非三个扁平计数字段
        String data = """
                {
                  "longPendingPayments": {"count": "12", "amount": "96000"},
                  "longRefundingRefunds": {"count": "3", "amount": "24000"},
                  "recentFailures": {"totalCount": "8", "byType": [
                    {"logType": "PAYMENT_QUERY", "failureCount": "5"},
                    {"logType": "REFUND_QUERY", "failureCount": "3"}
                  ]}
                }""";
        var resp = PaymentWireTestSupport.appServiceWithStubTransport(envelope(data))
                .getAnomalies();

        assertThat(resp.longPendingPayments().count()).isEqualTo(12L);
        assertThat(resp.longPendingPayments().amount()).isEqualTo(96000L);
        assertThat(resp.longRefundingRefunds().count()).isEqualTo(3L);
        assertThat(resp.longRefundingRefunds().amount()).isEqualTo(24000L);
        assertThat(resp.recentFailures().totalCount()).isEqualTo(8L);
        assertThat(resp.recentFailures().byType()).hasSize(2);
        assertThat(resp.recentFailures().byType().get(0).logType()).isEqualTo("PAYMENT_QUERY");
        assertThat(resp.recentFailures().byType().get(0).failureCount()).isEqualTo(5L);
        assertThat(resp.recentFailures().byType().get(1).logType()).isEqualTo("REFUND_QUERY");
        assertThat(resp.recentFailures().byType().get(1).failureCount()).isEqualTo(3L);
    }

    // ========== operations-activity：byOperator[].totalCount + operation Integer code/*Name + notifyResend 汇总 ==========

    @Test
    void given_operationsActivityWithNotifyResend_when_getOperationsActivity_then_mapByOperatorAndNotifyResendRollup() {
        // payment OperationsActivityResponse：byOperator（operatorId 可空、totalCount 为该员全部操作数、
        // operations 为枚举 Integer code + operationName 中文名）；notifyResend 为顶层汇总
        // （totalCount + byBusinessSystem，来源系统可空——系统动作单列一组保留 null）
        String data = """
                {
                  "byOperator": [
                    {"operatorId": "1001", "operatorName": "alice", "totalCount": "40",
                     "operations": [
                       {"operation": 1, "operationName": "审核通过", "count": "38"},
                       {"operation": 2, "operationName": "审核拒绝", "count": "2"}
                     ]},
                    {"operatorId": "2002", "operatorName": "bob", "totalCount": "3",
                     "operations": [{"operation": 3, "operationName": "通知重发", "count": "3"}]}
                  ],
                  "notifyResend": {"totalCount": "3", "byBusinessSystem": [
                    {"businessSystem": "course-svc", "count": "2"},
                    {"businessSystem": null, "count": "1"}
                  ]}
                }""";
        var resp = PaymentWireTestSupport.appServiceWithStubTransport(envelope(data))
                .getOperationsActivity(FROM, TO);

        assertThat(resp.byOperator()).hasSize(2);
        var first = resp.byOperator().get(0);
        assertThat(first.operatorId()).isEqualTo(1001L);
        assertThat(first.operatorName()).isEqualTo("alice");
        assertThat(first.totalCount()).isEqualTo(40L);
        assertThat(first.operations()).hasSize(2);
        assertThat(first.operations().get(0).operation()).isEqualTo(1);
        assertThat(first.operations().get(0).operationName()).isEqualTo("审核通过");
        assertThat(first.operations().get(0).count()).isEqualTo(38L);
        assertThat(first.operations().get(1).operation()).isEqualTo(2);
        assertThat(resp.byOperator().get(1).operatorName()).isEqualTo("bob");
        assertThat(resp.byOperator().get(1).operations()).hasSize(1);
        assertThat(resp.byOperator().get(1).operations().get(0).operation()).isEqualTo(3);
        // 通知重发汇总：总数 + 按来源业务系统（含 null 系统组原样透传）
        assertThat(resp.notifyResend().totalCount()).isEqualTo(3L);
        assertThat(resp.notifyResend().byBusinessSystem()).hasSize(2);
        assertThat(resp.notifyResend().byBusinessSystem().get(0).businessSystem()).isEqualTo("course-svc");
        assertThat(resp.notifyResend().byBusinessSystem().get(0).count()).isEqualTo(2L);
        assertThat(resp.notifyResend().byBusinessSystem().get(1).businessSystem()).isNull();
        assertThat(resp.notifyResend().byBusinessSystem().get(1).count()).isEqualTo(1L);
    }
}
