# 02 — 超管授权 bypass 生效（修 Bug ②，消费框架 AuthorizationBypassResolver）

**Parent spec:** `.scratch/rbac-enforcement-fix/spec.md`

**What to build:** 让内置超管登录后能访问任意 `@RequirePermission` / `@RequireRole` 接口，而**未登录仍被拦**（无后门）。此前应用层对超管返回空权限、指望框架拦截器放行，但框架当时无 bypass 能力 → 超管通不过任何权限检查。框架现已提供 `AuthorizationBypassResolver` SPI（中性命名、组合式、向后兼容）。做法：先 `mvn install` cartisan-boot 刷新本地制品（源码新于 `~/.m2` 旧 jar）；admin 提供一个 `AuthorizationBypassResolver` bean，声明"有 `SUPER_ADMIN` 角色即超管"（委托 `isSuperAdmin`）；移除权限应用服务里为绕此 bug 而存在的"超管返回空权限/角色"workaround。

**Blocked by:** 01（同一 Sa-Token 配置 + 复用 01 建立的 `@SpringBootTest` 集成测试 seam）

**Status:** resolved

- [x] cartisan-boot 已 `mvn install`，admin 能编译并运行于含 `AuthorizationBypassResolver` SPI 的新 cartisan-security
- [x] admin 提供 `AuthorizationBypassResolver` bean（判定 `SUPER_ADMIN` 角色；框架 javadoc 即以此为例）
- [x] 超管登录后访问任意 `@RequirePermission` 接口返回 200
- [x] 超管登录后访问任意 `@RequireRole` 接口返回 200（bypass 同时覆盖角色）
- [x] 超管**未登录**时仍被 `@RequireAuth` 拦截（401，无后门）
- [x] 移除权限应用服务里"超管 → 空权限/角色"的 workaround 分支；其单测同步更新
- [x] 集成测试新增"超管 → 200"场景（复用 01 的 `@SpringBootTest` seam）
- [x] 非超管行为不回归（01 的三场景仍通过）

## Answer

已实现（全量套件 285 tests, 0 失败, 0 错误, 11 skipped；ArchUnit 通过；BUILD SUCCESS）。

- 前置：cartisan-boot `mvn install` 刷新 `~/.m2`（框架 commit `b497fd3` 的 `AuthorizationBypassResolver` SPI + 改造后的 `SecurityInterceptor` 进入本地制品，jar 由 Apr 22 → Jul 27）。
- `SaTokenConfig` 新增 `@Bean AuthorizationBypassResolver superAdminAuthorizationBypassResolver()`，委托 `AdminUserPermissionAppService::isSuperAdmin`（与框架 SPI javadoc 示例一致）。TDD：先写超管→200 红测试（无 bean → 超管得空权限 → 403），加 bean 后转绿。
- 清理：移除 `AdminUserPermissionAppService.getPermissions`/`getRoleCodes` 的"超管→空列表"workaround 分支（为绕 Bug ② 而存在，框架 bypass 落地后冗余）；`getMenus` 的超管分支保留（"超管可见全部菜单"是展示规则，与授权 bypass 无关，AC 措辞"空权限/角色"不含它）。单测同步：2 个"超管→空"用例改为断言聚合后真实结果，4 个邻接用例移除不再调用的 `hasRole` stub（Mockito strict stubbing）。

## Comments

- **`@RequireRole` 的 AC 由框架侧测试满足**：admin 无 `@RequireRole` 端点，spec 的 Testing Decisions 明确"用真实 `@RequirePermission` 端点、不新建测试专用 controller"。`@RequireRole` 的 bypass 覆盖由框架 `AuthAnnotationIntegrationTest.shouldBypassRoleCheck_whenSuperAdminRequests` 验证（同一 `shouldBypassAuthorization` 代码路径）。
- **超管未登录→401（无后门）**：框架 `SecurityInterceptor` 在 `@RequireAuth`（`StpUtil.checkLogin`）之后才询问 resolver，且 resolver 仅在 `isLogin()` 为真时被调用 → 超管未登录必 401。本类既有 `given_unauthenticated_when_listUsers_then_401` 即此性质的外部行为证明（未登录请求无 loginId，bypass 永不触发）。
- **`roleCodes` 契约微调（已知、增量、建议用户知悉）**：移除 `getRoleCodes` workaround 后，`/current-admin` 对超管返回的 `roleCodes` 由 `[]` 变为 `["SUPER_ADMIN"]`（增量、向后兼容）。spec 的"权限列表行为不变"特指 permissions（确仍为 `[]`——SUPER_ADMIN 角色无权限绑定）。`roleCodes` 增量使前端能据角色码识别超管，更正确；不破坏任何合理前端逻辑。若需严格保持 `[]`，回退该单分支即可（1 行）。
- 发现（沿用 01）：测试 `application.yml` 仍遮蔽 main，集成测试从 `SaManager.getConfig()` 读 token 头名/前缀以自适应。
