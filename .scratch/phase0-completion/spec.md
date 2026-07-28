# Spec — Phase 0 收尾：破窗账号保护 + 登录写 userName（Bug ③ + ④）

Status: ready-for-agent
Feature: phase0-completion
关联：CONTEXT.md 决策日志（Bug ③/④）、ADR-0001、ADR-0002、cartisan-boot `.scratch/login-user-name/issues/01`、前序 `.scratch/rbac-enforcement-fix/spec.md`（Bug ①②）

## Problem Statement

统一后台的 RBAC 地基还差两处没修平，"基础功能（用户/角色/菜单/登录）真正能用、可上生产"就差这一步：

- **Bug ③（内置账号无保护 + `system` 列割裂）**：DB 有 `sys_admin_users.system NOT NULL`（无默认值）标记"内置不可删"，但 `AdminUser` 聚合没映射它 → 生产 `ddl-auto=none` 下建任何管理员 INSERT 漏列、命中 NOT NULL 直接 500（测试用 `create-drop` 按实体建表、没这列，所以一直没抓到）；同时 `delete()`/`disable()` 没有"内置账号不可删/不可禁"守卫 → 运营能把破窗救援号 `admin`(id=1) 删掉或禁用、把自己锁死在外。原 `system` 字段还把"系统管理员（授权）"和"内置不可删（韧性）"两个含义搅在一起，语义不清。
- **Bug ④（登录没写 userName）**：登录只建会话、不写 `SaSession.userName` → 之后每个请求 `RequestContext.userName` 恒为 null。今天潜伏（admin 暂不读 userName），一旦加审计/操作日志即踩雷。

从使用者视角：管理员可能建不出来（生产）、或破窗号被删后无救援入口；将来审计/日志拿不到"谁干的（按名）"。

## Solution

- **Bug ③**：把"授权"与"运维韧性"拆开。授权（谁是超管）已由 `SUPER_ADMIN` 角色 + 框架 `AuthorizationBypassResolver` 解决（Bug ② 已落地），不用 per-user 标记位。运维韧性由一个**固定破窗账号**承担——内置 `admin`（保留 ID = 1）不可删、不可禁、可改密。据此：`AdminUser` 聚合加守卫（删/禁破窗号抛领域错误），**删除 `system` 列**（V4 迁移，其"内置不可删"语义改由"保留 ID = 破窗号"承载），删掉因此变死逻辑的 `count()<=1` last-admin 检查。修完实体与 Flyway schema 重新对齐，"生产建管理员 500"随之消失。
- **Bug ④**：admin 登录流程在 `AuthenticationService.login(...)` 之后补写 `StpUtil.getSession().set("userName", 昵称)`，使 `RequestContext.userName` 在后续请求被框架 `SecurityFilter` 正确填充。框架侧根因（`login()` 把会话建立与 userName 写入割裂、逼调用方摸 `StpUtil`）已另提需求 cartisan-boot `.scratch/login-user-name/issues/01`，等框架 triage，不阻塞本 spec。

## User Stories

