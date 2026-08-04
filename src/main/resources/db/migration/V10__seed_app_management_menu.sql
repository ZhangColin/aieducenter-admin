-- ============================================================================
-- V10: Seed menus —— 应用管理 directory + 应用 menu + 系统管理 sort_order 调整
--       (REQ-15-T1 / issue #26)
-- ============================================================================
-- Purpose: 插入「应用管理」和「应用」两条种子菜单，并将「系统管理」sort_order
--          从 9 调整为 99，为后续应用相关菜单预留空间。
-- Context: Admin (sys_)
-- ============================================================================
-- Background (REQ-15, issue #26 / PR #25):
--   前端 Soybean 路由需要「应用管理」目录 (directory, /app) 作为应用相关页面的
--   路由前缀容器，其下挂「应用」叶子菜单 (menu, /app/list) 用于应用列表管理。
--   系统管理 sort_order 9 → 99 为应用管理 (sort_order=2) 在侧边栏中排在
--   控制台 (sort_order=1) 之后、系统管理 (sort_order=99) 之前腾出空间。
--
--   ID 分配沿用 V2/V5/V6 保留段：
--     id=40  应用管理  directory  (原 V2 权限管理被 V5 删除，id=40 复用)
--     id=70  应用      menu
--
--   幂等策略：INSERT ... ON CONFLICT (id) DO NOTHING（重复执行安全）；
--            UPDATE sort_order 重复执行值不变，同样幂等。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) 应用管理 directory（id=40）—— 路由前缀容器
-- ----------------------------------------------------------------------------
INSERT INTO sys_admin_menus (
    id, menu_name, route_name, route_path, component,
    icon, icon_type, i18n_key,
    parent_id, sort_order, menu_type, status, keep_alive,
    created_at, updated_at
) VALUES (
    40,
    '应用管理',
    'app',
    '/app',
    'layout.base',
    'carbon:application',
    1,  -- iconify
    'route.app',
    NULL,
    2,
    1,  -- directory
    1,  -- active
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2) 应用 menu（id=70）—— 叶子菜单，挂应用管理下
-- ----------------------------------------------------------------------------
INSERT INTO sys_admin_menus (
    id, menu_name, route_name, route_path, component,
    icon, icon_type, i18n_key,
    parent_id, sort_order, menu_type, status, keep_alive,
    created_at, updated_at
) VALUES (
    70,
    '应用',
    'app_list',
    '/app/list',
    'view.app_list',
    'carbon:application',
    1,  -- iconify
    'route.app_list',
    40,
    1,
    2,  -- menu
    1,  -- active
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3) 系统管理 sort_order：9 → 99（为应用管理腾出侧边栏位置）
-- ----------------------------------------------------------------------------
UPDATE sys_admin_menus
SET sort_order = 99, updated_at = CURRENT_TIMESTAMP
WHERE id = 60;
