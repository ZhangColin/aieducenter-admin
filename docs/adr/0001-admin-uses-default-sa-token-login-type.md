# ADR-0001：Admin 统一使用 Sa-Token 默认 loginType（放弃 "admin" 命名空间）

- **状态**：Accepted
- **日期**：2026-07-27
- **关联**：起步包必修 Bug ①（Sa-Token loginType 不匹配）

## 背景

Sa-Token 权限接线存在 Bug：`SaTokenConfig.cartisanStpInterface()` 仅在 `loginType=="admin"` 时返回管理员权限，否则返回空；而 `AdminUserAuthAppService.login()` 经 cartisan-security 的 `SaTokenAuthenticationService` 走默认 `StpUtil.login()`（Sa-Token 默认 loginType `"login"`）；`SecurityInterceptor`（框架内）调 `StpUtil.checkPermission()` 也走默认 `"login"` 命名空间。三者不一致 → `StpInterface` 恒以 `"login"` 被调用 → 返回空 → `@RequirePermission` 对全员失效。

`loginType=="admin"` 的区分源自旧单体 `aieducenter-platform/server`——彼时 Operator 登录与 Account（终端用户）登录**同进程**，用 loginType 把两类会话分到不同 Sa-Token 命名空间（旧 `SaTokenConfig` 注释明写："通过 loginType 区分管理员和普通用户"）。admin 独立成仓后该配置被原样带入，但 Account 登录侧并未带入、在本应用也不存在。

架构（`../aieducenter-architecture/.scratch/base-platform/map.md`）锁死：**admin 永远 operator-only、不做 Account SSO**。故"另一类用户"在本应用不存在、按架构也不会出现。

## 决策

放弃 `"admin"` 命名空间区分。`SaTokenConfig` 的 `StpInterface` **无条件**返回 admin 权限/角色（不再按 loginType 路由），删除 `ADMIN_LOGIN_TYPE` 常量。登录继续走默认 `StpUtil`（`"login"` 命名空间）。

架构要求"Account 与 Operator 各自 Sa-Token 命名空间、天然隔离"由**服务级隔离**满足（admin 是独立服务、与 identity 服务分库分进程），**不需要应用内再用 loginType 切分**。

## 结果

- ✅ Bug ① 修复：非超管的 `@RequirePermission` 生效。
- ✅ 移除单体遗物，配置简化、降低后续踩坑面。
- ⚠️ 若将来（违背当前架构）admin 需第二种会话域，需重新引入命名空间区分——架构明确不会。

**改动范围**：`config/SaTokenConfig.java`（删常量 + 去掉 loginType 判断）；`AdminUserAuthAppService.login()` 不动。
