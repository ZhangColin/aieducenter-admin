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
保证"就算角色被改坏、管理员被删光/禁光，也总有一个救援号能登进来"的**固定账号** = 内置 `admin`（保留 ID = 1）。不可删、不可禁、可改密；授权仍走它挂的 `SUPER_ADMIN` 角色——而**`SUPER_ADMIN` 角色自身也不可禁/不可删**（REQ-10 补全，2026-07-31；错误码 `SUPER_ADMIN_CANNOT_DISABLE/DELETE`，仿破窗号 guard），否则破窗号虽在、救援角色失效仍会锁死。识别方式：**按保留 ID**（`BREAK_GLASS_ADMIN_ID = 1`），不靠列。`system` 列**删除**（V4 迁移）——其原"内置不可删"语义改由"保留 ID = 破窗号"承载；原 `count()<=1` last-admin 检查随之删除（破窗号永在，该规则成死逻辑）。
_Avoid_: 给 `system` 列塞"系统管理员"的授权含义；把破窗号设计成随角色成员漂移的"最后一个超管"规则（脆、难文档化）。

**菜单 = Soybean 路由生成器数据源 (Menu = Soybean route-generator source)**:
菜单的**唯一事实源是前端 Soybean Admin 的路由生成器**（`@elegant-router`）——后端菜单模型**完全照 Soybean**，本项目原 `MENU/GROUP/DIVIDER` 三值模型**作废、当不存在**（2026-07-31 拍板："选了 Soybean 做后台就是完全配套"）。这推翻的正是几天前 REQ-1 刚交付的三值模型——见 [ADR-0004](docs/adr/0004-menu-model-follows-soybean.md)。`AdminMenu` 承载 Soybean 路由生成所需全部元数据：`menuType` 两值 `directory(1)`（容器/路由前缀）/ `menu(2)`（叶子页）；外加 `routeName`、`component`（`layout.<L>$view.<P>` 编码）、`i18nKey`、`icon`+`iconType`（`1`=iconify / `2`=local svg）、`sortOrder`（Soybean 叫 `order`，后端保留 `sortOrder` 不改——避 PG 保留字、前端适配）、`keepAlive`/`constant`/`multiTab`/`hideInMenu`、`activeMenu`、`href`、`fixedIndexInTab`、`query`、`buttons`（见 REQ-9）、`status`。**type/path/icon 等一切语义以 Soybean 源码为准，后端不再自创不变量**——旧 `applyTypeAndPath`/`ADMIN_014_3` path 规则、`MenuTreeAssembler` 的 DIVIDER 裁剪逻辑、REQ-6 的 Material Symbols 图标约定**全部作废**；V6 迁移重建种子菜单（V5 的 GROUP 结构作废）。
端点：树 `GET /menus`（父级选择器/角色分配用，保留）+ 扁平分页 `GET /menus/page`（Soybean 菜单表格用，新增）；页名选择器（`fetchGetAllPages`）由前端构建期派生，不向后端要。
_Avoid_: 在菜单模型上保留任何"Soybean 没有"的遗物（DIVIDER、旧 path 不变量、Material Symbols）；把 Soybean 既定的 type/icon 语义当开放项重新讨论。

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
- ✅ **[REQ-1] `MenuResponse` 补 `type`** ⚠️ **已被 REQ-8 推翻**（三值→Soybean 两值，见 [ADR-0004](docs/adr/0004-menu-model-follows-soybean.md)）｜原模型：单棵树 + 每节点 `type`（见上「菜单树与节点类型」），DIVIDER 用 fake-row（非 dividerBefore 标志位）。**已实现**（契约 issue #1 选 A：后端兜底排序+裁剪、前端 naive 渲染）：加 `type` 字段（`8c982ad`）+ 聚合内 path/type 不变量（`8ce8730`，`ADMIN_014_3`）+ 修 `findTree`（`00f1893`：按 `sortOrder` 排序、祖先链补全、裁空 GROUP/悬空 DIVIDER）+ 分隔线按结构自动出现（`5ee29ea`）。issues #4/#5/#6 已关闭。对齐评论：[issue #1](https://github.com/Zhangcolin/aieducenter-admin/issues/1#issuecomment-5102496878)。
- **[REQ-3] `/auth/captcha`**：**不做**（内部员工后台无 botnet 撞库场景，图形验证码收益≈0、徒增真人摩擦；内部账号安全靠 BCrypt + 账号锁定 + 内网访问控制）。已从 `application.yml` 放行名单删占位、关闭 [issue #2](https://github.com/ZhangColin/aieducenter-admin/issues/2)（won't fix）。未来登录防自动化走「失败 N 次锁定 + IP 限流」，非图形码。

- 🚧 **[REQ-8~12] 对齐 Soybean 系统管理**（[issue #11](https://github.com/ZhangColin/aieducenter-admin/issues/11)，进行中）：菜单/角色/用户全面对齐 Soybean admin-web。**已定**——菜单完全照 Soybean（原三值模型作废，见上「菜单」术语 + [ADR-0004](docs/adr/0004-menu-model-follows-soybean.md)）；**搁置**——按钮权限(REQ-9) 与设想不一致，等用户/角色/菜单核心全部做完再统一与前端对接（含 menu `buttons` 字段与角色→按钮端点）；**已定(REQ-10)**——角色加 `status`（Soybean 都有，照加）；**`SUPER_ADMIN` 角色自身不可禁用/不可删除**（补全破窗韧性最后一环，见上「破窗账号」+ [ADR-0003](docs/adr/0003-break-glass-reserved-id.md) 修订）；普通角色禁用语义跟 Soybean。
**搁置**——REQ-12 `AuditorAware<Long>`（视为前端过度设计，等其余功能就绪再单独过审计字段；此前 createdBy/updatedBy 出站暂缓，`createdAt/updatedAt` 可先出）。
**已定(REQ-8 命名)**——DB 列 + Java 字段 + JSON 契约**对齐 Soybean 命名**（`menu_name/route_path/menu_type/i18n_key/...`，V6 重建菜单表反正要动）；**唯一例外 `sortOrder` 保留、不改为 Soybean 的 `order`**（避 PostgreSQL 保留字）——前端 `order` 适配回 `sortOrder`；索引名按 Soybean 名称。
**待核实已清**——issue #11 的"用户列表 total=2 但无行"+ 三聚合（User/Role/Menu）软删读过滤一致性，此前已解决（commit `3bc447a`/`b5c0d0b`，REQ-5 软删读过滤），无需再查。

## ADR

设计决策记录在 [`docs/adr/`](docs/adr/)（随讨论建立）。
