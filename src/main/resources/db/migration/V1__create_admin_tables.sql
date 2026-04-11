-- ============================================================================
-- Admin Context: Core Tables
-- ============================================================================
-- Purpose: Platform administrator management with RBAC support
-- Context: Admin (sys_)
-- ============================================================================

-- ============================================================================
-- Table: sys_admin_users
-- Purpose: Store administrator account information
-- ============================================================================
CREATE TABLE sys_admin_users (
    -- Primary Key (TSID)
    id BIGINT PRIMARY KEY,

    -- Authentication fields
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,

    -- Profile fields
    nickname VARCHAR(50) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(20),
    avatar VARCHAR(512),

    -- Status fields
    status INTEGER NOT NULL,
    system BOOLEAN NOT NULL,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,

    -- Soft delete marker
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- Unique constraints
    CONSTRAINT uq_sys_admin_users_username UNIQUE (username)
);

-- Add table comment
COMMENT ON TABLE sys_admin_users IS 'Platform administrator accounts with RBAC support';

-- Add column comments
COMMENT ON COLUMN sys_admin_users.id IS 'Primary key (TSID)';
COMMENT ON COLUMN sys_admin_users.username IS 'Unique username for admin login';
COMMENT ON COLUMN sys_admin_users.password IS 'BCrypt encrypted password';
COMMENT ON COLUMN sys_admin_users.nickname IS 'Display name';
COMMENT ON COLUMN sys_admin_users.email IS 'Email address for notifications';
COMMENT ON COLUMN sys_admin_users.phone IS 'Phone number for SMS notifications';
COMMENT ON COLUMN sys_admin_users.avatar IS 'Avatar URL';
COMMENT ON COLUMN sys_admin_users.status IS 'Account status (0=DISABLED, 1=ACTIVE) - BaseEnum Integer code';
COMMENT ON COLUMN sys_admin_users.system IS 'System user marker (TRUE = built-in, cannot be deleted)';
COMMENT ON COLUMN sys_admin_users.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN sys_admin_users.updated_at IS 'Record last update timestamp';
COMMENT ON COLUMN sys_admin_users.created_by IS 'Admin ID who created this record';
COMMENT ON COLUMN sys_admin_users.updated_by IS 'Admin ID who last updated this record';
COMMENT ON COLUMN sys_admin_users.deleted IS 'Soft delete marker (FALSE = active, TRUE = deleted)';

-- Index on username for active admins
CREATE INDEX idx_sys_admin_users_username
    ON sys_admin_users(username)
    WHERE deleted = FALSE;

-- ============================================================================
-- Table: sys_admin_roles
-- Purpose: Define administrator roles for RBAC
-- ============================================================================
CREATE TABLE sys_admin_roles (
    -- Primary Key (TSID)
    id BIGINT PRIMARY KEY,

    -- Role fields
    name VARCHAR(50) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    sort_order INT NOT NULL,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,

    -- Soft delete marker
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- Unique constraints
    CONSTRAINT uq_sys_admin_roles_name UNIQUE (name),
    CONSTRAINT uq_sys_admin_roles_code UNIQUE (code)
);

-- Add table comment
COMMENT ON TABLE sys_admin_roles IS 'Administrator roles for role-based access control';

-- Add column comments
COMMENT ON COLUMN sys_admin_roles.id IS 'Primary key (TSID)';
COMMENT ON COLUMN sys_admin_roles.name IS 'Role display name';
COMMENT ON COLUMN sys_admin_roles.code IS 'Unique role code for programmatic access';
COMMENT ON COLUMN sys_admin_roles.description IS 'Role description';
COMMENT ON COLUMN sys_admin_roles.sort_order IS 'Display order in UI';
COMMENT ON COLUMN sys_admin_roles.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN sys_admin_roles.updated_at IS 'Record last update timestamp';
COMMENT ON COLUMN sys_admin_roles.created_by IS 'Admin ID who created this record';
COMMENT ON COLUMN sys_admin_roles.updated_by IS 'Admin ID who last updated this record';
COMMENT ON COLUMN sys_admin_roles.deleted IS 'Soft delete marker (FALSE = active, TRUE = deleted)';

