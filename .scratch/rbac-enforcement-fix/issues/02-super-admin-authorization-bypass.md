# 02 — 超管授权 bypass 生效（修 Bug ②，消费框架 AuthorizationBypassResolver）

**Parent spec:** `.scratch/rbac-enforcement-fix/spec.md`

**What to build:** 让内置超管登录后能访问任意 `@RequirePermission` / `@RequireRole` 接口，而**未登录仍被拦**（无后门）。此前应用层对超管返回空权限、指望框架拦截器放行，但框架当时无 bypass 能力 → 超管通不过任何权限检查。框架现已提供 `AuthorizationBypassResolver` SPI（中性命名、组合式、向后兼容）。做法：先 `mvn install` cartisan-boot 刷新本地制品（源码新于 `~/.m2` 旧 jar）；admin 提供一个 `AuthorizationBypassResolver` bean，声明"有 `SUPER_ADMIN` 角色即超管"（委托 `isSuperAdmin`）；移除权限应用服务里为绕此 bug 而存在的"超管返回空权限/角色"workaround。

**Blocked by:** 01（同一 Sa-Token 配置 + 复用 01 建立的 `@SpringBootTest` 集成测试 seam）

**Status:** ready-for-agent

- [ ] cartisan-boot 已 `mvn install`，admin 能编译并运行于含 `AuthorizationBypassResolver` SPI 的新 cartisan-security
- [ ] admin 提供 `AuthorizationBypassResolver` bean（判定 `SUPER_ADMIN` 角色；框架 javadoc 即以此为例）
- [ ] 超管登录后访问任意 `@RequirePermission` 接口返回 200
- [ ] 超管登录后访问任意 `@RequireRole` 接口返回 200（bypass 同时覆盖角色）
- [ ] 超管**未登录**时仍被 `@RequireAuth` 拦截（401，无后门）
- [ ] 移除权限应用服务里"超管 → 空权限/角色"的 workaround 分支；其单测同步更新
- [ ] 集成测试新增"超管 → 200"场景（复用 01 的 `@SpringBootTest` seam）
- [ ] 非超管行为不回归（01 的三场景仍通过）
