# 平台账号管理 BFF 实现 spec（消费 identity 契约，源 #49 / identity #66）

## Problem Statement

统一后台（admin）目前**没有任何终端用户账号管理面**。运营人员（Operator）无法在后台：搜索/查看平台终端用户账号、封号/解封、解除系统锁定、或强制下线。终端用户账号（Account）的身份与凭据归 identity 域，对操作员没有可见的管理入口。

后果：违规账号无人封禁、被锁用户无法运营介入、可疑会话无法强制下线。本 spec 在 admin 内建 **account admin BFF**，把 identity 的账号管理能力以**运营可操作**的形态聚合到统一后台。

## Solution

在 admin 现有单上下文内新增 **`account` 子包**（账号管理上下文），作为 BFF：admin 以自身 `admin-console` 身份经 cartisan-openapi 签名调用 identity 的 `/api/account/*` 管理端点，对前端暴露 `/api/admin/accounts/**` 一组端点，种 V14 菜单（「账号管理」目录 + 1 叶子「账号列表」），按权限码控写操作，写请求透传当前 Operator 身份。**业务逻辑、凭据与审计归 identity**（system of record），admin 只做调接口 + DTO 转换 + 聚合（见 [ADR-0007](../../docs/adr/0007-admin-bff-outbound-clients-are-flat-components.md)：BFF 出站客户端为裸 `@Component`）。

**密码边界（[ADR-0008](../../docs/adr/0008-admin-does-not-handle-end-user-passwords.md)）**：admin **不处理终端用户密码**——identity 提供的 3 个密码端点（reset-password / clear-password / force-change-password）**一个都不暴露**。admin 只消费 identity 的**账号状态 + 会话**类端点（6 个）。密码生命周期完全归 identity。

## User Stories

**读 · 列表**
1. 作为 Operator，我想分页搜索平台账号（email / phone / userId / status / locked / 注册时间区间），以便定位具体账号。
2. 作为 Operator，我想让列表分页请求/响应形状与 admin 现有列表端点（`/apps`、`/payments`）一致，以便前端统一处理。

**读 · 详情**
3. 作为 Operator，我想以**抽屉/弹窗**查看账号管理详情（状态/锁定/资料等 identity 返回字段），以便了解账号全貌（详情不种菜单，前端抽屉）。

**写 · 账号状态**
4. 作为持有写权限的 Operator，我想**封号**一个账号（带 reason，identity 自动踢所有会话），以止损违规账号。
5. 作为持有写权限的 Operator，我想**解封**一个已封号账号。
6. 作为持有写权限的 Operator，我想**解除系统锁定**（unlock——解除登录失败次数等触发的系统锁，区别于封号状态）。

**写 · 会话**
7. 作为持有写权限的 Operator，我想**强制下线**一个账号（revoke 全部会话，不改账号状态），以便紧急止损可疑登录。

**权限**
8. 作为超管（SUPER_ADMIN），我想自动看到「账号管理」全树（经框架 bypass），无需显式角色-菜单分配。
9. 作为无写权限的 Operator，我想被禁止状态/会话操作（端点 403 / 前端按钮隐藏），但仍能搜索/看详情。
10. 作为只读 Operator，我想能访问列表/详情，但看不到任何写操作按钮。

**集成 / 基础设施**
11. 作为 admin 系统，我想以 `admin-console` 身份对出站调用签名，以便 identity 验签识别我（复用既有凭据，与 payment / app-registry 同款）。
12. 作为 admin 系统，我想把当前 Operator 身份经 `RequestContext`→`X-User-Id / X-User-Name` 透传，以便 identity 审计记录「哪个运营操作了谁」。
13. 作为 admin 系统，我想把 identity 的错误翻译成 admin 的 `ADMIN_*` 错误码（携带正确 HTTP 状态），以便前端拿到一致错误。

## Implementation Decisions

**上下文与边界**
- 新增 `account` 子包（`com.aieducenter.admin.account`），落 admin 现有单上下文；不引入多上下文拆分。
- **账号管理上下文 ≠ Operator 管理（系统管理）**：管的是终端用户账号（Account，凭据在 identity），不是后台员工（Operator，凭据在本应用）。
- admin 角色 = BFF（调接口 + DTO 转换 + 聚合），不持业务逻辑、不持账号数据、不记业务审计。

