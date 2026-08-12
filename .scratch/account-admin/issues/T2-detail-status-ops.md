## Parent
#49（平台账号管理 BFF 实现 spec）

## What to build
账号管理详情（抽屉）+ 三个账号状态操作。复用 T1 打通的 AccountClient / AppService / 集成测试范式：

1. **管理详情**：`GET /api/admin/accounts/{userId}/management`（透传 identity management 详情，DTO 映射 identity wire → admin response；详情走前端抽屉、**不种菜单**）；`admin:account:read`。
2. **状态操作三端点**：
   - `POST /accounts/{userId}/disable`（body `{reason}`，reason 必填；封号——identity 侧自动踢所有会话）
   - `POST /accounts/{userId}/activate`（解封）
   - `POST /accounts/{userId}/unlock`（解除系统锁定，区别于封号状态）
   - 写权限码 `admin:account:write`（预案，见 spec；与 payment 统一时再定拆分）
   - 三操作均经 `RequestContext`→`X-User-Id/X-User-Name` 透传 operator 身份，供 identity 审计记录「哪个运营操作了谁」。

## Acceptance criteria
- [ ] `GET .../management` 返回 identity 详情字段映射后的 admin response
- [ ] `disable` 透传 `{reason}`（reason 必填校验）、`activate` / `unlock` 纯透传
- [ ] 三写操作经 RequestContext 透传 operator 身份（集成测试断言 X-User-Id/Name 被带入出站调用）
- [ ] 三写端点挂 `admin:account:write`，无权限者 403（RBAC 集成测试覆盖）
- [ ] identity 错误按 HTTP 状态翻译为 ADMIN_* DomainException（如账号不存在→404）
- [ ] BFF 集成测试覆盖详情映射 + 三操作的透传 + 错误翻译

## Blocked by
T1（复用 AccountClient / AppService / 集成测试范式）