1. 作为运营，我想新建普通管理员账号能成功，以便把后台交给同事使用。
2. 作为运营，我想能删除/禁用普通管理员账号，以便回收离职同事的访问。
3. 作为运营，我（即使有权限）也不能删除内置破窗号 `admin`，以便救援入口永远存在。
4. 作为运营，我也不能禁用内置破窗号 `admin`，以便不会把自己/平台锁死在外。
5. 作为平台团队，我想破窗号的密码可被重置，以便定期轮换、不卡死在初始密码。
6. 作为平台团队，我想"谁是破窗号"由保留 ID 决定（`admin` = id=1），以便识别简单、无歧义、不靠易被误解的列。
7. 作为平台团队，我想删掉 `system` 列，以便实体与 schema 对齐、消除"生产建管理员 500"的潜伏 bug。
8. 作为平台团队，我想授权继续走 `SUPER_ADMIN` 角色（已有），以便不在 per-user 标记位上重复"谁是超管"。
9. 作为平台团队，我想删掉 `count()<=1` last-admin 检查，以便去掉破窗号永在后已成死逻辑的规则。
10. 作为后续开发者，我想"破窗号不可删/不可禁"守卫在 `AdminUser` 聚合内，以便领域不变量单一执行点、不散落在应用服务。
11. 作为后续开发者，我想 `AdminUserResponse` 暴露"是否破窗号"（按保留 ID 判定），以便前端能标注（如"内置·不可删"）。
12. 作为前端，我想删除/禁用破窗号被拒时收到标准错误体（明确错误码），以便友好提示"内置账号不可删除/禁用"。
13. 作为运营，我登录后系统在会话里记下我的名字，以便后续审计/日志能记录"谁干的（按名）"。
14. 作为平台团队，我想只要登录成功会话就有 userName，以便任何登录路径都不漏写。
15. 作为后续开发者（加审计/操作日志时），我想 `RequestContext.userName` 已被正确填充，以便直接取用、不为 null。
16. 作为平台团队，我想框架侧的根因（login 割裂）已提需求，以便不只是 admin、所有消费方都受益。
17. 作为平台团队，我想 admin 侧修复不被框架 triage 阻塞，以便现在就让 userName 生效。
18. 作为平台团队，我想本次修复有集成测试覆盖关键外部行为，以便回归能被及时发现。
19. 作为平台团队，我想除 `system` 字段移除、`AdminUserResponse` 增加破窗号标识外，对外 API 契约不变，以便前端无感或平滑跟进。
20. 作为后续开发者，我想 CONTEXT.md / ADR 记录"授权 vs 韧性"拆分决策，以便不再回到"`system` 混淆"的老路。

## Implementation Decisions

- **范围**：仅 Phase 0 的 Bug ③（破窗账号保护 + 删 `system` 列）+ Bug ④（登录写 userName）。依据 CONTEXT.md 决策日志。
- **Bug ③ 改动**：
  - `AdminUser` 聚合：加常量 `BREAK_GLASS_ADMIN_ID = 1L`（保留 ID，沿用 V2 种子的保留段 1–10000）；override 软删入口 `markAsDeleted()`——目标 id == 破窗号 → 抛领域错误（如 `SYSTEM_ADMIN_CANNOT_DELETE`）；`disable()` 同理保护（破窗号不可禁）。`enable()` / 改密 / 改名放行。
  - **删除 `system` 字段**：V4 迁移 `ALTER TABLE sys_admin_users DROP COLUMN system;`。实体本就没映射该列；删列后实体↔Flyway schema 重新对齐。
  - `AdminUserManagementAppService.delete()`：去掉 `count()<=1` last-admin 检查（死逻辑）；守卫由聚合承担。`updateStatus(DISABLED)` 对破窗号的拒绝同样由聚合 `disable()` 抛错承担。
  - `AdminUserResponse`：增加"是否破窗号"派生字段（按 `id == BREAK_GLASS_ADMIN_ID` 判定，不入库），供前端标注。
  - 创建管理员 API **不**暴露"破窗号/system"开关——破窗号只由种子保留 ID 决定，新建账号一律是普通账号。
- **Bug ④ 改动**：`AdminUserAuthAppService.login()` 在 `authenticationService.login(adminUser.getId(), timeout)` 之后补 `StpUtil.getSession().set("userName", adminUser.getNickname())`（用昵称，审计可读）。框架侧根因另提（见 Further Notes）。
- **架构决策**：授权（超管）走角色、不走标记位；韧性（救援入口）= 固定破窗号（保留 ID 识别）；领域不变量守卫在聚合内。遵守 CONTEXT.md「破窗账号」「超管」术语。
- **API 契约**：`AdminUserResponse` 增"是否破窗号"派生字段（增量、向后兼容）；删除/禁用破窗号返回既定错误码（标准错误体）；其余契约不变。`system` 列移除属内部 schema、不暴露 API。
- **数据库**：V4 迁移删 `system` 列。无其它 schema 变更。
- **交互**：软删走框架 `BaseRepositoryImpl.delete()` → `entity.markAsDeleted()`，故聚合 override `markAsDeleted()` 即守卫点。

