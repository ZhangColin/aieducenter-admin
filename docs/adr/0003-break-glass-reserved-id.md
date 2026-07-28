# ADR-0003：破窗账号 = 固定保留 ID、授权走角色；删 `system` 列（Bug ③）

- **状态**：Accepted；已实现（2026-07-28）
- **日期**：2026-07-28
- **关联**：起步包必修 Bug ③；术语见 [CONTEXT.md「破窗账号」「超管」](../../CONTEXT.md)；前序 [ADR-0002](0002-super-admin-bypass-is-framework-gap.md)（Bug ② 超管 bypass）

## 背景

`sys_admin_users` 历史上有 `system BOOLEAN NOT NULL`（无默认值）一列，语义模糊地把两件事搅在一起：

1. **"内置不可删"**（运维韧性）——保证总有一个救援号能登进来。
2. **"系统管理员"**（授权）——谁是超管。

由此产生两个问题：

- **生产 500（潜伏）**：`AdminUser` 聚合从未映射 `system` 列；生产 `ddl-auto=none` 下，建任何管理员的 INSERT 都漏写该列、命中 `NOT NULL` 直接 500。测试用 `ddl-auto=create-drop` 按实体建表（无此列），所以一直没抓到。
- **缺救援守卫**：`delete()`/`disable()` 没有"内置账号不可删/不可禁"的领域守卫——运营能把破窗救援号 `admin`(id=1) 删掉或禁用、把自己锁死在外。原 `count()<=1` last-admin 检查是脆的"最后一个超管"规则、且破窗号永在后已成死逻辑。

授权层（"谁是超管"）在 Bug ② 后已由 `SUPER_ADMIN` 角色 + 框架 `AuthorizationBypassResolver` 解决，**不需要 per-user 标记位**。

## 决策

**把"授权"与"运维韧性"拆开**，各自单一表达：

- **授权（谁是超管）**：继续走 `SUPER_ADMIN` 角色（种子 id=1，V2 内置）+ 框架 `AuthorizationBypassResolver`。不引入 per-user 标记位。
- **运维韧性（救援入口）**：由一个**固定破窗账号**承担——内置 `admin`，**保留 ID = 1**（`AdminUser.BREAK_GLASS_ADMIN_ID`，沿用 V2 种子的保留段 1–10000）。不可删、不可禁、可改密。识别方式为**保留 ID**，不靠列。

据此：

1. **`AdminUser` 聚合加守卫**（领域不变量单一执行点）：override 软删入口 `markAsDeleted()`——目标 id == 破窗号 → 抛 `DomainException(BREAK_GLASS_CANNOT_DELETE)`；`disable()` 同理保护（`BREAK_GLASS_CANNOT_DISABLE`）。`enable()` / 改密 / 改名放行。守卫点正确性源于框架 `BaseRepositoryImpl.delete()` 经 `SoftDeletable.markAsDeleted()` 执行软删（虚分派命中 override）。
2. **删除 `system` 列**（V4 迁移 `DROP COLUMN system`）。实体本就没映射它；删列后实体↔Flyway schema 重新对齐，"生产建管理员 500"随之消失。
3. **删掉 `count()<=1` last-admin 检查**及其错误码 `LAST_ADMIN_CANNOT_DELETE`——破窗号永在，该规则成死逻辑。
4. **`AdminUserResponse` 暴露 `breakGlass` 派生字段**（按 `id == BREAK_GLASS_ADMIN_ID` 判定，不入库），供前端标注（如"内置·不可删"）。创建管理员 API **不**暴露该开关——破窗号只由保留 ID 决定。
5. **角色绑定补全（判断点）**：为守住"总能以全权救援"，破窗号的 `SUPER_ADMIN` 绑定也受保护——`assignRoles` 不允许从破窗号移除 `SUPER_ADMIN`（`BREAK_GLASS_SUPER_ADMIN_REQUIRED`）。否则能登入却无救援能力，违背韧性目标。这是"不可删/不可禁"的逻辑补全。

### 否决

- **保留 `system` 列只改语义**：列本身是 bug 根因（生产漏列 500），且"内置不可删"改由保留 ID 承载更直接——删列更干净。
- **破窗号 = "最后一个超管"动态规则**：脆、难文档化、随角色成员漂移。固定保留 ID 识别简单、无歧义。
- **per-user 超管标记位**：与 Bug ② 已落地的"授权走角色"重复，双轨必漂移。

## 结果

- ✅ 生产建管理员不再 500（实体↔schema 对齐）。
- ✅ 破窗救援入口不可删/不可禁/不可去权，可改密；守卫在聚合内单一执行。
- ✅ 授权（角色）与韧性（保留 ID）解耦，`system` 列的语义混淆彻底消除。
- ✅ `AdminUserResponse.breakGlass` 供前端平滑跟进（增量、向后兼容）。
- ✅ Bug ④（登录写 userName）框架根因已修（cartisan-boot commit `efcb1e7` 破坏性补全 `login` 签名）、admin 已迁到新签名并删 `StpUtil` workaround，见 CONTEXT.md 决策记录。

## 参见

- 工作原则 memory：`framework-gaps-raise-requirement`。
- spec：[`.scratch/phase0-completion/spec.md`](../../.scratch/phase0-completion/spec.md)（Bug ③④）。
