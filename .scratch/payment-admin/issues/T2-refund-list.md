## Parent
#38

## What to build
运营人员在「支付管理 → 退款订单」分页查看并筛选退款订单。复用 T1 的 PaymentClient/list 范式，加 `GET /api/admin/payment/refunds`（refundOrderNo/paymentOrderNo/businessOrderNo/businessSystemName/status/auditType/auditorId/退款金额区间/createdAt 区间）。

## Acceptance criteria
- [ ] `GET /refunds` 返回分页+筛选结果，分页形状对齐 T1 的 payments
- [ ] 挂 `admin:payment:read`
- [ ] BFF 集成测试覆盖退款筛选映射/分页/错误翻译

## Blocked by
__BLOCKER__
