## Parent
#38

## What to build
运营人员从列表页弹窗/抽屉打开支付/退款订单详情，并查看订单生命周期（按时间合并该单 PaymentLog + OperationLog 的只读视图）。加 `GET /payments/{no}`、`GET /refunds/{no}`、`GET /orders/{no}/lifecycle`。详情页不经菜单（前端弹窗/抽屉，见 CONTEXT.md「详情页 = 弹窗/抽屉优先」）。

## Acceptance criteria
- [ ] 三个端点返回详情聚合 / 生命周期时间线 DTO
- [ ] 挂 `admin:payment:read`
- [ ] BFF 集成测试覆盖详情映射、生命周期合并、payment 404→admin 404

## Blocked by
__BLOCKER__
