-- ============================================================================
-- Admin Context: Align seed menus with frontend (REQ-6)
-- ============================================================================
-- Purpose: Realign seed menus to the admin-web sidebar contract
-- Context: Admin (sys_)
-- ============================================================================
-- Background (REQ-6, issue #8): the V2 seed (4 flat MENU nodes, /admin/* paths,
-- Lucide-style icon names) mismatches the frontend's actual shape:
--   1. Route prefix  → frontend routes live under /dashboard/* (Next.js App Router)
--   2. Icon system   → frontend renders Material Symbols names verbatim
--                      (lowercase snake_case); backend stores icon as an opaque string
--   3. Structure     → sidebar is a two-panel interaction needing a GROUP level
--   4. 权限管理 menu → frontend decided against a standalone permission page
--                      (permission codes are assigned inside the role page, 2026-07-28)
--
-- Resulting two-level structure (type semantics per REQ-1: GROUP.path is NULL):
--   控制台       MENU  /dashboard         icon=dashboard  sort=10
--   系统管理     GROUP (path NULL)        icon=settings   sort=20
--     用户管理   MENU  /dashboard/users   icon=group      sort=10
--     角色管理   MENU  /dashboard/roles   icon=shield     sort=20
--     菜单管理   MENU  /dashboard/menus   icon=menu       sort=30
--
-- Assignment continuity (no dangling role-menu links):
--   * The three kept menus (id 10/20/30) are UPDATEd in place → existing role
--     assignments stay valid; their new parent GROUP (id 60) reaches consumers
--     via MenuTreeAssembler ancestor-chain completion (no explicit GROUP
--     assignment needed).
--   * SUPER_ADMIN needs no assignment rows at all: getMenus() short-circuits
--     super admins to the full tree (findTree(null)).
--   * 权限管理 (id 40) is DELETEd; its role-menu assignment rows are removed
--     first (defensive — the FK is ON DELETE CASCADE anyway).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) New top-level nodes (reserved id range continues the V2 tens sequence)
-- ----------------------------------------------------------------------------
-- Menu: 控制台 (ID: 50)
INSERT INTO sys_admin_menus (
    id, name, path, icon, parent_id, sort_order, type,
    created_at, updated_at, deleted
) VALUES (
    50,
    '控制台',
    '/dashboard',
    'dashboard',
    NULL,
    10,
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
);

-- Group: 系统管理 (ID: 60) — GROUP: path 归一 NULL（type↔path 不变量）
INSERT INTO sys_admin_menus (
    id, name, path, icon, parent_id, sort_order, type,
    created_at, updated_at, deleted
) VALUES (
    60,
    '系统管理',
    NULL,
    'settings',
    NULL,
    20,
    2,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
);

-- ----------------------------------------------------------------------------
-- 2) Realign the three kept menus in place (id 不变 → 既有角色分配不悬空)
-- ----------------------------------------------------------------------------
UPDATE sys_admin_menus
SET path = '/dashboard/users', icon = 'group', parent_id = 60, sort_order = 10,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 10;

UPDATE sys_admin_menus
SET path = '/dashboard/roles', icon = 'shield', parent_id = 60, sort_order = 20,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 20;

UPDATE sys_admin_menus
SET path = '/dashboard/menus', icon = 'menu', parent_id = 60, sort_order = 30,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 30;

-- ----------------------------------------------------------------------------
-- 3) Remove 权限管理 (ID: 40) — 前端不建独立权限页（权限码并入角色管理页分配）
-- ----------------------------------------------------------------------------
DELETE FROM sys_admin_role_menus WHERE menu_id = 40;
DELETE FROM sys_admin_menus WHERE id = 40;
