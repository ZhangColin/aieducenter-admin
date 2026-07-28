# 01 — 非超管 RBAC 强制执行生效（修 Bug ① loginType）

**Parent spec:** `.scratch/rbac-enforcement-fix/spec.md`

**What to build:** 让拥有正确角色/权限的运营人员，其 `@RequirePermission` 接口**按权限真正放行/拒绝**——此前因 Sa-Token loginType 不匹配，权限解析对全员返回空，`@RequirePermission` 对所有人失效。做法：移除 admin Sa-Token 配置里单体时代遗留的 `"admin"` loginType 区分（源自旧单体运营/终端用户同进程），权限解析（`StpInterface`）无条件返回 admin 权限（本应用唯一的用户类型）。登录继续走默认 Sa-Token 命名空间。

**Blocked by:** None — 可立即开始

**Status:** resolved

- [x] 非超管运营**拥有**某 `@RequirePermission` 权限时，访问该接口返回 200
- [x] 非超管运营**缺少**该权限时，访问该接口返回 403
- [x] 未登录访问受 `@RequireAuth` 保护接口返回 401
- [x] admin Sa-Token 配置移除了 `"admin"` loginType 区分（相关常量与 loginType 判断分支已删除，权限解析无条件返回 admin 权限）
- [x] 新增一个 `@SpringBootTest` 集成测试（真库，对齐 admin 现有 integration 约定），覆盖上述三个场景；prior art = 框架 `AuthAnnotationIntegrationTest` + admin 自身 `@SpringBootTest` 集成测试
- [x] 既有测试不受影响（超管路径留待 02，本票不引入回归）

## Answer

已实现（commit `c8ea11c`，全量套件 284 tests 绿）。

- `SaTokenConfig`：移除 `"admin"` loginType 区分（ADR-0001）。
- 修复中暴露两个 latent bug（此前 loginType 不匹配、StpInterface 永远走不到，故被掩盖）：
  - `(Long) loginId` 强转 → Sa-Token/Redis-Jackson 反序列化为 String，改为 Number/String 健壮收敛。
  - `AdminUserPermissionAppService` 读方法未声明事务 → 拦截器阶段懒加载角色集合无 session（LazyInit），加 `@Transactional(readOnly=true)`。
- 测试 seam 实际用 `@SpringBootTest(RANDOM_PORT) + TestRestTemplate`（真实 servlet 过滤器链）；MockMvc 在 Sa-Token 1.45.0 下 token 读取有坑，故用真实端口。token 头名/前缀从 `SaManager` 运行时配置读取（测试 `application.yml` 遮蔽 main，Sa-Token 用默认 `satoken` 头）。
- 附带 unblock：`AdminAuthControllerTest` 既有编译错误（controller 弃用 `@CurrentUser` 改 `RequestContext` 后未同步）。

## Comments

- 发现：`src/test/resources/application.yml` 遮蔽 `src/main/resources/application.yml`，测试环境 Sa-Token 用默认配置（`satoken` 头），与生产（`Authorization/Bearer`）不同——后续可考虑改 profile 化（`application-test.yml`）让测试环境更贴近生产。