## Testing Decisions

- **好测试的标准**：只测外部行为（HTTP 状态/错误体、会话可见状态），不测内部方法调用顺序。
- **主 seam（单一集成层）**：复用现有 `@SpringBootTest` 集成测试约定（真库 `create-drop` + 真 Spring + Sa-Token + SecurityFilter；对齐 `RbacEnforcementIntegrationTest`）。
  - Bug ③ 场景：删破窗号 → 被拒（非 2xx，错误码命中）；禁用破窗号 → 被拒；建普通管理员 → 成功；删普通管理员 → 成功。
  - Bug ④ 场景：登录后断言会话 `userName` == 登录用户昵称。
- **次 seam（领域单元）**：`AdminUser` 聚合单测——破窗号 `markAsDeleted()` / `disable()` 抛领域错误、普通账号正常。
- **Bug ④ seam 说明（判断点）**：`RequestContext.userName` 被填充这一效果**目前无端点暴露**，故在 login-session seam 直接断言会话（`StpUtil.getSession().get("userName")`），不新建测试专用 controller（沿用 Bug ② spec 约定）。
- **Prior art**：`RbacEnforcementIntegrationTest`（HTTP 外部行为 + 真 DB）、`AdminUserPermissionAppServiceTest`（Mockito 单测）。
- **约定**：测试命名 `given_{条件}_when_{操作}_then_{预期}`；断言用 AssertJ。
- **已知测试基建缝（不在本 spec 范围）**：集成测试用 `create-drop` 而非真 Flyway schema，抓不到"仅生产 schema 可见"的 bug；本 spec 通过删 `system` 列使实体与 schema 重新对齐、消除该具体分裂，"测试改用真 Flyway"作为单独 follow-up。

## Out of Scope

- **框架侧 `login()` 根因修复**：已提 cartisan-boot `.scratch/login-user-name/issues/01`，等框架 triage；本 spec 仅 admin 侧补写 userName。
- **部门 / 岗位模型**（Phase 1）——未 grill，另出 spec。
- **权限模型**（注解扫描+列存 vs `sys_permissions` 字典表）——未 grill。
- **财务上下文 / 各能力域聚合 / BFF 签名客户端**——本轮明确不做（admin 暂无调用其它服务需求）。
- **审计日志 / 操作日志**——未做；Bug ④ 为其铺路（userName 将可用），但审计本身不在范围。
- **前端（admin-web）**：破窗号标注的前端展示——独立前端仓库，本 spec 不涉及。
- **测试改用真 Flyway schema**——单独测试基建 follow-up。
- **超管判定性能优化**——`isSuperAdmin` 按请求查库，对齐 session 缓存的优化留待将来。

## Further Notes

- **里程碑**：本 spec 落地后，Phase 0 四个 RBAC bug 全部修平——"基础功能（用户/角色/菜单/登录）真正能用、可上生产"达成。
- **工作原则**：Bug ④ 体现"框架问题在框架修、不在应用层硬绕"（见 memory `framework-gaps-raise-requirement`）；admin 侧先修症状（不被阻塞），框架根因并行提需求。
- **判断点（可 veto）——破窗号角色绑定保护**：为守住"总能以全权救援"的韧性目标，建议破窗号(id=1)的 `SUPER_ADMIN` 角色绑定也受保护——`assignRoles` 不允许从破窗号移除 `SUPER_ADMIN`（否则能登入却无救援能力，违背目标）。这是对"不可删/不可禁"的逻辑补全；若你认为角色管理应自由、仅守删/禁，可去掉此项。
- **决策追溯**：Bug ③ 的"授权 vs 韧性"拆分、破窗号=保留 ID、删 `system` 列——见 CONTEXT.md 术语表「超管」「破窗账号」。建议补 ADR-0003 固化"破窗号 = 固定保留 ID、授权走角色"决策。
