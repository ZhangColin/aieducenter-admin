-- ============================================================================
-- Admin Context: Role → Soybean alignment (status + home) (REQ-10 / issue #16)
-- ============================================================================
-- Purpose: Add role enable/disable status + default home route to sys_admin_roles.
-- Context: Admin (sys_)
-- ============================================================================
-- Background (issue #16, ADR-0003 修订): Soybean Admin roles carry an enabled/
-- disabled status and a default home route. This adds both columns to the roles
-- table. status is 1=ENABLED / 0=DISABLED — matches AdminRoleStatus BaseEnum
-- Integer code, and the same 1/0 convention as sys_admin_users.status and
-- sys_admin_menus.status. home is the Soybean route name a user lands on after
-- login for this role (nullable). The SUPER_ADMIN guard (cannot disable/delete)
-- lives in the AdminRole aggregate, not the schema. Existing roles backfill to
-- status=1 (enabled).
-- ----------------------------------------------------------------------------

ALTER TABLE sys_admin_roles ADD COLUMN IF NOT EXISTS status INTEGER NOT NULL DEFAULT 1;  -- 1=ENABLED / 0=DISABLED
ALTER TABLE sys_admin_roles ADD COLUMN IF NOT EXISTS home VARCHAR(100);

COMMENT ON COLUMN sys_admin_roles.status IS 'Role status (1=ENABLED / 0=DISABLED) - BaseEnum Integer code';
COMMENT ON COLUMN sys_admin_roles.home IS 'Default home route name after login (Soybean home)';
