-- ============================================================================
-- V14: Seed menus —— 账号管理 directory + 账号列表 leaf（2 行）
--       (account-admin T1 tracer bullet / issue #52)
-- ============================================================================
-- Purpose: 插入「账号管理」目录及其下「账号列表」叶子菜单，承载 account admin BFF 的侧边栏导航。
-- Context: Admin (sys_)
-- ============================================================================
-- Background (account-admin spec, issue #49 / CONTEXT.md「平台账号管理菜单」):
--   admin 作为 BFF 聚合 identity 的终端用户账号管理能力（搜索/封号/解封/解锁/强制下线），
--   前端 Soybean 路由需要「账号管理」目录 (directory, /account) 作为账号相关页面的
--   路由前缀容器，其下挂 1 个叶子菜单「账号列表」。账号详情不种菜单（前端抽屉/弹窗，
--   见 CONTEXT.md「详情页 = 弹窗/抽屉优先」）。菜单模型照 Soybean（ADR-0004）。
--
--   ID 分配沿用保留段（已用：10/20/30/40/50/60/70/80(payment)/90-已删/100-140(payment)）：
--     id=81  账号管理        directory  (sort=4，夹 payment 3 与系统管理 99 之间)
--     id=150 账号列表        menu       (sort=1)
--
--   SUPER_ADMIN 经框架 bypass 见全树，无需 role-menu 种子行。
--   幂等策略：INSERT ... ON CONFLICT (id) DO NOTHING（重复执行安全）。
--
--   不写种子快照测试（V12 已删、脆性高）；迁移正确性由 RoleAssignmentFlywaySchemaIntegrationTest
--   在独立 schema 跑全量 Flyway 链兜底。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) 账号管理 directory（id=81）—— 路由前缀容器
-- ----------------------------------------------------------------------------
INSERT INTO sys_admin_menus (
    id, menu_name, route_name, route_path, component,
    icon, icon_type, i18n_key,
    parent_id, sort_order, menu_type, status, keep_alive,
    created_at, updated_at
) VALUES (
    81,
    '账号管理',
    'account',
    '/account',
    'layout.base',
    'carbon:user',
    1,  -- iconify
    'route.account',
    NULL,
    4,
    1,  -- directory
    1,  -- active
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2) 账号列表 menu（id=150）—— sort=1
-- ----------------------------------------------------------------------------
INSERT INTO sys_admin_menus (
    id, menu_name, route_name, route_path, component,
    icon, icon_type, i18n_key,
    parent_id, sort_order, menu_type, status, keep_alive,
    created_at, updated_at
) VALUES (
    150,
    '账号列表',
    'account_list',
    '/account/list',
    'view.account_list',
    'carbon:user-multiple',
    1,  -- iconify
    'route.account_list',
    81,
    1,
    2,  -- menu
    1,  -- active
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
