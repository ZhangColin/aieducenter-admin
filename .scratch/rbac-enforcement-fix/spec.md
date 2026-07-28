# Spec — RBAC 强制执行修复（Phase 0 Bug ① + ②）

Status: ready-for-agent
Feature: rbac-enforcement-fix
关联：ADR-0001（loginType）、ADR-0002（超管 bypass 归框架）、CONTEXT.md 决策日志

## Problem Statement

统一后台（admin BFF）的 RBAC **实质上是坏的**，权限注解形同虚设：

- **Bug ①（全员失效）**：admin 的 Sa-Token 权限解析（`StpInterface`）仅在 `loginType=="admin"` 时返回权限，而登录实际走 Sa-Token 默认命名空间（`"login"`）。两者不一致 → 框架拦截器对**任何**用户都拿到空权限列表 → `@RequirePermission` 对**所有人**失败。这个区分源自旧单体（运营与终端用户曾同进程），admin 独立成仓后已无意义。
- **Bug ②（超管被锁死）**：应用层对超管返回空权限、注释指望"框架拦截器对超管短路放行"，但框架当时没有超管 bypass 能力 → 超管通不过任何 `@RequirePermission`。内置 `admin` 账号登录后等于废的。

从使用者视角：拥有正确角色的运营访问该放行的接口仍被挡；超管则被完全锁在受保护接口之外。整个权限系统不工作。

## Solution

在框架侧补齐能力后，admin 侧两处根治：

1. **Bug ①**：放弃单体时代遗留的 `"admin"` 命名空间区分，admin 统一用 Sa-Token 默认 loginType；权限解析（`StpInterface`）无条件返回 admin 权限（本应用唯一的用户类型）。
2. **Bug ②**：消费 cartisan-security 新提供的 `AuthorizationBypassResolver` SPI——admin 提供一个 bean 声明"谁是超管"（有 `SUPER_ADMIN` 角色），框架拦截器在确认登录后、检查角色/权限前询问该 resolver，命中则跳过 `@RequireRole`/`@RequirePermission`（`@RequireAuth` 登录要求不受影响）。同步清理为绕 Bug ② 而存在的"超管返回空权限"workaround。

修完后：非超管靠 Bug ① 获得真实权限执行、超管靠 Bug ② 获得放行——RBAC 真正可用。

## User Stories

1. 作为超管，我希望登录后能访问任意受 `@RequirePermission` 保护的接口，以便不被本应被我绕过的权限检查挡住。
2. 作为超管，我希望仍必须先登录才能访问受保护接口，以便 bypass 不成为免登录后门。
3. 作为超管，我希望 bypass 同时覆盖 `@RequireRole`，以便任何角色受限的接口我也能进入。
4. 作为超管，我希望登录流程与非超管一致（同一登录端点），以便体验统一。
5. 作为拥有某权限的运营，我希望访问对应 `@RequirePermission` 接口时被放行（200），以便完成我的工作。
6. 作为运营，我希望我的权限由我的角色实际决定，以便最小权限原则真正生效。
7. 作为运营，我希望权限检查基于我登录态下的真实权限列表，以便授权准确。
8. 作为缺少某权限的运营，我希望访问该接口时被拒绝（403），以便访问控制真正强制执行。
9. 作为运营，我希望 403 由统一异常处理返回标准错误体，以便前端能一致处理。
10. 作为未登录访客，我希望访问受保护接口时被拒绝（401），以便系统不对外开放。
11. 作为未登录访客，我希望即使存在超管 bypass 机制也无法越权，以便无后门。
12. 作为平台团队，我希望 `@RequirePermission` 注解真正具有强制力，以便权限模型不是摆设。
13. 作为平台团队，我希望"超管 bypass"由框架统一提供、所有应用一致，以便不每个应用各搞一套。
14. 作为平台团队，我希望 admin 只声明"谁是超管"（提供 resolver bean），bypass 语义归框架，以便应用代码不掺杂框架级鉴权逻辑。
15. 作为平台团队，我希望清理掉为绕 bug 而存在的 workaround（超管返回空权限），以便代码不藏历史包袱。
16. 作为平台团队，我希望移除单体时代遗留的 loginType 命名空间区分，以便配置不再携带已失效的动机。
17. 作为平台团队，我希望框架改动向后兼容（不提供 resolver 的应用行为不变），以便升级不破坏其他消费方。
18. 作为平台团队，我希望 RBAC 修复有集成测试覆盖四个关键场景，以便回归能被及时发现。
19. 作为平台团队，我希望本次修复不改变对外 API 契约，以便前端无感。
20. 作为后续开发者，我希望超管判定点单一（resolver bean），以便将来调整"谁是超管"只改一处。
21. 作为前端（admin-web），我希望 `/current-admin` 对超管的返回契约本次不变，以便无需配合改动。

## Implementation Decisions

