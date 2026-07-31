-- ============================================================================
-- Admin Context: Menu model → Soybean route-generator (REQ-8 / issue #13 / ADR-0004)
-- ============================================================================
-- Purpose: Switch sys_admin_menus from the retired MENU/GROUP/DIVIDER three-value
--          model to the Soybean @elegant-router route-generator model.
-- Context: Admin (sys_)
-- ============================================================================
-- Background (ADR-0004, issue #13): Soybean Admin v2 is the admin frontend; its
-- menu IS the route generator's data source. Soybean's menuType has only two
-- values — directory(1) / menu(2) — and each menu carries full Vue Router meta.
-- The prior three-value model (MENU=1 / GROUP=2 / DIVIDER=3, REQ-1) and its
-- path invariant (ADMIN_014_3) are retired; type/path/icon semantics now follow
-- Soybean source verbatim. Naming aligns to Soybean (DB column + Java + JSON),
-- the sole exception being `sort_order` (kept; Soybean calls it `order`, a PG
-- reserved word). icons switch from Material Symbols to iconify ids.
--
-- Seed menus mirror the Soybean example routes (soybean/example src/router/elegant/routes.ts):
--   home        menu   /home            layout.base$view.home    icon=mdi:monitor-dashboard      order=1            i18n=route.home
--   manage   directory /manage          layout.base              icon=carbon:cloud-service-management order=9       i18n=route.manage
--     manage_user  menu /manage/user    view.manage_user         icon=ic:round-manage-accounts   order=1            i18n=route.manage_user
--     manage_role  menu /manage/role    view.manage_role         icon=carbon:user-role           order=2            i18n=route.manage_role
--     manage_menu  menu /manage/menu    view.manage_menu         icon=material-symbols:route     order=3 keepAlive  i18n=route.manage_menu
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) Rename legacy columns to Soybean naming
-- ----------------------------------------------------------------------------
ALTER TABLE sys_admin_menus RENAME COLUMN name TO menu_name;
ALTER TABLE sys_admin_menus RENAME COLUMN path TO route_path;
ALTER TABLE sys_admin_menus RENAME COLUMN type TO menu_type;

-- Widen text columns to match entity lengths (iconify ids / longer names)
ALTER TABLE sys_admin_menus ALTER COLUMN menu_name TYPE VARCHAR(100);
ALTER TABLE sys_admin_menus ALTER COLUMN icon TYPE VARCHAR(100);

-- ----------------------------------------------------------------------------
-- 2) Add Soybean route-generator columns
-- ----------------------------------------------------------------------------
ALTER TABLE sys_admin_menus ADD COLUMN IF NOT EXISTS route_name VARCHAR(100);
ALTER TABLE sys_admin_menus ADD COLUMN IF NOT EXISTS component VARCHAR(255);
ALTER TABLE sys_admin_menus ADD COLUMN IF NOT EXISTS icon_type INTEGER NOT NULL DEFAULT 1;  -- 1=iconify / 2=local
ALTER TABLE sys_admin_menus ADD COLUMN IF NOT EXISTS i18n_key VARCHAR(100);
ALTER TABLE sys_admin_menus ADD COLUMN IF NOT EXISTS keep_alive BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sys_admin_menus ADD COLUMN IF NOT EXISTS constant BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sys_admin_menus ADD COLUMN IF NOT EXISTS multi_tab BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sys_admin_menus ADD COLUMN IF NOT EXISTS hide_in_menu BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sys_admin_menus ADD COLUMN IF NOT EXISTS active_menu VARCHAR(100);
ALTER TABLE sys_admin_menus ADD COLUMN IF NOT EXISTS href VARCHAR(255);
ALTER TABLE sys_admin_menus ADD COLUMN IF NOT EXISTS fixed_index_in_tab INTEGER;
ALTER TABLE sys_admin_menus ADD COLUMN IF NOT EXISTS query TEXT;                             -- JSON [{key,value}]
ALTER TABLE sys_admin_menus ADD COLUMN IF NOT EXISTS status INTEGER NOT NULL DEFAULT 1;      -- 1=active / 0=disabled

-- ----------------------------------------------------------------------------
-- 3) Convert old type values to Soybean directory/menu + drop DIVIDER
--    old MENU(1) → menu(2) ; old GROUP(2) → directory(1) ; DIVIDER(3) → delete
-- ----------------------------------------------------------------------------
UPDATE sys_admin_menus
SET menu_type = CASE menu_type WHEN 1 THEN 2 WHEN 2 THEN 1 ELSE menu_type END;

