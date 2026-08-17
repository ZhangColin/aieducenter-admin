package com.aieducenter.admin.payment.infrastructure;

import com.aieducenter.admin.payment.application.dto.response.OrderLifecycleResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * lifecycle 端点（{@code GET /api/v1/orders/{orderNo}/lifecycle}）的 wire 反序列化契约测试。
 *
 * <p>根因（issue #57）：payment 实返 {@code ApiResponse<List<OrderLifecycleResponse>>}——
 * {@code data} 为<strong>扁平事件数组</strong>（orderNo 是路径参数，响应无包装），事件为语义 9 字段
 * {@code (id, source, createdAt, action, actionName, outcome, performer, performerSystem, detail)}。
 * 早期 wire 按平表 union 想象写成 {@code ApiResponse<{orderNo, events:[…20 字段]}>} 对象，对数组反序列化
 * 直接抛 {@code MismatchedInputException}——订单详情抽屉时间线每次调用报错的根源。且该端点此前零测试覆盖。</p>
 *
 * <p>本测试经 {@link PaymentWireTestSupport} 子类化 {@code OpenApiClient} 仅替换 HTTP 传输，其余全真实：
 * 真实 {@code PaymentClient.getLifecycle}（真实 {@code TypeReference}）→ 真实 Jackson 反序列化 →
 * 真实 {@code PaymentManagementAppService.getLifecycle} 映射。</p>
 *
 * <p>fixture 取 payment <strong>真实</strong> 序列化形状（ADR-0011）：扁平数组、{@code Long} 字段（{@code id}）
 * 为 string 形态（框架 {@code ToStringSerializer}）、{@code source} 取值 {@code GATEWAY}/{@code OPERATION}
 * （非早期想象的 PAYMENT_LOG/OPERATION_LOG）。</p>
 *
 * @since 0.1.0
 */
class PaymentClientLifecycleContractTest {

    /**
     * payment GET /api/v1/orders/{orderNo}/lifecycle 的真实响应形状：
     * ApiResponse&lt;List&lt;OrderLifecycleResponse&gt;&gt; 信封，data 为语义 9 字段事件扁平数组（无 orderNo 包装）。
     */
    private static final String LIFECYCLE_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": [
                {
                  "id": "1001",
                  "source": "OPERATION",
                  "createdAt": "2026-08-12T10:05:00",
                  "action": "AUDIT_APPROVE",
                  "actionName": "审核通过",
                  "outcome": "SUCCESS",
                  "performer": "alice",
                  "performerSystem": "admin-console",
                  "detail": "同意退款"
                },
                {
                  "id": "501",
                  "source": "GATEWAY",
                  "createdAt": "2026-08-12T09:55:00",
                  "action": "PAYMENT_REQUEST",
                  "actionName": "PAYMENT_REQUEST",
                  "outcome": "SUCCESS",
                  "performer": "ICBC_PAY",
                  "performerSystem": "ICBC",
                  "detail": "交易成功"
                },
                {
                  "id": "502",
                  "source": "GATEWAY",
                  "createdAt": "2026-08-12T09:50:00",
                  "action": "REFUND_REQUEST",
                  "actionName": "REFUND_REQUEST",
                  "outcome": "FAILED",
                  "performer": "ICBC_REFUND",
                  "performerSystem": "ICBC",
                  "detail": "余额不足"
                }
              ],
              "requestId": null,
              "errors": null
            }
            """;

    @Test
    void given_flatArrayEnvelope_when_getLifecycle_then_deserializesSemanticEventsWithoutThrowing() {
        var appService = PaymentWireTestSupport.appServiceWithStubTransport(LIFECYCLE_ENVELOPE);

        // 曾为 bug（#57 根源）：typeres 按对象信封反序列化数组 → MismatchedInputException；契约对齐后为扁平事件列表
        List<OrderLifecycleResponse> lifecycle = appService.getLifecycle("PAY-1");

        assertThat(lifecycle).hasSize(3);

        // OPERATION 事件：语义 9 字段逐字镜像（action=operation 枚举名，actionName=中文名，
        // performer=operatorName，performerSystem=operatorSystem，detail=remark）
        var operation = lifecycle.get(0);
        assertThat(operation.id()).isEqualTo(1001L);   // Long 字段以 string 形态上 wire（框架 ToStringSerializer）
        assertThat(operation.source()).isEqualTo("OPERATION");
        assertThat(operation.createdAt()).isEqualTo(LocalDateTime.of(2026, 8, 12, 10, 5));
        assertThat(operation.action()).isEqualTo("AUDIT_APPROVE");
        assertThat(operation.actionName()).isEqualTo("审核通过");
        assertThat(operation.outcome()).isEqualTo("SUCCESS");
        assertThat(operation.performer()).isEqualTo("alice");
        assertThat(operation.performerSystem()).isEqualTo("admin-console");
        assertThat(operation.detail()).isEqualTo("同意退款");

        // GATEWAY 成功事件：action=logType、performer=bankInterface、performerSystem=bankCode、detail=returnMsg
        var gatewayOk = lifecycle.get(1);
        assertThat(gatewayOk.id()).isEqualTo(501L);
        assertThat(gatewayOk.source()).isEqualTo("GATEWAY");
        assertThat(gatewayOk.action()).isEqualTo("PAYMENT_REQUEST");
        assertThat(gatewayOk.actionName()).isEqualTo("PAYMENT_REQUEST");
        assertThat(gatewayOk.outcome()).isEqualTo("SUCCESS");
        assertThat(gatewayOk.performer()).isEqualTo("ICBC_PAY");
        assertThat(gatewayOk.performerSystem()).isEqualTo("ICBC");
        assertThat(gatewayOk.detail()).isEqualTo("交易成功");

        // GATEWAY 失败事件：outcome=FAILED、detail=errorMessage
        var gatewayFailed = lifecycle.get(2);
        assertThat(gatewayFailed.outcome()).isEqualTo("FAILED");
        assertThat(gatewayFailed.detail()).isEqualTo("余额不足");
    }
}