**密码边界（[ADR-0008](../../docs/adr/0008-admin-does-not-handle-end-user-passwords.md)）**
- identity 9 个 `/api/account/*` 端点中，admin **只消费 6 个**（搜索 / 管理详情 / disable / activate / unlock / sessions-revoke）；**3 个密码端点不暴露**（reset-password / clear-password / force-change-password）。未来工程师勿当遗漏去「补」，要接须另立 ADR 推翻 ADR-0008。

**菜单种子 V14**（新 Flyway 迁移，`ON CONFLICT (id) DO NOTHING`，不写种子快照测试，SUPER_ADMIN 经 bypass 见全树、无 role-menu 行）：

| 菜单 | route_name | route_path | component | icon | i18n_key | menu_type | sort_order | parent_id |
|---|---|---|---|---|---|---|---|---|
| 账号管理 | `account` | `/account` | `layout.base` | `carbon:user` | `route.account` | directory(1) | 4 | NULL |
| 账号列表 | `account_list` | `/account/list` | `view.account_list` | `carbon:user-multiple` | `route.account_list` | menu(2) | 1 | (账号管理 id) |

ID：directory=81；leaf=150（避已用 10/20/30/40/50/60/70/80/90-已删/100-140；最终 id 见迁移）。账号详情**不种菜单**（前端抽屉/弹窗，见 `CONTEXT.md`「详情页 = 弹窗/抽屉优先」）。菜单模型照 Soybean（[ADR-0004](../../docs/adr/0004-menu-model-follows-soybean.md)）。route_name / component / icon 为提案，admin-web UI issue 对齐。

**出站客户端**
- 新增 `AccountClient`（infrastructure 裸 `@Component`，镜像 `PaymentClient` / `AppRegistryClient`）：构造注入框架 `OpenApiClient` + identity base-url（`application.yml` 新增 `admin.identity.base-url`），复用框架自动 HMAC-SHA256 签名 + admin-console 身份。不走 `@Port/@Adapter`（ADR-0007）。
- **凭据复用**：admin 以 `admin-console` 身份签名（`application.yml` 既有 `cartisan.openapi.self`），identity 向 app-registry 查 secret 验签——无需新凭据、无需新签名客户端。
- **错误翻译**：`OpenApiClientException` → `DomainException`（携带 `ADMIN_*` `CodeMessage`），按下游 HTTP 状态映射（404→404、409→409 等），逐方法 catch-and-rethrow（与 `PaymentClient` / `AppManagementAppService` 一致）。

**端点面**（北向 `/api/admin/accounts/**`）
- 读：`GET /accounts`（分页搜索，透传 identity 查询参数）、`GET /accounts/{userId}/management`（管理详情）。
- 写·状态：`POST /accounts/{userId}/disable`（body `{reason}`）、`POST /accounts/{userId}/activate`、`POST /accounts/{userId}/unlock`。
- 写·会话：`POST /accounts/{userId}/sessions/revoke`（强制下线——无会话列表端点，一键 revoke 全部会话）。
- 分页/筛选契约对齐 admin 现有列表（与 `/apps`、`/payments` 同形）。

**权限码**（controller 方法级 `@RequirePermission`，`admin:<domain>:<action>`；与 payment 同模式，统一处理，REQ-9 button 级搁置）
- `admin:account:read` —— 所有 GET（列表 / 详情）。
- 写权限码预案 `admin:account:write`（全部 4 个写操作：disable/activate/unlock/revoke）；与 payment 权限统一时再定是否按敏感度拆分（如 `admin:account:status` + `admin:account:session`）。前端按 `/auth/current` 的 permission claims 自控按钮显隐。

**操作者身份透传**
- 经框架 `RequestContext.getUserId()/getUserName()`，cartisan-openapi 自动带 `X-User-Id / X-User-Name`（payment 已验证链路通）。零 Sa-Token、零 DB、零新注解。

**审计**
- 业务操作权威审计 = identity 侧审计（identity ADR-0010）。admin **不本地记账**，仅透传身份 + 读回展示。admin 无 DB 迁移（不持任何账号数据），**除 V14 菜单种子外无 schema 变更**。