- **范围**：仅 Phase 0 的 Bug ①（loginType）+ Bug ②（超管 bypass）。依据 ADR-0001、ADR-0002。
- **框架前提**：cartisan-security 已实现 `AuthorizationBypassResolver { boolean shouldBypass(Long loginId); }`（中性命名，组合式 SPI），`SecurityInterceptor` 用 `ObjectProvider` 注入、登录确认后判断、未登录不 bypass、bean 不存在时行为不变。admin 消费前需在 cartisan-boot 执行一次 `mvn install` 刷新本地 Maven 制品（源码新于 `~/.m2` 中的旧 jar）。
- **Bug ① 改动（admin Sa-Token 配置）**：移除 `ADMIN_LOGIN_TYPE` 常量与 `loginType` 判断分支，`StpInterface` 无条件返回 admin 权限/角色。登录继续走默认 `StpUtil`。
- **Bug ② 改动（admin Sa-Token 配置）**：新增一个 `AuthorizationBypassResolver` bean，委托 admin 权限应用服务的 `isSuperAdmin(loginId)` 判定（框架 SPI 的 javadoc 即以此为例）。
- **workaround 清理（admin 权限应用服务）**：移除 `getPermissions` / `getRoleCodes` 中"超管 → 空列表"的特殊分支——它们是为绕 Bug ② 而存在，框架 bypass 落地后冗余。
- **架构决策**：Operator 用 Sa-Token 默认 loginType（不做应用内命名空间切分；隔离由 admin 作为独立服务满足）；超管 bypass 语义归框架，应用只声明"谁是超管"。
- **API 契约**：无对外契约变更。`/current-admin` 对超管返回的权限列表行为本次保持不变（仅强制执行由框架 bypass 覆盖；前端如何处理超管是独立前端细节，不在本 spec）。
- **数据库**：无 schema 变更。
- **交互**：`SecurityInterceptor` 在 `@RequireAuth` 通过后、`@RequireRole`/`@RequirePermission` 之前询问 `AuthorizationBypassResolver`。

## Testing Decisions

- **好测试的标准**：只测外部行为（HTTP 状态码 200 / 403 / 401），不测内部接线细节（不直接断言 `StpInterface` 内部或 bean 装配）。
- **主 seam（新增覆盖，HTTP/拦截器边界）—— 一个 `@SpringBootTest` 集成测试**：对齐 admin 现有 integration 约定（`@SpringBootTest` + 真库 + `@Transactional`），加载真实 `SecurityInterceptor` + Sa-Token + admin 的 `StpInterface` + admin 的 `AuthorizationBypassResolver` bean；用 MockMvc 打一个**真实** `@RequirePermission` 端点（复用现有 controller，不新建测试专用 controller），覆盖四场景：
  - 超管登录 → 放行（200）— 验 Bug ②
  - 非超管 + 有该权限 → 200 — 验 Bug ①
  - 非超管 + 无该权限 → 403 — 验 Bug ①
  - 未登录 → 401 — 验 `@RequireAuth` 不被 bypass（无后门）
- **现有 seam 维护（非新增）**：admin 权限应用服务的单测中，关于"超管返回空权限"的断言随 workaround 清理同步更新。
- **Prior art**：框架 `AuthAnnotationIntegrationTest`（MockMvc + 登录取 token + 断言 HTTP 状态，含 bypass 场景）；admin 自身 `@SpringBootTest` 集成测试（真库模式）。
- **约定**：测试命名 `given_{条件}_when_{操作}_then_{预期}`；断言用 AssertJ；HTTP 用 MockMvc。

## Out of Scope

- **Bug ③**（`AdminUser` 未映射 `system` 列 + 内置 `admin` 可被删）——尚未 grill，另出 spec。
- **Bug ④**（登录后未写 `SaSession.userName` → `RequestContext.userName` 恒 null）——尚未 grill，另出 spec；届时按"框架问题在框架修"原则评估 `AuthenticationService.login()` 契约易错性是否也提框架。
- **Phase 1**（部门 / 岗位模型、权限模型"注解扫描+列存 vs `sys_permissions` 字典表"）——未 grill。
- **Phase 2**（财务上下文、各能力域聚合、cartisan-openapi 签名客户端）——未 grill。
- **前端（admin-web）**：超管权限列表契约的前端处理、按钮级权限展示——独立前端仓库，本 spec 不涉及。
- **框架 `AuthorizationBypassResolver` SPI 本体**：已在 cartisan-security 实现；本 spec 仅消费，不改框架。
- **超管判定的性能优化**：`isSuperAdmin` 现按请求查库（框架 SPI 文档提示 per-request 调用、不缓存）；对齐 Sa-Token session 缓存的优化留待将来。

## Further Notes

- **前置**：实现前先在 cartisan-boot 执行 `mvn install`，确保 admin 能编译到新的 `AuthorizationBypassResolver` SPI。
- **里程碑意义**：本 spec 落地后，admin 的 RBAC 强制执行对超管与非超管均生效——即 Phase 0 的"RBAC 真正能用"里程碑达成（③、④ 随后 grill）。
- **工作原则**：本 spec 体现"框架问题在框架修、不在应用层绕"的原则（见 memory `framework-gaps-raise-requirement`）；Bug ② 是该原则的首个实例，需求记录于 cartisan-boot `.scratch/super-admin-bypass/issues/01`。
