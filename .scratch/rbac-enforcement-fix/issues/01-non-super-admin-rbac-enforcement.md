# 01 — 非超管 RBAC 强制执行生效（修 Bug ① loginType）

**Parent spec:** `.scratch/rbac-enforcement-fix/spec.md`

**What to build:** 让拥有正确角色/权限的运营人员，其 `@RequirePermission` 接口**按权限真正放行/拒绝**——此前因 Sa-Token loginType 不匹配，权限解析对全员返回空，`@RequirePermission` 对所有人失效。做法：移除 admin Sa-Token 配置里单体时代遗留的 `"admin"` loginType 区分（源自旧单体运营/终端用户同进程），权限解析（`StpInterface`）无条件返回 admin 权限（本应用唯一的用户类型）。登录继续走默认 Sa-Token 命名空间。

**Blocked by:** None — 可立即开始

**Status:** ready-for-agent

- [ ] 非超管运营**拥有**某 `@RequirePermission` 权限时，访问该接口返回 200
- [ ] 非超管运营**缺少**该权限时，访问该接口返回 403
- [ ] 未登录访问受 `@RequireAuth` 保护接口返回 401
- [ ] admin Sa-Token 配置移除了 `"admin"` loginType 区分（相关常量与 loginType 判断分支已删除，权限解析无条件返回 admin 权限）
- [ ] 新增一个 `@SpringBootTest` 集成测试（真库，对齐 admin 现有 integration 约定），覆盖上述三个场景；prior art = 框架 `AuthAnnotationIntegrationTest` + admin 自身 `@SpringBootTest` 集成测试
- [ ] 既有测试不受影响（超管路径留待 02，本票不引入回归）