-- Remove DIVIDER rows (and any dangling role-menu links) before they violate the new two-value model
DELETE FROM sys_admin_role_menus WHERE menu_id IN (SELECT id FROM sys_admin_menus WHERE menu_type = 3);
DELETE FROM sys_admin_menus WHERE menu_type = 3;

-- ----------------------------------------------------------------------------
-- 4) Rebuild seed menus to mirror Soybean routes
--    IDs preserved (50/60/10/20/30) → existing role-menu assignments stay valid.
-- ----------------------------------------------------------------------------

-- home (id 50): 首页 / dashboard leaf
UPDATE sys_admin_menus SET
    menu_name = '首页', route_name = 'home', route_path = '/home',
    component = 'layout.base$view.home', icon = 'mdi:monitor-dashboard', icon_type = 1,
    i18n_key = 'route.home', parent_id = NULL, sort_order = 1, menu_type = 2, status = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 50;

-- manage (id 60): 系统管理 directory container（icon/order 取自 soybean/example routes.ts）
UPDATE sys_admin_menus SET
    menu_name = '系统管理', route_name = 'manage', route_path = '/manage',
    component = 'layout.base', icon = 'carbon:cloud-service-management', icon_type = 1,
    i18n_key = 'route.manage', parent_id = NULL, sort_order = 9, menu_type = 1, status = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 60;

-- manage_user (id 10)
UPDATE sys_admin_menus SET
    menu_name = '用户管理', route_name = 'manage_user', route_path = '/manage/user',
    component = 'view.manage_user', icon = 'ic:round-manage-accounts', icon_type = 1,
    i18n_key = 'route.manage_user', parent_id = 60, sort_order = 1, menu_type = 2, status = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 10;

-- manage_role (id 20)
UPDATE sys_admin_menus SET
    menu_name = '角色管理', route_name = 'manage_role', route_path = '/manage/role',
    component = 'view.manage_role', icon = 'carbon:user-role', icon_type = 1,
    i18n_key = 'route.manage_role', parent_id = 60, sort_order = 2, menu_type = 2, status = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 20;

-- manage_menu (id 30)（soybean/example：keepAlive=true）
UPDATE sys_admin_menus SET
    menu_name = '菜单管理', route_name = 'manage_menu', route_path = '/manage/menu',
    component = 'view.manage_menu', icon = 'material-symbols:route', icon_type = 1,
    i18n_key = 'route.manage_menu', parent_id = 60, sort_order = 3, menu_type = 2, status = 1,
    keep_alive = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 30;

-- ----------------------------------------------------------------------------
-- 5) Refresh column comments to Soybean semantics
-- ----------------------------------------------------------------------------
COMMENT ON COLUMN sys_admin_menus.menu_name IS 'Menu display name (Soybean menuName)';
COMMENT ON COLUMN sys_admin_menus.route_name IS 'Vue Router route name (Soybean routeName)';
COMMENT ON COLUMN sys_admin_menus.route_path IS 'Route path (Soybean routePath)';
COMMENT ON COLUMN sys_admin_menus.component IS 'Component encoding layout.<L>$view.<P> (Soybean component)';
COMMENT ON COLUMN sys_admin_menus.icon_type IS 'Icon type (1=iconify / 2=local) - BaseEnum Integer code (Soybean IconType)';
COMMENT ON COLUMN sys_admin_menus.menu_type IS 'Menu type (1=directory / 2=menu) - BaseEnum Integer code (Soybean MenuType)';
COMMENT ON COLUMN sys_admin_menus.i18n_key IS 'Sidebar i18n key, e.g. route.manage_user (Soybean i18nKey)';
COMMENT ON COLUMN sys_admin_menus.keep_alive IS 'Whether to cache the route (Soybean keepAlive)';
COMMENT ON COLUMN sys_admin_menus.constant IS 'Whether the route needs no auth (Soybean constant)';
COMMENT ON COLUMN sys_admin_menus.multi_tab IS 'Whether different query uses different tabs (Soybean multiTab)';
COMMENT ON COLUMN sys_admin_menus.hide_in_menu IS 'Whether to hide the route in the menu (Soybean hideInMenu)';
COMMENT ON COLUMN sys_admin_menus.active_menu IS 'Menu key activated when entering the hidden route (Soybean activeMenu)';
COMMENT ON COLUMN sys_admin_menus.href IS 'Outer link of the route (Soybean href)';
COMMENT ON COLUMN sys_admin_menus.fixed_index_in_tab IS 'Order of fixed tab if set (Soybean fixedIndexInTab)';
COMMENT ON COLUMN sys_admin_menus.query IS 'Static route query params as JSON [{key,value}] (Soybean query)';
COMMENT ON COLUMN sys_admin_menus.status IS 'Status (1=active / 0=disabled) - BaseEnum Integer code';
