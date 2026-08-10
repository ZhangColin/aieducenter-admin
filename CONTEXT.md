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
**用户菜单/权限 = 其全部启用角色的汇总（并集）**；**禁用角色（`AdminRoleStatus.DISABLED`）在任何汇总聚合中视为不存在**——不贡献菜单、不参与 home 推导、不贡献权限 claims 与 roleCodes（REQ-13 定，2026-08-02：`getRoleCodes`/`getPermissions`/导航聚合（现 `getMyMenus`）同路径一次修齐；此前禁用角色零消费、照样发菜单发权限）。超管不受影响（`SUPER_ADMIN` 角色自身不可禁用，REQ-10）。
**删除 = 物理删除**（ADR-0005，REQ-14 定，2026-08-03）：三聚合基类 `Auditable`（非 `AuditableSoftDeletable`），软删在本应用无恢复需求、只带来残留泄漏面（REQ-5 读过滤 / #21 汇总过滤 / REQ-14 删除守卫三度泄漏）；「停用」语义由 `status` 承载。关联行经 JPA `cascade = ALL + orphanRemoval` + 生产 FK `ON DELETE CASCADE` 双保险级联清除。删除守卫（破窗号 / `SUPER_ADMIN` 角色）挂显式领域方法 `requireDeletable()`，由 AppService 在 `repository.delete()` 之前调用。

**超管 (SUPER_ADMIN 角色) — 授权层**:
"谁是超管" = 是否持有 `SUPER_ADMIN` 角色（种子 id=1，V2 内置）。Bug ② 起接通框架 `AuthorizationBypassResolver`：admin 提供 bean 委托 `isSuperAdmin`，命中即跳过 `@RequireRole`/`@RequirePermission`（仍须 `@RequireAuth` 登录）。**授权维度不再用 per-user 标记位**——旧 `system` 列曾把"授权"与"运维韧性"搅在一起，已拆。

**破窗账号 (Break-glass account) — 运维韧性层**:
保证"就算角色被改坏、管理员被删光/禁光，也总有一个救援号能登进来"的**固定账号** = 内置 `admin`（保留 ID = 1）。不可删、不可禁、可改密；授权仍走它挂的 `SUPER_ADMIN` 角色——而**`SUPER_ADMIN` 角色自身也不可禁/不可删**（REQ-10 补全，2026-07-31；错误码 `SUPER_ADMIN_CANNOT_DISABLE/DELETE`，仿破窗号 guard），否则破窗号虽在、救援角色失效仍会锁死。识别方式：**按保留 ID**（`BREAK_GLASS_ADMIN_ID = 1`），不靠列。`system` 列**删除**（V4 迁移）——其原"内置不可删"语义改由"保留 ID = 破窗号"承载；原 `count()<=1` last-admin 检查随之删除（破窗号永在，该规则成死逻辑）。
_Avoid_: 给 `system` 列塞"系统管理员"的授权含义；把破窗号设计成随角色成员漂移的"最后一个超管"规则（脆、难文档化）。

**菜单 = Soybean 路由生成器数据源 (Menu = Soybean route-generator source)**:
菜单的**唯一事实源是前端 Soybean Admin 的路由生成器**（`@elegant-router`）——后端菜单模型**完全照 Soybean**，本项目原 `MENU/GROUP/DIVIDER` 三值模型**作废、当不存在**（2026-07-31 拍板："选了 Soybean 做后台就是完全配套"）。这推翻的正是几天前 REQ-1 刚交付的三值模型——见 [ADR-0004](docs/adr/0004-menu-model-follows-soybean.md)。`AdminMenu` 承载 Soybean 路由生成所需全部元数据：`menuType` 两值 `directory(1)`（容器/路由前缀）/ `menu(2)`（叶子页）；外加 `routeName`、`component`（`layout.<L>$view.<P>` 编码）、`i18nKey`、`icon`+`iconType`（`1`=iconify / `2`=local svg）、`sortOrder`（Soybean 叫 `order`，后端保留 `sortOrder` 不改——避 PG 保留字、前端适配）、`keepAlive`/`constant`/`multiTab`/`hideInMenu`、`activeMenu`、`href`、`fixedIndexInTab`、`query`、`buttons`（见 REQ-9）、`status`。**type/path/icon 等一切语义以 Soybean 源码为准，后端不再自创不变量**——旧 `applyTypeAndPath`/`ADMIN_014_3` path 规则、`MenuTreeAssembler` 的 DIVIDER 裁剪逻辑、REQ-6 的 Material Symbols 图标约定**全部作废**；V6 迁移重建种子菜单（V5 的 GROUP 结构作废）。
端点：扁平分页 `GET /menus`（Soybean 菜单表格用，集合根分页、对齐 `/users`/`/roles`）+ 树 `GET /menus/tree`（父级选择器/角色分配用）；页名选择器（`fetchGetAllPages`）由前端构建期派生，不向后端要。
_Avoid_: 在菜单模型上保留任何"Soybean 没有"的遗物（DIVIDER、旧 path 不变量、Material Symbols）；把 Soybean 既定的 type/icon 语义当开放项重新讨论。

