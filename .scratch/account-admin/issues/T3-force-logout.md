## Parent
#49（平台账号管理 BFF 实现 spec）

## What to build
强制下线（一键 revoke 全部会话）。复用 T1 的 AccountClient / AppService / 集成测试范式：

- `POST /api/admin/accounts/{userId}/sessions/revoke`（强制下线——一键 revoke 该账号全部会话、**不改账号状态**，区别于 disable；identity 无会话列表端点，故无列表视图，前端为详情抽屉内一个按钮）。
- 写权限码 `admin:account:write`（预案，见 spec）。
- 经 `RequestContext`→`X-User-Id/X-User-Name` 透传 operator 身份供 identity 审计。

## Acceptance criteria
- [ ] `revoke` 端点透传 identity `/sessions/revoke`、**不改账号状态**（区别于 disable，集成测试断言 account 状态字段不变）
- [ ] 经 RequestContext 透传 operator 身份（集成测试断言）
- [ ] 挂 `admin:account:write`，无权限者 403（RBAC 集成测试覆盖）
- [ ] identity 错误按 HTTP 状态翻译为 ADMIN_* DomainException
- [ ] BFF 集成测试覆盖 revoke 透传 + 身份透传 + 错误翻译

## Blocked by
T1（复用 AccountClient / AppService / 集成测试范式）；入口在 T2 详情抽屉内
