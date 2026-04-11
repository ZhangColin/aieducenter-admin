-- ============================================================================
-- Admin Context: Foreign Key Constraints
-- ============================================================================
-- Purpose: Define referential integrity constraints for admin tables
-- Context: Admin (sys_)
-- ============================================================================
-- NOTE: Constraints are created in a separate migration script for better
-- control over the order of operations and to avoid circular dependencies
-- during table creation.
-- ============================================================================

-- ============================================================================
-- Foreign Key: sys_admin_menus.parent_id → sys_admin_menus.id
-- Purpose: Support hierarchical menu structure (self-reference)
-- ============================================================================
ALTER TABLE sys_admin_menus
    ADD CONSTRAINT fk_sys_admin_menus_parent_id
    FOREIGN KEY (parent_id)
    REFERENCES sys_admin_menus(id)
    ON DELETE RESTRICT
    ON UPDATE CASCADE;

COMMENT ON CONSTRAINT fk_sys_admin_menus_parent_id ON sys_admin_menus
    IS 'Foreign key: parent menu references sys_admin_menus.id (self-reference)';

-- ============================================================================
-- Foreign Key: sys_admin_user_roles.admin_id → sys_admin_users.id
-- Purpose: Ensure user-role assignments reference valid users
-- ============================================================================
ALTER TABLE sys_admin_user_roles
    ADD CONSTRAINT fk_sys_admin_user_roles_admin_id
    FOREIGN KEY (admin_id)
    REFERENCES sys_admin_users(id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

COMMENT ON CONSTRAINT fk_sys_admin_user_roles_admin_id ON sys_admin_user_roles
    IS 'Foreign key: admin_id references sys_admin_users.id (cascade delete)';

-- ============================================================================
-- Foreign Key: sys_admin_user_roles.role_id → sys_admin_roles.id
-- Purpose: Ensure user-role assignments reference valid roles
-- ============================================================================
ALTER TABLE sys_admin_user_roles
    ADD CONSTRAINT fk_sys_admin_user_roles_role_id
    FOREIGN KEY (role_id)
    REFERENCES sys_admin_roles(id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

COMMENT ON CONSTRAINT fk_sys_admin_user_roles_role_id ON sys_admin_user_roles
    IS 'Foreign key: role_id references sys_admin_roles.id (cascade delete)';

-- ============================================================================
-- Foreign Key: sys_admin_role_menus.role_id → sys_admin_roles.id
-- Purpose: Ensure role-menu assignments reference valid roles
-- ============================================================================
ALTER TABLE sys_admin_role_menus
    ADD CONSTRAINT fk_sys_admin_role_menus_role_id
    FOREIGN KEY (role_id)
    REFERENCES sys_admin_roles(id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

COMMENT ON CONSTRAINT fk_sys_admin_role_menus_role_id ON sys_admin_role_menus
    IS 'Foreign key: role_id references sys_admin_roles.id (cascade delete)';

-- ============================================================================
-- Foreign Key: sys_admin_role_menus.menu_id → sys_admin_menus.id
-- Purpose: Ensure role-menu assignments reference valid menus
-- ============================================================================
ALTER TABLE sys_admin_role_menus
    ADD CONSTRAINT fk_sys_admin_role_menus_menu_id
    FOREIGN KEY (menu_id)
    REFERENCES sys_admin_menus(id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

COMMENT ON CONSTRAINT fk_sys_admin_role_menus_menu_id ON sys_admin_role_menus
    IS 'Foreign key: menu_id references sys_admin_menus.id (cascade delete)';

-- ============================================================================
-- Foreign Key: sys_admin_role_permissions.role_id → sys_admin_roles.id
-- Purpose: Ensure role permissions reference valid roles
-- ============================================================================
ALTER TABLE sys_admin_role_permissions
    ADD CONSTRAINT fk_sys_admin_role_permissions_role_id
    FOREIGN KEY (role_id)
    REFERENCES sys_admin_roles(id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

COMMENT ON CONSTRAINT fk_sys_admin_role_permissions_role_id ON sys_admin_role_permissions
    IS 'Foreign key: role_id references sys_admin_roles.id (cascade delete)';
