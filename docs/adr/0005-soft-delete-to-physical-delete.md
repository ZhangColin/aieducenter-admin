# ADR-0005：三聚合软删 → 物理删除；守卫迁为显式 `requireDeletable()`（REQ-14 / issue #24）

- **状态**：Accepted；已实现（2026-08-03）
- **日期**：2026-08-03
- **关联**：[issue #24](https://github.com/ZhangColin/aieducenter-admin/issues/24)（REQ-14）；前序 [ADR-0003](0003-break-glass-reserved-id.md)（破窗/超管守卫原挂 `markAsDeleted`，本 ADR 迁移其执行点）；框架侧软删文档/约定降级由 [cartisan-boot#10](https://github.com/ZhangColin/cartisan-boot/issues/10) 独立跟踪

## 背景

REQ-14：用户软删后其 `sys_admin_user_roles` 关联行残留，`AdminRoleRepository.isUsedByAnyAdmin` 只按 `roleId` 计数、未排除已软删用户 → 挂过（已删）用户的角色 `DELETE /roles/{id}` 永返 `403 ADMIN_013 角色正在使用中`，**永远无法删除**。

这不是孤例，而是软删在本应用的第三次泄漏：

1. **REQ-5**（issue #7）：软删行在列表/详情查询漏出 → 靠框架 `SoftDeletableRestrictionContributor`（cartisan-boot#2）+ 显式 `DeletedFalse` 派生查询双重打补丁；
2. **汇总聚合**（issue #21）：禁用/软删角色照样发菜单发权限 → JPQL 里手写 `r.deleted = false`；
3. **REQ-14**（issue #24）：删除守卫被残留关联永阻。

软删在本应用**零收益**：三个聚合均有 `status`（`ACTIVE/DISABLED`、`ENABLED/DISABLED`）覆盖「停用」语义，从无恢复（undelete）需求。留下的只有成本——每条新查询都必须记得显式过滤 `deleted`，漏一处就是一个 bug。

## 决策

**`AdminUser` / `AdminRole` / `AdminMenu` 从软删迁移为物理删除**，基类 `AuditableSoftDeletable` → `Auditable`（保留 `createdAt/updatedAt/createdBy/updatedBy` 审计字段），`repository.delete(entity)` = 物理 DELETE。关联实体 `AdminUserRole` / `AdminRoleMenu` / `AdminRolePermission` 本就物理删除，不变。

1. **`markAsDeleted()` override 整体移除**（关键陷阱）：框架 `BaseRepositoryImpl.delete()` 对「有 `markAsDeleted` 方法但不实现 `SoftDeletable`」的实体走**反射软存**——方法残留则删除不会物理生效。
2. **守卫迁为显式领域方法**：破窗守卫（`AdminUser` 保留 ID=1 不可删）与超管守卫（`SUPER_ADMIN` 角色不可删）从 `markAsDeleted()` override 迁为 `requireDeletable()`，由对应 AppService 在 `repository.delete()` **之前显式调用**。行为不变（403 + 原错误码），且超管角色的 `SUPER_ADMIN_CANNOT_DELETE` 不再依赖「跳过 in-use 检查」的特判排序（守卫先于 `ROLE_IN_USE` 命中）。
3. **关联行级联无需新机制**：JPA `cascade = ALL + orphanRemoval`（测试库无 FK 约束亦生效）+ 生产库 V3 既有 `FK ON DELETE CASCADE`（`user_roles`/`role_menus`/`role_permissions`）双保险。角色删除的 in-use 守卫保留——残留消失后计数天然只含存活用户。
4. **Flyway V9**：先 `DELETE FROM ... WHERE deleted = true` 物理清除历史软删行（防迁移后同名「复活」；菜单自引用 FK 为 `RESTRICT`，已软删菜单及其父链已断的后代经递归 CTE 一并圈除），再从三张主表 `DROP COLUMN deleted`。历史迁移文件不改。
5. **查询侧清理红利**：`findByStatusAndDeletedFalse...` → `findByStatusOrderBySortOrderAscIdAsc`、`findByIdInAndDeletedFalse` → `findByIdIn`、`findByIdInAndStatusAndDeletedFalse` → `findByIdInAndStatus`（`deleted` 字段消失后派生查询必须改名，否则 Spring Data 派生启动失败）；`AdminUserRepository.hasRole` JPQL 去掉 `r.deleted = false`。
6. **测试**：`SoftDeleteReadFilterIntegrationTest`（前提消失）改写为 `PhysicalDeleteIntegrationTest`——钉「物理删除真实生效（DB 无行）+ REQ-14 复现链 + 关联行级联清除 + 三表无 deleted 列」；破窗/超管守卫 HTTP 行为由既有集成测试在新执行点重钉。

### 否决

- **仅修 REQ-14 查询侧**（`isUsedByAnyAdmin` join `AdminUser` 加 `u.deleted = false`，issue 原始倾向 1）：最小改动、能灭本条 bug，但软删的泄漏面原样保留——下一个查询照样可能漏过滤。治标不治本。
- **用户软删时级联删关联行**（issue 原始倾向 2）：只清 `user_roles` 一类残留；软删行本身的复活/泄漏问题（同名唯一约束占位、查询漏过滤）仍在。
- **关联行在应用层显式删**：JPA/DB 双层 cascade 已覆盖（聚合内实体随聚合根删除），引入第三条手工路径只会漂移。
- **保留 `markAsDeleted()` 作为守卫挂载点**：框架对非 `SoftDeletable` 实体的反射软存路径会使「删除」静默退化为 save——必须整体移除，守卫只能显式调用。

## 结果

- ✅ REQ-14 复现链通过：建角色 + 建用户 + 挂角色 → 删用户（`user_roles` 行物理清除）→ 删角色成功，`GET /roles` 查无该角色（`PhysicalDeleteIntegrationTest` 钉住）。
- ✅ 守卫行为不变：删破窗号仍 403 `BREAK_GLASS_CANNOT_DELETE`、删 `SUPER_ADMIN` 角色仍 403 `SUPER_ADMIN_CANNOT_DELETE`（`BreakGlassAccountProtectionIntegrationTest` / `RoleManagementSoybeanAlignmentIntegrationTest` 重钉）。
- ✅ 查询侧 `deleted` 条件全清；API 契约不变（DELETE 仍 200/403，仅语义从软删变物理删）——前端零改动。
- ✅ 用户名 / 角色 code 删除后天然可复用（唯一约束不变、物理行已不存在，无需 DDL 调整）。
- ✅ 软删政策单一事实源：本 ADR；框架侧文档降级跟踪见 cartisan-boot#10。

## 参见

- 工作原则 memory：`framework-gaps-raise-requirement`。
- 复现与验收：[issue #24](https://github.com/ZhangColin/aieducenter-admin/issues/24)。