**财务上下文 (Finance Context)**:
本应用内的一个**限界上下文（非独立域/服务）**。只读各能力域（支付/钱包/Token计量）做**收入确认（consume-based，履约时点）+ append-only 冲销 + 负债/营销费用视角**。**不收款（支付域）、不持余额（钱包域）、不计量 token（Token计量域）**。详见架构仓库 architecture.md §6.15。

**聚合 / 签名调用 (Aggregation via signed calls)**:
前端不直连各能力域；统一后台经 **cartisan-openapi 签名调用**聚合各域（支付/钱包/模型网关/Token计量/…），对前端呈现统一视图。

**应用注册中心 (App Registry)**:
独立服务 `aieducenter-app-registry`（端口 8088），管理平台所有对接的外部应用/服务。Admin 通过 cartisan-openapi 签名调用其 API。Admin 自身的 apiKey=`admin-console`，apiSecret 已配置。核心聚合：`RegisteredApp`（应用信息）、`ApiKey`（apiKey/apiSecret，AES-GCM 可逆加密）、`SsoClient`（OIDC SSO 客户端，client_secret argon2 单向哈希）。Admin 的 BFF 层聚合这三个聚合的查询/维护为单一「应用管理」页面。

**应用管理菜单 (App Management Menu)**:
新增一级目录「应用管理」（`directory`, sort_order=2），位于首页之后、系统管理之前。其下二级菜单「应用」（`menu`）。系统管理 sort_order 同步调整为 99（原 9），为未来目录留空间。元数据已定：

| 菜单 | route_name | route_path | component | icon | i18n_key | menu_type | sort_order | parent_id |
|------|-----------|------------|-----------|------|----------|-----------|------------|-----------|
| 应用管理 | `app` | `/app` | `layout.base` | `carbon:application` | `route.app` | directory(1) | 2 | NULL |
| 应用 | `app_list` | `/app/list` | `view.app_list` | `carbon:application` | `route.app_list` | menu(2) | 1 | (应用管理id) |

**应用管理 BFF 设计**:
Admin 作为 BFF，前端只访问 admin，不对 app-registry 直连。Admin 透传 + 聚合调用 app-registry（经 cartisan-openapi 签名）。

