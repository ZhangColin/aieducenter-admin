## Parent
#38

## What to build
运营人员对退款单 approve/reject——**首个写端点**，打通操作者身份透传范式：admin 把当前 Operator 的 id/name 从 RequestContext 放入请求体（auditorId/auditorName），payment 落 OperationLog（auditType=MANUAL）。加 `POST /api/admin/payment/refunds/{no}/audit`，挂 `admin:payment:refund:audit`。审计归 payment，admin 不本地记账。

## Acceptance criteria
- [ ] `POST /refunds/{no}/audit` 透传 approve/reject + 操作者身份到 payment
- [ ] auditorId=RequestContext.getUserId()、auditorName=RequestContext.getUserName()（零 Sa-Token/零 DB/零新注解）
- [ ] 挂 `admin:payment:refund:audit`，无权限者 403
- [ ] BFF 集成测试断言请求体含正确 auditor 字段 + 错误翻译

## Blocked by
__BLOCKER__
