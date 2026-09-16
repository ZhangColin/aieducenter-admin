package com.aieducenter.admin.payment.infrastructure;

import com.aieducenter.admin.payment.application.dto.query.OperationLogQuery;
import com.aieducenter.admin.payment.application.dto.query.PaymentLogQuery;
import com.aieducenter.admin.payment.application.dto.query.PaymentOrderQuery;
import com.aieducenter.admin.payment.application.dto.query.RefundOrderQuery;
import com.cartisan.web.request.Pagination;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * payment 四列表端点的 wire 反序列化契约测试。
 *
 * <p>根因：payment 的列表端点返回 {@code ApiResponse<PageResponse<...>>} 信封
 * （{@code {"code","message","data":{"items","total","page","size"},...}}）。{@link PaymentClient} 必须按信封
 * 反序列化并取 {@code .data()}；若把信封体直接当 {@code PageResponse} 反序列化，则在 Spring Boot 默认
 * {@code FAIL_ON_UNKNOWN_PROPERTIES=false} 下，信封的 {@code code/message/data} 被静默忽略，
 * {@code PageResponse.items} 默认为 {@code null}，继而在 {@code PaymentManagementAppService.list} 的
 * {@code page.items().stream()} 处抛 NPE（生产 {@code PaymentController.listPayments} 500）。</p>
 *
 * <p>本测试 <strong>不</strong> mock {@code PaymentClient}（既有集成测试在方法边界 mock，恰好掩盖了这条 wire 契约），
 * 而是经 {@link PaymentWireTestSupport} 子类化 {@code OpenApiClient} 仅替换 HTTP 传输，其余全真实：真实
 * {@code PaymentClient} 列表方法（传入它真实的 {@code TypeReference}）→ 真实 Jackson 反序列化 → 真实
 * {@code PaymentManagementAppService} 映射。四个列表端点（payments / refunds / payment-logs / operation-logs）
 * 各至少一例打到真实 deserialization seam。</p>
 *
 * <p>fixture 取 payment <strong>真实</strong> 序列化形状：枚举为 Integer code（{@code PaymentStatus.PAID=2}、
 * {@code PayMode.WECHAT=9}、{@code AccessType.H5=4}、{@code PaymentChannel.ICBC=1}）+ 同名 {@code *Name} 中文名
 * （ADR-0009）——而非早期凭 spec 猜测的 enum name 串；金额为 <strong>string 形态</strong>（{@code "amount": "9900"}，
 * Long 分，ADR-0011——cartisan-web 全局对 Long 注册 {@code ToStringSerializer}，与 {@code auditorId} 等既有
 * Long 字段同形态）。既验信封反序列化不丢字段，又验 {@code *Name} 与金额 Long 透传到 admin Response。</p>
 *
 * <p>分页契约（#73 钉死，ADR-0012）：全链 1-based——北向 {@code page} 1-based 经框架 {@code Pagination} 绑定，
 * wire 与北向<strong>同值直传</strong>（无 ±1）；payment #22 起响应 {@code page} 回显==请求页码。fixture 的
 * {@code "page"} 按 payment 真实回显构造（wire 请求 page=1 → 回显 1）。</p>
 *
 * @since 0.1.0
 */
class PaymentClientListEnvelopeContractTest {