**不做 dashboard**：identity 这 6 端点全是 per-account 操作 + 搜索，无 stats 端点。v1 不做账号仪表盘；真实需求由后台反向提出（CONTEXT.md「BFF 仪表盘 = 需求驱动」）。

## Testing Decisions

**好测试的原则**：只测外部行为（HTTP 响应 / DTO 形状 / 错误码 / 身份透传契约），不测内部调用序列——**例外**：操作者身份透传（验证 `RequestContext`→出站 header）是契约的一部分，需断言。AssertJ，命名 `given_{条件}_when_{操作}_then_{预期}`。

**主 seam（新，最高）—— account BFF 集成测试**：`@SpringBootTest` + `@MockBean AccountClient`，镜像 `PaymentBffIntegrationTest`。真实 Spring 上下文跑 controller → appservice → client（client mock），一次断言：DTO 映射（identity wire → admin response）、筛选字段映射、分页契约、操作者身份透传、错误翻译（identity 状态码 → `ADMIN_*`）。覆盖全部 6 端点行为。
- 不写 `AccountClient` 直测（与 `PaymentClient` / `AppRegistryClient` 一致：不经 MockWebServer、client bean 直接 mock）。
- 不写 controller standalone 单测（集成测试已更高位覆盖）。

**复用现有 seam（无新增）**：
- **菜单种子 V14**：`RoleAssignmentFlywaySchemaIntegrationTest` 模式——独立 PG schema + Flyway + `ddl-auto=none`，验迁移落库。
- **权限强制**：`RbacEnforcementIntegrationTest` 模式——真实 Sa-Token 过滤链，断言 account 端点 `@RequirePermission` 对无权者 403。

## Out of Scope

- **admin-web 前端页面**（菜单路由元数据已定、详情走抽屉、按钮按 claims 显隐）—— spec 契约锁定后另发 admin-web issue（见 `admin-web-sync.md`）。
- **identity 服务侧实现**（identity #67–#72）—— 本 spec 是消费视角，最终字段以 identity 实现契约为准。
- **3 个密码端点**（reset / clear / force-change-password）—— admin 不处理终端用户密码（[ADR-0008](../../docs/adr/0008-admin-does-not-handle-end-user-passwords.md)）。
- **账号仪表盘 / 统计** —— identity 无 stats 端点；需求由后台反向提。
- **admin 本地账号表 / 审计** —— 账号数据与审计归 identity，admin 不持。
- **创建账号 / 注册** —— identity 域职责。
- **会话列表视图** —— identity 仅暴露 revoke、无 list 端点；强制下线为一键 revoke 全部会话按钮。

## Further Notes

- **契约源**：admin [issue #49](https://github.com/ZhangColin/aieducenter-admin/issues/49) / identity [spec #66](https://github.com/ZhangColin/aieducenter-identity/issues/66)。各端点最终字段以 identity #67–#72 实现契约为准。
- **联调前提**：identity #67–#72 落地（尤其 frontier #67 gate+详情、#70 搜索）后方可联调；**设计与 BFF 搭建可先于其进行**（client / appservice 可先写、用 mock 验，待 identity 就绪再接真）。
- **术语**：见 [`CONTEXT.md`](../../CONTEXT.md)「平台账号 (Account)」「账号管理上下文」「admin 是 BFF」「BFF 不记业务审计」「详情页 = 弹窗/抽屉优先」。
- **相关 ADR**：[0004 菜单照 Soybean](../../docs/adr/0004-menu-model-follows-soybean.md)、[0007 BFF 出站客户端裸 @Component](../../docs/adr/0007-admin-bff-outbound-clients-are-flat-components.md)、[0008 admin 不处理终端用户密码](../../docs/adr/0008-admin-does-not-handle-end-user-passwords.md)。
- **Ticket 拆分**（依赖序，挂本 spec 为 parent = #49）：T1 菜单种子 V14 + AccountClient 地基 + 账号列表（tracer bullet）/ T2 管理详情 + 状态操作（封号 / 解封 / 解锁）/ T3 强制下线。T1 无依赖可先行；T2 / T3 依赖 T1；admin-web issue 依赖本 spec 契约锁定。
