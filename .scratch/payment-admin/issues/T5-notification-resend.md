## Parent
#38

## What to build
运营人员重发支付/退款结果通知到业务系统（补救漏投），不改订单状态。复用 T4 的操作者身份透传范式。加 `POST /api/admin/payment/payments/{no}/notifications/resend` + `POST /api/admin/payment/refunds/{no}/notifications/resend`，挂 `admin:payment:notification:resend`。

## Acceptance criteria
- [ ] 两个重发端点透传到 payment，不改订单状态
- [ ] 挂 `admin:payment:notification:resend`，无权限者 403
- [ ] BFF 集成测试覆盖重发调用 + 错误翻译

## Blocked by
__BLOCKER__