    /**
     * payment GET /api/v1/payments 的真实响应形状：ApiResponse&lt;PageResponse&lt;PaymentOrderResponse&gt;&gt; 信封，
     * 枚举为 Integer code + *Name 中文名，金额为 string 形态的 Long（分）。
     */
    private static final String PAYMENT_LIST_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "items": [
                  {
                    "paymentOrderNo": "PAY-1",
                    "businessOrderNo": "BIZ-1",
                    "businessSystemName": "course-svc",
                    "status": 2,
                    "statusName": "已支付",
                    "amount": "9900",
                    "payMode": 9,
                    "payModeName": "微信",
                    "accessType": 4,
                    "accessTypeName": "H5",
                    "paymentChannel": 1,
                    "paymentChannelName": "工商银行",
                    "paidAt": "2026-08-12T10:00:00",
                    "createdAt": "2026-08-12T09:55:00"
                  }
                ],
                "total": 1,
                "page": 1,
                "size": 20
              },
              "requestId": null,
              "errors": null
            }
            """;

    /** payment GET /api/v1/refunds 的真实响应形状：ApiResponse&lt;PageResponse&lt;RefundOrderResponse&gt;&gt; 信封。 */
    private static final String REFUND_LIST_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "items": [
                  {
                    "refundOrderNo": "REF-1",
                    "paymentOrderNo": "PAY-1",
                    "businessOrderNo": "BIZ-1",
                    "businessSystemName": "course-svc",
                    "status": 3,
                    "statusName": "已退款",
                    "refundAmount": "9900",
                    "auditType": 2,
                    "auditTypeName": "人工审核",
                    "auditorName": "运营甲",
                    "createdAt": "2026-08-12T11:00:00"
                  }
                ],
                "total": 1,
                "page": 1,
                "size": 20
              },
              "requestId": null,
              "errors": null
            }
            """;

    /** payment GET /api/v1/payment-logs 的真实响应形状：ApiResponse&lt;PageResponse&lt;PaymentLogResponse&gt;&gt; 信封。 */
    private static final String PAYMENT_LOG_LIST_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "items": [
                  {
                    "id": "1001",
                    "paymentOrderNo": "PAY-1",
                    "refundOrderNo": null,
                    "logType": "PAYMENT_REQUEST",
                    "bankCode": "ICBC",
                    "bankInterface": "pay",
                    "httpStatus": 200,
                    "returnCode": "0",
                    "returnMsg": "ok",
                    "executionTime": 120,
                    "success": true,
                    "errorMessage": null,
                    "createdAt": "2026-08-12T09:55:01"
                  }
                ],
                "total": 1,
                "page": 1,
                "size": 20
              },
              "requestId": null,
              "errors": null
            }
            """;

    /** payment GET /api/v1/operation-logs 的真实响应形状：ApiResponse&lt;PageResponse&lt;OperationLogResponse&gt;&gt; 信封。 */
    private static final String OPERATION_LOG_LIST_ENVELOPE = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "items": [
                  {
                    "id": "2001",
                    "targetType": 1,
                    "targetTypeName": "支付单",
                    "targetNo": "PAY-1",
                    "operation": 3,
                    "operationName": "重发通知",
                    "operatorId": "9001",
                    "operatorName": "运营甲",
                    "operatorSystem": "admin-console",
                    "result": "SUCCESS",
                    "remark": null,
                    "createdAt": "2026-08-12T12:00:00"
                  }
                ],
                "total": 1,
                "page": 1,
                "size": 20
              },
              "requestId": null,
              "errors": null
            }
            """;

    @Test
    void given_paymentApiResponseEnvelope_when_listPayments_then_itemsPopulatedWithEnumNames() {
        String[] wireUrl = new String[1];
        var appService = PaymentWireTestSupport.appServiceWithStubTransport(PAYMENT_LIST_ENVELOPE, wireUrl);

        var page = appService.list(
                new PaymentOrderQuery(null, null, null, null, null, null, null,
                        null, null, null, null, null, null),
                new Pagination(1, 20, null));

        // 出站 page 直传（ADR-0012）：北向 page=1 → wire 即 page=1（无 ±1）
        assertThat(wireUrl[0]).endsWith("/api/v1/payments?page=1&size=20");

        assertThat(page.items()).hasSize(1);
        var item = page.items().get(0);
        // 信封反序列化不丢字段
        assertThat(item.paymentOrderNo()).isEqualTo("PAY-1");
        assertThat(page.total()).isEqualTo(1L);
        // 响应 page 为 payment 回显（==请求页码），北向透传
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);
        // 枚举 code 为 Integer（与 payment 真实形状一致，非 enum name 串）
        assertThat(item.status()).isEqualTo(2);
        assertThat(item.payMode()).isEqualTo(9);
        assertThat(item.accessType()).isEqualTo(4);
        assertThat(item.paymentChannel()).isEqualTo(1);
        // 金额 Long（分）从 string 形态 fixture 透传到北向 Response（ADR-0011：与 payment 同型、零换算）
        assertThat(item.amount()).isEqualTo(9900L);
        // *Name 中文名透传到 admin Response（ADR-0009）——前端直读，取代前端 i18n 枚举映射
        assertThat(item.statusName()).isEqualTo("已支付");
        assertThat(item.payModeName()).isEqualTo("微信");
        assertThat(item.accessTypeName()).isEqualTo("H5");
        assertThat(item.paymentChannelName()).isEqualTo("工商银行");
    }

    @Test
    void given_paymentApiResponseEnvelope_when_listRefunds_then_itemsPopulatedAndPagePassthrough() {
        String[] wireUrl = new String[1];
        var appService = PaymentWireTestSupport.appServiceWithStubTransport(REFUND_LIST_ENVELOPE, wireUrl);

        var page = appService.listRefunds(
                new RefundOrderQuery(null, null, null, null, null, null, null,
                        null, null, null, null),
                new Pagination(1, 20, null));

        // 出站 page 直传（ADR-0012）：北向 page=1 → wire 即 page=1
        assertThat(wireUrl[0]).endsWith("/api/v1/refunds?page=1&size=20");

        assertThat(page.items()).hasSize(1);
        var item = page.items().get(0);
        assertThat(item.refundOrderNo()).isEqualTo("REF-1");
        assertThat(item.paymentOrderNo()).isEqualTo("PAY-1");
        assertThat(item.status()).isEqualTo(3);
        assertThat(item.statusName()).isEqualTo("已退款");
        assertThat(item.refundAmount()).isEqualTo(9900L);
        assertThat(item.auditType()).isEqualTo(2);
        assertThat(item.auditTypeName()).isEqualTo("人工审核");
        assertThat(item.auditorName()).isEqualTo("运营甲");
        assertThat(page.total()).isEqualTo(1L);
        // 响应 page 为 payment 回显（==请求页码），北向透传
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void given_paymentApiResponseEnvelope_when_listPaymentLogs_then_itemsPopulatedAndPagePassthrough() {
        String[] wireUrl = new String[1];
        var appService = PaymentWireTestSupport.appServiceWithStubTransport(PAYMENT_LOG_LIST_ENVELOPE, wireUrl);

        var page = appService.listPaymentLogs(
                new PaymentLogQuery(null, null, null, null, null, null, null, null),
                new Pagination(1, 20, null));

        // 出站 page 直传（ADR-0012）：北向 page=1 → wire 即 page=1
        assertThat(wireUrl[0]).endsWith("/api/v1/payment-logs?page=1&size=20");

        assertThat(page.items()).hasSize(1);
        var item = page.items().get(0);
        assertThat(item.id()).isEqualTo(1001L);
        assertThat(item.paymentOrderNo()).isEqualTo("PAY-1");
        assertThat(item.logType()).isEqualTo("PAYMENT_REQUEST");
        assertThat(item.bankCode()).isEqualTo("ICBC");
        assertThat(item.httpStatus()).isEqualTo(200);
        assertThat(item.success()).isTrue();
        assertThat(page.total()).isEqualTo(1L);
        // 响应 page 为 payment 回显（==请求页码），北向透传
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void given_paymentApiResponseEnvelope_when_listOperationLogs_then_itemsPopulatedAndPagePassthrough() {
        String[] wireUrl = new String[1];
        var appService = PaymentWireTestSupport.appServiceWithStubTransport(OPERATION_LOG_LIST_ENVELOPE, wireUrl);

        var page = appService.listOperationLogs(
                new OperationLogQuery(null, null, null, null, null, null, null, null),
                new Pagination(1, 20, null));

        // 出站 page 直传（ADR-0012）：北向 page=1 → wire 即 page=1
        assertThat(wireUrl[0]).endsWith("/api/v1/operation-logs?page=1&size=20");

        assertThat(page.items()).hasSize(1);
        var item = page.items().get(0);
        assertThat(item.id()).isEqualTo(2001L);
        assertThat(item.targetType()).isEqualTo(1);
        assertThat(item.targetTypeName()).isEqualTo("支付单");
        assertThat(item.targetNo()).isEqualTo("PAY-1");
        assertThat(item.operation()).isEqualTo(3);   // OperationType NOTIFY_RESEND 的 BaseEnum code
        assertThat(item.operationName()).isEqualTo("重发通知");
        assertThat(item.operatorId()).isEqualTo(9001L);
        assertThat(item.operatorSystem()).isEqualTo("admin-console");
        assertThat(page.total()).isEqualTo(1L);
        // 响应 page 为 payment 回显（==请求页码），北向透传
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);
    }
}
