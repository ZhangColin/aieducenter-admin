package com.aieducenter.admin.payment.infrastructure;

import com.aieducenter.admin.payment.application.dto.query.OperationLogQuery;
import com.aieducenter.admin.payment.application.dto.query.RefundOrderQuery;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * payment wire 枚举 *Name 透传契约测试（ADR-0009 / issue #50）。
 *
 * <p>验证：payment 出口给出的 {@code *Name} 中文名经 admin wire 反序列化 → {@code toXxx} 映射 → admin 对外 Response
 * 一路透传同一中文名。fixture 取 payment <strong>真实</strong> 序列化形状（枚举 Integer code + {@code *Name}），
 * 而非凭 spec 猜测的值——这正是 #37 未与 payment 真实契约比对、且既有契约测试用自造 fixture 导致 mismatch 恒绿
 * （直到 #50 才浮现）的纠偏（见 #55 根因）。覆盖 #50 受影响端点：状态分布（issue 复现路径）、退款列表、操作日志。
 * 支付列表的透传在 {@link PaymentClientListEnvelopeContractTest} 覆盖。</p>
 *
 * <p>测试方式经 {@link PaymentWireTestSupport} 子类化 {@code OpenApiClient} 仅替换 HTTP 传输，其余全真实
 * （真实 {@code PaymentClient} + 真实 Jackson 反序列化 + 真实 {@code PaymentManagementAppService} 映射）。</p>
 *
 * @since 0.1.0
 */
class PaymentEnumNamePassThroughContractTest {

    @Test
    void given_paymentStatusDistributionWithStatusName_when_getOrderStatusDistribution_then_bucketCarriesStatusName() {
        // issue #50 复现路径：GET /stats/orders/status-distribution 的各分桶须含 status + statusName
        String envelope = """
                {
                  "code": 0, "message": "ok",
                  "data": {
                    "paymentStatuses": [
                      {"status": 2, "statusName": "已支付", "count": 72, "amount": 7200}
                    ],
                    "refundStatuses": [
                      {"status": 1, "statusName": "待审核", "count": 3, "amount": 300}
                    ],
                    "refundPendingAuditCount": 3
                  },
                  "requestId": null, "errors": null
                }
                """;
        var resp = PaymentWireTestSupport.appServiceWithStubTransport(envelope).getOrderStatusDistribution();

        assertThat(resp.paymentStatuses()).hasSize(1);
        assertThat(resp.paymentStatuses().get(0).status()).isEqualTo(2);
        assertThat(resp.paymentStatuses().get(0).statusName()).isEqualTo("已支付");
        assertThat(resp.refundStatuses().get(0).statusName()).isEqualTo("待审核");
    }

    @Test
    void given_refundListWithEnumNames_when_listRefunds_then_itemsCarryStatusAndAuditNames() {
        // fixture 取 payment 真实序列化形状：金额 Long 经框架 ToStringSerializer 出 string（ADR-0011）、
        // 无 auditorId/auditedAt（payment RefundOrderResponse 从不发送的 ghost，#59 删）
        String envelope = """
                {
                  "code": 0, "message": "ok",
                  "data": {
                    "items": [
                      {
                        "refundOrderNo": "RF-1", "paymentOrderNo": "PAY-1", "businessOrderNo": "BIZ-1",
                        "businessSystemName": "course-svc",
                        "status": 1, "statusName": "待审核",
                        "refundAmount": "9900",
                        "auditType": 2, "auditTypeName": "人工审核",
                        "auditorName": null,
                        "createdAt": "2026-08-12T09:55:00"
                      }
                    ],
                    "total": 1, "page": 0, "size": 20
                  },
                  "requestId": null, "errors": null
                }
                """;
        var page = PaymentWireTestSupport.appServiceWithStubTransport(envelope).listRefunds(
                new RefundOrderQuery(null, null, null, null, null, null, null,
                        null, null, null, null),
                PageRequest.of(0, 20));

        assertThat(page.items()).hasSize(1);
        var item = page.items().get(0);
        assertThat(item.status()).isEqualTo(1);
        assertThat(item.statusName()).isEqualTo("待审核");
        // 金额 Long（分）透传：string 形态进、Long 出（零换算，ADR-0011 §1）
        assertThat(item.refundAmount()).isEqualTo(9900L);
        assertThat(item.auditType()).isEqualTo(2);
        assertThat(item.auditTypeName()).isEqualTo("人工审核");
    }

    @Test
    void given_operationLogsWithEnumNames_when_listOperationLogs_then_itemsCarryTargetTypeAndOperationNames() {
        String envelope = """
                {
                  "code": 0, "message": "ok",
                  "data": {
                    "items": [
                      {
                        "id": 100,
                        "targetType": 1, "targetTypeName": "支付订单",
                        "targetNo": "PAY-1",
                        "operation": 3, "operationName": "通知重发",
                        "operatorId": 7, "operatorName": "张三", "operatorSystem": "admin-console",
                        "result": "SUCCESS", "remark": "补发通知",
                        "createdAt": "2026-08-12T10:00:00"
                      }
                    ],
                    "total": 1, "page": 0, "size": 20
                  },
                  "requestId": null, "errors": null
                }
                """;
        var page = PaymentWireTestSupport.appServiceWithStubTransport(envelope).listOperationLogs(
                new OperationLogQuery(null, null, null, null, null, null, null, null),
                PageRequest.of(0, 20));

        assertThat(page.items()).hasSize(1);
        var item = page.items().get(0);
        assertThat(item.targetType()).isEqualTo(1);
        assertThat(item.targetTypeName()).isEqualTo("支付订单");
        assertThat(item.operation()).isEqualTo(3);
        assertThat(item.operationName()).isEqualTo("通知重发");
    }
}
