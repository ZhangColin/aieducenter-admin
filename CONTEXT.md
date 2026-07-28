# 统一后台后端 (aieducenter-admin) — 领域术语表

> 企业内部聚合入口（应用层 · 平台自带应用）：**Operator 自有** + **聚合各能力域** + **财务上下文**。SPA + BFF。
> 平台级术语表（限界上下文 / 基础域 / 组合域 / 用户域 / 钱包 / 支付 / Token计量…）在兄弟仓库 [`../aieducenter-architecture/CONTEXT.md`](../aieducenter-architecture/CONTEXT.md)，本文只记**本项目自有**的术语与决策演进。架构不变式见 [`CLAUDE.md` § 平台架构上下文](CLAUDE.md)。

## 术语表（随讨论生长）

**统一后台 (Admin Portal)**:
本应用（`aieducenter-admin` + `aieducenter-admin-web`）。企业内部聚合入口，SPA + BFF。`aieducenter-admin` = 本后端（端口 8081）。

**运营用户 (Operator)**:
后台内部员工（非跨应用共享的平台身份）。**认证 + 角色/部门/岗位/RBAC 归本应用自有**，凭据留本应用（用 cartisan-security），不进用户域/IdP。本地登录即可（统一后台是唯一内部应用，**不做 Operator SSO**，等第二个内部应用出现再考虑）。
_Avoid_: 把 Operator 塞进用户域/IdP；把 Operator 与终端用户(Account)混在一张表。

**RBAC（已有，真金白银）**:
`AdminUser` / `AdminRole` / `AdminMenu` 聚合 + `AdminUserRole` / `AdminRoleMenu` / `AdminRolePermission` 实体 + 6 张 `sys_admin_*` 表。鉴权：BCrypt(10) + cartisan-security（默认 `SaTokenAuthenticationService`）+ `StpInterface`（按 loginType 路由）+ `@RequirePermission` + `PermissionScanner`。

**财务上下文 (Finance Context)**:
本应用内的一个**限界上下文（非独立域/服务）**。只读各能力域（支付/钱包/Token计量）做**收入确认（consume-based，履约时点）+ append-only 冲销 + 负债/营销费用视角**。**不收款（支付域）、不持余额（钱包域）、不计量 token（Token计量域）**。详见架构仓库 architecture.md §6.15。

**聚合 / 签名调用 (Aggregation via signed calls)**:
前端不直连各能力域；统一后台经 **cartisan-openapi 签名调用**聚合各域（支付/钱包/模型网关/Token计量/…），对前端呈现统一视图。

## 稳定不变式（来自平台架构，本项目务必遵守）

- Operator 归本应用自有；不做 Operator SSO。
- 财务上下文只读各能力域；不收款、不持余额、不计量 token。
- 经 cartisan-openapi 签名调用各能力域；前端不直连各域。
- 现有 RBAC（AdminUser/Role/Menu + sys_admin_*）继续用；动手新功能前先修已知 bug。

## 决策记录（grilling 中逐项落地；完整 ADR 见 [`docs/adr/`](docs/adr/)）

### Phase 0 — 修 4 个 RBAC bug（地基，CLAUDE.md 强制）
- ✅ [ADR-0001](docs/adr/0001-admin-uses-default-sa-token-login-type.md) Sa-Token 用默认 loginType、放弃 "admin" 命名空间（Bug ①）— 已定
- 🔁 Bug ② 超管 bypass — 框架已实现 `AuthorizationBypassResolver` SPI（中性命名，`shouldBypass(Long)`）；admin 消费待实现（先 `mvn install` cartisan-boot 刷新 `~/.m2`）。见 [ADR-0002](docs/adr/0002-super-admin-bypass-is-framework-gap.md)
- ⏳ Bug ③ `AdminUser` 未映射 `system` 列 + 内置账号可被删 — 待
- ⏳ Bug ④ 登录后未写 `SaSession.userName` — 待

### Phase 1 — Operator 模型补全
- ⏳ 部门 / 岗位模型（树？数据权限挂钩？）
- ⏳ 权限模型：注解扫描 + 列存 vs 独立 `sys_permissions` 字典表

### Phase 2 — 财务上下文 + 各能力域聚合
- ⏳ 财务首批视图 + 与各域取数契约
- ⏳ cartisan-openapi 签名客户端

## ADR

设计决策记录在 [`docs/adr/`](docs/adr/)（随讨论建立）。
