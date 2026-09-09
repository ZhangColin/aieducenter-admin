## Parent
#38

## ⚠️ 决策待定
是否给操作员暴露「主动查行」（对齐银行真相的同步）？payment 列为可选、admin-bff 自决。**未定前此票不 ready-for-agent。** 暴露 → 按下建；不暴露 → 关闭此票。

## What to build（若决定暴露）
运营人员主动触发查行，把本地订单状态同步成银行真实态。加 `POST /api/admin/payment/payments/{no}/query`，挂 `admin:payment:bank:query`。

## Acceptance criteria
- [ ] 决策已定：暴露 / 不暴露
- [ ] （若暴露）`POST /payments/{no}/query` 透传到 payment
- [ ] （若暴露）挂 `admin:payment:bank:query`，无权限者 403
- [ ] （若暴露）BFF 集成测试覆盖

## Blocked by
__BLOCKER__ + 产品决策（暴露与否）
