# ADR-0002：超管权限 bypass 归 cartisan-security 框架（Bug ② 不在应用层修）

- **状态**：Accepted；框架已实现（2026-07-27）；admin 消费待实现
- **日期**：2026-07-27
- **关联**：起步包必修 Bug ②；框架实现 [cartisan-boot `.scratch/super-admin-bypass/issues/01`](../../../cartisan-boot/.scratch/super-admin-bypass/issues/01-super-admin-permission-bypass.md)

## 背景

`AdminUserPermissionAppService.getPermissions()` 对超管返回空权限、注释"由 SaToken 拦截器直接放行"，但 cartisan-security 的 `SecurityInterceptor` 无超管短路 → 超管通不过任何 `@RequirePermission`。"超管 bypass 所有权限/角色检查（仍须登录）"是所有 cartisan-security RBAC 应用的通用需求，非 admin 专有。

## 决策

按"框架问题在框架里修、不在应用层绕"的原则（memory: `framework-gaps-raise-requirement`），Bug ② 认作**框架缺口**，不在 admin 内打补丁。需求提给 cartisan-security（`.scratch/super-admin-bypass/issues/01`）。

### 框架侧 triage + 实现（2026-07-27）

- ✅ 接受为框架缺口。
- 采纳 F1 的 SPI 组合路线，但命名**中性化**为 **`AuthorizationBypassResolver { boolean shouldBypass(Long loginId); }`**（不特化"超管"业务概念——框架只提供"授权 bypass"机制，"谁是超管"由应用判定）。
- `SecurityInterceptor` 用 `ObjectProvider<AuthorizationBypassResolver>` 注入：在 `@RequireAuth` 登录确认**之后**、`@RequireRole` / `@RequirePermission` 检查**之前**判断，命中则跳过授权检查。`@RequireAuth` 不参与 bypass（超管也须登录）；未登录不 bypass（无后门）；bean 不存在时行为不变（向后兼容）。
- 否决：F1 原命名 `SuperAdminResolver`（业务特化）、template method（组合优于继承）、context object（YAGNI）、F3 / F2。
- 含单元 + 集成测试。

### admin 消费（待实现）

- 提供 `@Bean AuthorizationBypassResolver`，委托 `adminPermissionAppService.isSuperAdmin(loginId)`（框架 SPI 的 javadoc 即以此为例）。
- 清理 `AdminUserPermissionAppService` 里"对超管返回空权限/角色"的旧 workaround 分支（`getPermissions` / `getRoleCodes` 中 `hasRole(SUPER_ADMIN) → List.of()`）——它们是为绕 Bug ② 而存在的，框架 bypass 落地后冗余。

### 前置

cartisan-boot 源码已改，但 `~/.m2` 的 `cartisan-security-0.1.0-SNAPSHOT.jar` 仍是旧版（4/22）。admin 消费前需在 cartisan-boot 跑 `mvn install` 刷新本地制品。

## 结果

- ✅ Bug ② 在框架层根治；所有 cartisan-security 应用受益。
- ✅ admin 侧零 workaround、零框架 fork。
- ⏸ `/current-admin` 对超管返回的权限列表契约（前端 admin-web 怎么处理超管）是独立的前端细节，不阻塞本 bug（强制执行已由框架 bypass 覆盖）。

## 参见

- 工作原则 memory：`framework-gaps-raise-requirement`。
