package com.aieducenter.admin.payment.infrastructure;

import com.aieducenter.admin.payment.application.dto.query.PaymentOrderQuery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * payment 列表端点的 wire 反序列化契约测试。
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
 * {@code PaymentClient.listPayments}（传入它真实的 {@code TypeReference}）→ 真实 Jackson 反序列化 → 真实
 * {@code PaymentManagementAppService.list}。</p>
 *
 * <p>fixture 取 payment <strong>真实</strong> 序列化形状：枚举为 Integer code（{@code PaymentStatus.PAID=2}、
 * {@code PayMode.WECHAT=9}、{@code AccessType.H5=4}、{@code PaymentChannel.ICBC=1}）+ 同名 {@code *Name} 中文名
 * （ADR-0009）——而非早期凭 spec 猜测的 enum name 串；金额为 <strong>string 形态</strong>（{@code "amount": "9900"}，
 * Long 分，ADR-0011——cartisan-web 全局对 Long 注册 {@code ToStringSerializer}，与 {@code auditorId} 等既有
 * Long 字段同形态）。既验信封反序列化不丢字段，又验 {@code *Name} 与金额 Long 透传到 admin Response。</p>
 *
 * @since 0.1.0
 */
class PaymentClientListEnvelopeContractTest {

    /**
     * payment GET /api/v1/payments 的真实响应形状：ApiResponse<PageResponse<PaymentOrderResponse>> 信封，
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
                "page": 0,
                "size": 20
              },
              "requestId": null,
              "errors": null
            }
            """;

    @Test
    void given_paymentApiResponseEnvelope_when_listPayments_then_itemsPopulatedWithEnumNames() {
        var appService = PaymentWireTestSupport.appServiceWithStubTransport(PAYMENT_LIST_ENVELOPE);

        var page = appService.list(
                new PaymentOrderQuery(null, null, null, null, null, null, null,
                        null, null, null, null, null, null),
                org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(page.items()).hasSize(1);
        var item = page.items().get(0);
        // 信封反序列化不丢字段
        assertThat(item.paymentOrderNo()).isEqualTo("PAY-1");
        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.page()).isZero();
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
}
