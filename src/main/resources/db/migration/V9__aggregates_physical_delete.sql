-- ============================================================================
-- V9: AdminUser / AdminRole / AdminMenu 软删 → 物理删除（ADR-0005，REQ-14 / issue #24）
-- ============================================================================
-- 背景：软删在本应用无恢复需求（三聚合均有 ACTIVE/DISABLED 类状态覆盖「停用」语义），
-- 只带来残留泄漏面（REQ-5 读过滤、REQ-14 删除守卫被软删用户的残留关联永阻）。
--
-- 本迁移：
--   1) 物理清除历史软删行（deleted = true）——防迁移后同名「复活」与残留泄漏。
--      关联表（sys_admin_user_roles / sys_admin_role_menus / sys_admin_role_permissions）
--      经 V3 FK ON DELETE CASCADE 随主表行一并清除。
--   2) 三张主表 DROP COLUMN deleted——与聚合基类 AuditableSoftDeletable → Auditable 对齐。
--      依赖 deleted 列的部分索引 idx_sys_admin_users_username 随列自动删除；
--      uq_* 唯一约束不涉及 deleted，保留——物理删除后用户名/角色码天然可复用。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) 清除历史软删行
-- ----------------------------------------------------------------------------

-- 菜单自引用 FK（parent_id）为 ON DELETE RESTRICT：先圈出「已软删菜单及其全部后代」
-- （父链已断的后代在树中不可见，属残留，一并清除）。单语句删除——约束检查在语句末进行，
-- 父子同语句删除安全；已删菜单的 role_menus 关联行经 FK CASCADE 一并清除。
WITH RECURSIVE doomed AS (
    SELECT id FROM sys_admin_menus WHERE deleted = TRUE
    UNION
    SELECT m.id FROM sys_admin_menus m JOIN doomed d ON m.parent_id = d.id
)
DELETE FROM sys_admin_menus WHERE id IN (SELECT id FROM doomed);

DELETE FROM sys_admin_users WHERE deleted = TRUE;

DELETE FROM sys_admin_roles WHERE deleted = TRUE;

-- ----------------------------------------------------------------------------
-- 2) 三张主表去除软删列
-- ----------------------------------------------------------------------------

ALTER TABLE sys_admin_users DROP COLUMN deleted;
ALTER TABLE sys_admin_roles DROP COLUMN deleted;
ALTER TABLE sys_admin_menus DROP COLUMN deleted;
