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
`AdminUser` / `AdminRole` / `AdminMenu` 聚合 + `AdminUserRole` / `AdminRoleMenu` / `AdminRolePermission` 实体 + 6 张 `sys_admin_*` 表。鉴权：BCrypt(10) + cartisan-security（默认 `SaTokenAuthenticationService`）+ `StpInterface`（Bug ① 后：admin 用默认 loginType，`StpInterface` 无条件返回 admin 权限/角色）+ `@RequirePermission` + `PermissionScanner`。

**超管 (SUPER_ADMIN 角色) — 授权层**:
"谁是超管" = 是否持有 `SUPER_ADMIN` 角色（种子 id=1，V2 内置）。Bug ② 起接通框架 `AuthorizationBypassResolver`：admin 提供 bean 委托 `isSuperAdmin`，命中即跳过 `@RequireRole`/`@RequirePermission`（仍须 `@RequireAuth` 登录）。**授权维度不再用 per-user 标记位**——旧 `system` 列曾把"授权"与"运维韧性"搅在一起，已拆。

**破窗账号 (Break-glass account) — 运维韧性层**:
保证"就算角色被改坏、管理员被删光/禁光，也总有一个救援号能登进来"的**固定账号** = 内置 `admin`（保留 ID = 1）。不可删、不可禁、可改密；授权仍走它挂的 `SUPER_ADMIN` 角色。识别方式：**按保留 ID**（`BREAK_GLASS_ADMIN_ID = 1`），不靠列。`system` 列**删除**（V4 迁移）——其原"内置不可删"语义改由"保留 ID = 破窗号"承载；原 `count()<=1` last-admin 检查随之删除（破窗号永在，该规则成死逻辑）。
_Avoid_: 给 `system` 列塞"系统管理员"的授权含义；把破窗号设计成随角色成员漂移的"最后一个超管"规则（脆、难文档化）。

**菜单树与节点类型 (Menu tree & MenuType)**:
导航是一棵树（`AdminMenu`，`parentId` 组装，`MAX_DEPTH=3`）。**节点的 `type` 描述"这个节点怎么渲染"，与深度正交**——深度由树结构推出（前端据深度决定"画成一级图标 / 二级面板项"，据 `type` 决定"分组头 / 可路由叶子 / 分隔线"组件）。三型：`MENU`(1)=可路由叶子、`path` 必填；`GROUP`(2)=分组容器/小节标题、`path` 空、有子节点；`DIVIDER`(3)=**同级分隔线（非容器）**——作兄弟节点插入、仅靠 `parentId`+`sortOrder` 定位、无 `path`、无子、`name` 仅作维护备注不渲染。可见性派生（过滤层职责，非聚合）：GROUP 仅当 ≥1 子节点可见才显示，DIVIDER 仅当存在可见邻居才显示——按权限裁剪菜单树时一并裁掉空 GROUP 与悬空 DIVIDER（已实现于 `MenuTreeAssembler`：祖先链补全 + 每层按 `sortOrder` 排序 + 裁剪；DIVIDER 不分配、按结构自动纳入，仅靠有无可见邻居决定去留）。不变量（已在聚合 `AdminMenu.applyTypeAndPath` 强制，错误码 `ADMIN_014_3`）：MENU 必有非空 path；GROUP/DIVIDER 的 path 为 null。
_Avoid_: 把 `type` 当层级标识（GROUP≠"一级"、MENU≠"二级"）；把 DIVIDER 做成"挂在下一个节点上的 dividerBefore 标志位"（CRUD 时不直观、排序别扭、违背"维护时可读"，已否决）。

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
- ✅ Bug ② 超管 bypass — 已落地（commit `9db013b`）：`SaTokenConfig` 提供 `AuthorizationBypassResolver` bean 委托 `isSuperAdmin`。见 [ADR-0002](docs/adr/0002-super-admin-bypass-is-framework-gap.md)
- ✅ Bug ③ 内置账号保护 — **已落地**（2026-07-28）：拆"授权 vs 韧性"；超管 = 角色（已有）；破窗号 = 固定 `admin`(id=1) 不可删/不可禁/可改密；**删 `system` 列**（V4）、按保留 ID 识别；删 `count()<=1` 死逻辑；`assignRoles` 不许从破窗号移除 `SUPER_ADMIN`。见 [ADR-0003](docs/adr/0003-break-glass-reserved-id.md)。spec：[`.scratch/phase0-completion/spec.md`](.scratch/phase0-completion/spec.md)。
- ✅ Bug ④ 登录未写 `SaSession.userName` — **框架根因已修 + admin 已迁移**：cartisan-boot commit `efcb1e7` 破坏性补全 `login` 签名（两个重载各加 `String userName`、由框架写入 session、删旧重载）；admin 已迁到新签名（传昵称）、删除 `StpUtil.getSession().set(...)` 临时补丁（admin 不再直接依赖 `StpUtil`）。见 [`.scratch/phase0-completion/issues/03`](.scratch/phase0-completion/issues/03-migrate-to-framework-login-signature.md)。

### Phase 1 — Operator 模型补全
- ⏳ 部门 / 岗位模型（树？数据权限挂钩？）
- ⏳ 权限模型：注解扫描 + 列存 vs 独立 `sys_permissions` 字典表

### Phase 2 — 财务上下文 + 各能力域聚合
- ⏳ 财务首批视图 + 与各域取数契约
- ⏳ cartisan-openapi 签名客户端

### Issue 处置（前端 aieducenter-admin-web 提的需求）
- ✅ **[REQ-1] `MenuResponse` 补 `type`**：模型已定——单棵树 + 每节点 `type`（见上「菜单树与节点类型」），DIVIDER 用 fake-row（非 dividerBefore 标志位）。**已实现**（契约 issue #1 选 A：后端兜底排序+裁剪、前端 naive 渲染）：加 `type` 字段（`8c982ad`）+ 聚合内 path/type 不变量（`8ce8730`，`ADMIN_014_3`）+ 修 `findTree`（`00f1893`：按 `sortOrder` 排序、祖先链补全、裁空 GROUP/悬空 DIVIDER）+ 分隔线按结构自动出现（`5ee29ea`）。issues #4/#5/#6 已关闭。对齐评论：[issue #1](https://github.com/Zhangcolin/aieducenter-admin/issues/1#issuecomment-5102496878)。
- **[REQ-3] `/auth/captcha`**：**不做**（内部员工后台无 botnet 撞库场景，图形验证码收益≈0、徒增真人摩擦；内部账号安全靠 BCrypt + 账号锁定 + 内网访问控制）。已从 `application.yml` 放行名单删占位、关闭 [issue #2](https://github.com/ZhangColin/aieducenter-admin/issues/2)（won't fix）。未来登录防自动化走「失败 N 次锁定 + IP 限流」，非图形码。

## ADR

设计决策记录在 [`docs/adr/`](docs/adr/)（随讨论建立）。