-- ============================================================================
-- Table: sys_admin_menus
-- Purpose: Define menu structure for admin panel
-- ============================================================================
CREATE TABLE sys_admin_menus (
    -- Primary Key (TSID)
    id BIGINT PRIMARY KEY,

    -- Menu fields
    name VARCHAR(50) NOT NULL,
    path VARCHAR(255),
    icon VARCHAR(50),
    parent_id BIGINT,
    sort_order INT NOT NULL,
    type INTEGER NOT NULL,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,

    -- Soft delete marker
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- Add table comment
COMMENT ON TABLE sys_admin_menus IS 'Menu structure for admin panel navigation';

-- Add column comments
COMMENT ON COLUMN sys_admin_menus.id IS 'Primary key (TSID)';
COMMENT ON COLUMN sys_admin_menus.name IS 'Menu display name';
COMMENT ON COLUMN sys_admin_menus.path IS 'Menu path/URL';
COMMENT ON COLUMN sys_admin_menus.icon IS 'Menu icon name';
COMMENT ON COLUMN sys_admin_menus.parent_id IS 'Parent menu ID (self-reference to sys_admin_menus.id)';
COMMENT ON COLUMN sys_admin_menus.sort_order IS 'Display order in UI';
COMMENT ON COLUMN sys_admin_menus.type IS 'Menu type (1=MENU, 2=GROUP, 3=DIVIDER) - BaseEnum Integer code';
COMMENT ON COLUMN sys_admin_menus.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN sys_admin_menus.updated_at IS 'Record last update timestamp';
COMMENT ON COLUMN sys_admin_menus.created_by IS 'Admin ID who created this record';
COMMENT ON COLUMN sys_admin_menus.updated_by IS 'Admin ID who last updated this record';
COMMENT ON COLUMN sys_admin_menus.deleted IS 'Soft delete marker (FALSE = active, TRUE = deleted)';

-- ============================================================================
-- Table: sys_admin_user_roles
-- Purpose: Many-to-many relationship between users and roles
-- ============================================================================
CREATE TABLE sys_admin_user_roles (
    -- Primary Key (TSID)
    id BIGINT PRIMARY KEY,

    -- Foreign keys
    admin_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT
);

-- Add table comment
COMMENT ON TABLE sys_admin_user_roles IS 'Many-to-many relationship between admin users and roles';

-- Add column comments
COMMENT ON COLUMN sys_admin_user_roles.id IS 'Primary key (TSID)';
COMMENT ON COLUMN sys_admin_user_roles.admin_id IS 'Foreign key to sys_admin_users.id';
COMMENT ON COLUMN sys_admin_user_roles.role_id IS 'Foreign key to sys_admin_roles.id';
COMMENT ON COLUMN sys_admin_user_roles.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN sys_admin_user_roles.created_by IS 'Admin ID who created this assignment';

-- ============================================================================
-- Table: sys_admin_role_menus
-- Purpose: Many-to-many relationship between roles and menus
-- ============================================================================
CREATE TABLE sys_admin_role_menus (
    -- Primary Key (TSID)
    id BIGINT PRIMARY KEY,

    -- Foreign keys
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT
);

-- Add table comment
COMMENT ON TABLE sys_admin_role_menus IS 'Many-to-many relationship between roles and menus';

-- Add column comments
COMMENT ON COLUMN sys_admin_role_menus.id IS 'Primary key (TSID)';
COMMENT ON COLUMN sys_admin_role_menus.role_id IS 'Foreign key to sys_admin_roles.id';
COMMENT ON COLUMN sys_admin_role_menus.menu_id IS 'Foreign key to sys_admin_menus.id';
COMMENT ON COLUMN sys_admin_role_menus.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN sys_admin_role_menus.created_by IS 'Admin ID who created this assignment';

-- ============================================================================
-- Table: sys_admin_role_permissions
-- Purpose: Store role permissions for fine-grained access control
-- ============================================================================
CREATE TABLE sys_admin_role_permissions (
    -- Primary Key (TSID)
    id BIGINT PRIMARY KEY,

    -- Foreign key
    role_id BIGINT NOT NULL,

    -- Permission fields
    permission_code VARCHAR(100) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT
);

-- Add table comment
COMMENT ON TABLE sys_admin_role_permissions IS 'Role permissions for fine-grained access control';

-- Add column comments
COMMENT ON COLUMN sys_admin_role_permissions.id IS 'Primary key (TSID)';
COMMENT ON COLUMN sys_admin_role_permissions.role_id IS 'Foreign key to sys_admin_roles.id';
COMMENT ON COLUMN sys_admin_role_permissions.permission_code IS 'Permission code (e.g., user:create, user:update)';
COMMENT ON COLUMN sys_admin_role_permissions.permission_name IS 'Permission display name';
COMMENT ON COLUMN sys_admin_role_permissions.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN sys_admin_role_permissions.created_by IS 'Admin ID who granted this permission';
