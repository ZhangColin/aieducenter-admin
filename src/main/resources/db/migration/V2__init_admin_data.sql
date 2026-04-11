-- ============================================================================
-- Admin Context: Initial Data
-- ============================================================================
-- Purpose: Seed super admin account and base menu structure
-- Context: Admin (sys_)
-- ============================================================================
-- NOTE: Uses reserved ID range (1-10000) for system data
-- ============================================================================

-- ============================================================================
-- Insert Super Admin Role
-- ============================================================================
-- ID: 1 (reserved for system super admin)
-- Code: SUPER_ADMIN - Has all permissions by design, no explicit config needed
-- ============================================================================
INSERT INTO sys_admin_roles (
    id, name, code, description, sort_order,
    created_at, updated_at, deleted
) VALUES (
    1,
    '超级管理员',
    'SUPER_ADMIN',
    '拥有所有权限，无需配置',
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
);

-- ============================================================================
-- Insert System Admin Account
-- ============================================================================
-- ID: 1 (reserved for system admin)
-- Username: admin
-- Password: Hcy@2026 (BCrypt hash)
-- Status: 1 (ACTIVE)
-- System: TRUE (built-in account, cannot be deleted)
-- ============================================================================
INSERT INTO sys_admin_users (
    id, username, password, nickname, email, phone,
    status, system,
    created_at, updated_at, deleted
) VALUES (
    1,
    'admin',
    '$2a$10$ICG05u/zxSMDoGhaAMMQze9gsmlziEMRQK1zHrpbM7Ppl2Kdiu56S',
    '超级管理员',
    NULL,
    NULL,
    1,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
);

-- ============================================================================
-- Insert Base Menus
-- ============================================================================
-- IDs: 10, 20, 30, 40 (reserved range for system menus)
-- These are the core management menus for the admin panel
-- ============================================================================

-- Menu: 用户管理 (ID: 10)
INSERT INTO sys_admin_menus (
    id, name, path, icon, parent_id, sort_order, type,
    created_at, updated_at, deleted
) VALUES (
    10,
    '用户管理',
    '/admin/users',
    'User',
    NULL,
    10,
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
);

-- Menu: 角色管理 (ID: 20)
INSERT INTO sys_admin_menus (
    id, name, path, icon, parent_id, sort_order, type,
    created_at, updated_at, deleted
) VALUES (
    20,
    '角色管理',
    '/admin/roles',
    'Shield',
    NULL,
    20,
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
);

-- Menu: 菜单管理 (ID: 30)
INSERT INTO sys_admin_menus (
    id, name, path, icon, parent_id, sort_order, type,
    created_at, updated_at, deleted
) VALUES (
    30,
    '菜单管理',
    '/admin/menus',
    'Menu',
    NULL,
    30,
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
);

-- Menu: 权限管理 (ID: 40)
INSERT INTO sys_admin_menus (
    id, name, path, icon, parent_id, sort_order, type,
    created_at, updated_at, deleted
) VALUES (
    40,
    '权限管理',
    '/admin/permissions',
    'Key',
    NULL,
    40,
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
);

-- ============================================================================
-- Assign Super Admin Role to System Admin
-- ============================================================================
-- Links admin_id=1 (system admin) with role_id=1 (SUPER_ADMIN)
-- ============================================================================
INSERT INTO sys_admin_user_roles (
    id, admin_id, role_id, created_at, created_by
) VALUES (
    1,
    1,
    1,
    CURRENT_TIMESTAMP,
    1
);
