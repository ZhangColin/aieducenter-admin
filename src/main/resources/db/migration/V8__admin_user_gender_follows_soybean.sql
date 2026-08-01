-- ============================================================================
-- Admin Context: User profile → Soybean alignment (gender) (REQ-11 / issue #17)
-- ============================================================================
-- Purpose: Add gender column to sys_admin_users.
-- Context: Admin (sys_)
-- ============================================================================
-- Background (issue #17): Soybean Admin user profiles carry a gender field.
-- This adds a nullable gender column to sys_admin_users. gender is 1=MALE /
-- 2=FEMALE — matches AdminUserGender BaseEnum Integer code, same Integer-code
-- convention as sys_admin_users.status and sys_admin_roles.status. Nullable:
-- a profile field that may be left unset. Existing users backfill to NULL.
-- ----------------------------------------------------------------------------

ALTER TABLE sys_admin_users ADD COLUMN IF NOT EXISTS gender INTEGER;  -- 1=MALE / 2=FEMALE (nullable)

COMMENT ON COLUMN sys_admin_users.gender IS 'Gender (1=MALE / 2=FEMALE, nullable) - BaseEnum Integer code';