- `appCode` = `apiKey`（二者一致）
- `apiSecret`：无则生成，有则重置
- 业务逻辑全在 app-registry，admin 不做业务判断
- 端点（`/api/admin/apps`）：列表、详情（聚合 app+apiKey+ssoClient）、创建、**更新应用信息**（name/description）、管理 apiKey（生成/重置）、管理 ssoClient（**凭证 create/reset + 配置 PUT + 启停用**，三独立原语，issue #33）、应用启停用 — 各区块独立保存
- 对 app-registry 的依赖 issue：[#14 列表接口](https://github.com/ZhangColin/aieducenter-app-registry/issues/14) / [#15 更新接口](https://github.com/ZhangColin/aieducenter-app-registry/issues/15)

**应用详情页布局**（单页三区块，各独立保存）：
1. 应用基本信息：appCode(只读) + name(编辑) + description(编辑) + status(启停按钮)
2. API Key：apiKey(只读,=appCode) + apiSecret(生成/重置后展示一次) + 生成/重置按钮
3. SSO Client(可选)：clientId(只读) + status(启停按钮) + redirectUris(编辑) + postLogoutRedirectUris(编辑) + scopes/grants(编辑) + clientSecret(开通/重置后展示一次) + 开通/配置/重置按钮（首次须先开通（凭证）再配置——PUT 在 SsoClient 未建时 404）

（2026-08-04 开始讨论；2026-08-10 issue #33：region 3 拆为 开通/配置/重置 三动作 + 加 postLogoutRedirectUris + SsoClient status 启停）

**SSO 凭证 (Credential) vs 配置 (Config)**:
SsoClient 管理拆为两个独立原语（对齐 app-registry [ADR-0005](../aieducenter-app-registry/docs/adr/0005-sso-client-config-credential-separation.md)，2026-08-10、issue #33）：**凭证** = `client_id` + `client_secret` 对（`client_id` 终身稳定、创建时生成一次；`client_secret` 仅创建/重置时一次性返明文、余皆 argon2 hash-only）→ 凭证接口（create-or-reset，无 body）；**配置** = `redirectUris` / `postLogoutRedirectUris` / `scopes` / `grants` → 配置 PUT（整份替换，不动凭证、不动 status，无 SsoClient 则 404）。首次开通 = 先凭证后配置。admin 北向两独立端点镜像下游、纯透传、**不自动建凭证**（否则一次性明文 secret 会被静默丢弃）。
_Avoid_: 把凭证与配置混在一个端点（旧 `createOrRotate` 已废）、配置改动连换凭证；用"轮换"指代 SSO 凭证更新。

**重置 (Reset) vs 轮换 (Rotate)**:
**重置** = 仅重新生成 `client_secret`（`client_id` 不变）；**轮换** = `client_id`+`client_secret` 同时换——SSO 适配后**全链路不再使用**（`client_id` 终身稳定；OIDC 约定 `client_id` 稳定、`client_secret` 才是密钥）。apiKey 的"无则生成/有则重置"同为 reset 语义。
_Avoid_: 用"轮换"指代 SSO 凭证或 apiKey 更新。

**消费面 vs 管理面 (Consumption side vs Management side)**:
同一资源的两种服务视角（REQ-13 定，2026-08-02）。**消费面** = 终端使用视角（我的导航、动态路由），只下发**启用**数据、登录即可访问（不挂管理权限）、禁用项不下发且 directory 禁用整棵子树不下发——对超管同样生效；**管理面** = 维护视角（菜单/角色管理页），全量含禁用项、挂 `@RequirePermission`。例：`GET /menus/my` 是消费面；`GET /menus`、`GET /menus/tree` 是管理面。身份 claims（user/roleCodes/permissions）留 auth 域（`/auth/current`），导航资源（menus/home）归 menu 域——身份 vs 导航不混在一个响应里。
_Avoid_: 给消费面端点挂管理权限注解；让消费面为了"超管全量"而连禁用项也下发；在管理面端点上做消费面过滤。

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
- ✅ **[REQ-13] 「我的导航」端点 `GET /menus/my` + `/auth/current` 移除 menus**（[issue #20](https://github.com/ZhangColin/aieducenter-admin/issues/20)，spec 已发布于 issue 评论；拆票线性链 [#21](https://github.com/ZhangColin/aieducenter-admin/issues/21) ✅ 禁用角色汇总剔除 → [#22](https://github.com/ZhangColin/aieducenter-admin/issues/22) ✅ `/menus/my` 端点 → [#23](https://github.com/ZhangColin/aieducenter-admin/issues/23) ✅ `/auth/current` 移除 menus）：前端翻 `VITE_AUTH_ROUTE_MODE=dynamic`，按「身份 vs 导航」拆域——`/auth/current` 收敛为 `{user, roleCodes, permissions}`（身份 claims 留 auth 域）；新增 `/menus/my` 出 `{home, menus}`（导航资源归 menu 域，登录即可、不挂 `@RequirePermission`，贴 Soybean `UserRoute` 原生形状 `{routes, home}`）。消费面只下发**启用（status=1）菜单，directory 禁用则整棵子树不下发**（修原 `/auth/current.menus` 不过滤 status=0 之弊；T2 落地于 `MenuTreeAssembler.assembleVisible`——可见 = 自身启用 ∧ 祖先链全启用，在 id 集合层过滤故幸存子节点不提升到根；超管走 `findVisibleTree(null)`，与管理面 `findTree(null)` 语义分叉）；管理面 `GET /menus`、`GET /menus/tree` 保持全量。**已定①**——禁用角色在任何汇总聚合中视为不存在（见上「RBAC」条目）；**已定②**——`home` = 用户全部启用角色按 `(sortOrder 升, id 升)` 取第一个非空白 home，全空 → `null`（超管同规则，种子 `SUPER_ADMIN.home`=NULL，前端兜底第一个可见叶子）；**已定③**——status 过滤对超管同样生效：「超管全量」= 不受角色裁剪的全量**启用**菜单，禁用项仅管理面可见（否则禁用开关对超管形同虚设、动态路由会把禁用页注册成可访问路由）。
- ✅ **[REQ-1] `MenuResponse` 补 `type`** ⚠️ **已被 REQ-8 推翻**（三值→Soybean 两值，见 [ADR-0004](docs/adr/0004-menu-model-follows-soybean.md)）｜原模型：单棵树 + 每节点 `type`（见上「菜单树与节点类型」），DIVIDER 用 fake-row（非 dividerBefore 标志位）。**已实现**（契约 issue #1 选 A：后端兜底排序+裁剪、前端 naive 渲染）：加 `type` 字段（`8c982ad`）+ 聚合内 path/type 不变量（`8ce8730`，`ADMIN_014_3`）+ 修 `findTree`（`00f1893`：按 `sortOrder` 排序、祖先链补全、裁空 GROUP/悬空 DIVIDER）+ 分隔线按结构自动出现（`5ee29ea`）。issues #4/#5/#6 已关闭。对齐评论：[issue #1](https://github.com/Zhangcolin/aieducenter-admin/issues/1#issuecomment-5102496878)。
- **[REQ-3] `/auth/captcha`**：**不做**（内部员工后台无 botnet 撞库场景，图形验证码收益≈0、徒增真人摩擦；内部账号安全靠 BCrypt + 账号锁定 + 内网访问控制）。已从 `application.yml` 放行名单删占位、关闭 [issue #2](https://github.com/ZhangColin/aieducenter-admin/issues/2)（won't fix）。未来登录防自动化走「失败 N 次锁定 + IP 限流」，非图形码。

- ✅ **[REQ-8~12] 对齐 Soybean 系统管理**（[issue #11](https://github.com/ZhangColin/aieducenter-admin/issues/11)，已关闭）：菜单/角色/用户全面对齐 Soybean admin-web。**已定**——菜单完全照 Soybean（原三值模型作废，见上「菜单」术语 + [ADR-0004](docs/adr/0004-menu-model-follows-soybean.md)）；**搁置**——按钮权限(REQ-9) 与设想不一致，等用户/角色/菜单核心全部做完再统一与前端对接（含 menu `buttons` 字段与角色→按钮端点；已拆出 [issue #18](https://github.com/ZhangColin/aieducenter-admin/issues/18) 独立追踪，核心已全部做完、搁置前提已满足，待重启对齐）；**已定(REQ-10)**——角色加 `status`（Soybean 都有，照加）；**`SUPER_ADMIN` 角色自身不可禁用/不可删除**（补全破窗韧性最后一环，见上「破窗账号」+ [ADR-0003](docs/adr/0003-break-glass-reserved-id.md) 修订）；普通角色禁用语义跟 Soybean。
**搁置**——REQ-12 `AuditorAware<Long>`（视为前端过度设计，等其余功能就绪再单独过审计字段；此前 createdBy/updatedBy 出站暂缓，`createdAt/updatedAt` 可先出）。
**已定(REQ-8 命名)**——DB 列 + Java 字段 + JSON 契约**对齐 Soybean 命名**（`menu_name/route_path/menu_type/i18n_key/...`，V6 重建菜单表反正要动）；**唯一例外 `sortOrder` 保留、不改为 Soybean 的 `order`**（避 PostgreSQL 保留字）——前端 `order` 适配回 `sortOrder`；索引名按 Soybean 名称。
**待核实已清**——issue #11 的"用户列表 total=2 但无行"+ 三聚合（User/Role/Menu）软删读过滤一致性，此前已解决（commit `3bc447a`/`b5c0d0b`，REQ-5 软删读过滤），无需再查。
**已定(REQ-11 用户档案)**——用户档案对齐 Soybean（[issue #17](https://github.com/ZhangColin/aieducenter-admin/issues/17)）：`AdminUser` 加 `gender`（`AdminUserGender` 1 男 / 2 女，整数枚举，V8 迁移加可空 `gender` 列），透传 `Create/UpdateAdminUserCommand` + `AdminUserQuery` + `AdminUserResponse`（+`genderName` 镜像既有 `statusName`）；`AdminUserQuery` 加独立 `phone` 模糊搜索（keyword 仍覆盖 username/nickname/email，phone 独立字段）；**用户列表项内联角色摘要**（裁剪投影 `{id,name,code}`，按本页全部用户角色 ID 批量 `findByIdIn` 一次取齐、内存分组，无 N+1；关联指向的角色已物理删除则不回显，与详情同语义）；`AdminUserResponse` 出 `createdAt/updatedAt`（REQ-12 审计字段先出此二者、`createdBy/updatedBy` 暂缓，见上 REQ-12 搁置条）。
- ✅ **[REQ-14] 三聚合软删 → 物理删除**（[issue #24](https://github.com/ZhangColin/aieducenter-admin/issues/24)，2026-08-03）：角色删除 in-use 守卫被软删用户的残留 `user_roles` 行永阻（403 `ADMIN_013`）——triage 拍板不做查询侧补丁，从根上迁移：`AdminUser`/`AdminRole`/`AdminMenu` 基类 `AuditableSoftDeletable` → `Auditable`，删除即物理 DELETE；`markAsDeleted()` 整体移除（框架对「有该方法但非 SoftDeletable」走反射软存，残留则物理删除失效）；破窗/超管守卫迁为显式 `requireDeletable()` 由 AppService 删除前调用（行为不变：403 + 原错误码）；V9 迁移先 purge 历史软删行再 `DROP COLUMN deleted`（三张主表）；查询侧 `DeletedFalse` 派生查询与 JPQL `r.deleted = false` 全清。见 [ADR-0005](docs/adr/0005-soft-delete-to-physical-delete.md)；框架侧软删文档/约定降级由 [cartisan-boot#10](https://github.com/ZhangColin/cartisan-boot/issues/10) 独立跟踪。
- ⏳ **[REQ-15-T6] 适配 app-registry SsoClient 管理 API 破坏性变更**（[issue #33](https://github.com/ZhangColin/aieducenter-admin/issues/33)，2026-08-10 grill 定稿）：app-registry #22（commit `d9e1444`）删 `createOrRotate` 单端点，拆成**凭证**（`POST .../sso-clients/credentials`，create-or-reset `client_secret`、`client_id` 终身稳定、一次性返明文）+ **配置 PUT**（`PUT .../sso-clients`，整份替换 `redirectUris`/`postLogoutRedirectUris`/`scopes`/`grants`，不动凭证/状态、无 SsoClient→404）+ 启停用。**已定**——admin 北向两端点（`POST .../sso-client/credentials` + `PUT .../sso-client`）+ `enable`/`disable` 镜像下游、纯透传、**不在配置 PUT 自动建凭证**（一次性明文 secret 必须被显式捕获，否则静默丢失）；配置 PUT 的 404 翻译为专属 `ADMIN_SSO_CLIENT_NOT_PROVISIONED`；`postLogoutRedirectUris` 加入 admin 全链路（6 DTO + 详情 region 3）；首次开通由前端编排（凭证→配置），"凭证已建、配置未 PUT"中间态接受（继承 app-registry ADR-0005）；弃用"轮换(rotate)"措辞、`client_id` 终身稳定。无 admin 侧校验、无 DB 迁移。见 [ADR-0006](docs/adr/0006-sso-client-bff-mirrors-credential-config-split.md) + 上「SSO 凭证 vs 配置」「重置 vs 轮换」术语。

## ADR

设计决策记录在 [`docs/adr/`](docs/adr/)（随讨论建立）。
