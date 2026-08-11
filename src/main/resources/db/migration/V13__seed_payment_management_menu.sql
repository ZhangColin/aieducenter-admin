-- ============================================================================
-- V13: Seed menus —— 支付管理 directory + 5 叶子（统计概览/支付订单/退款订单/
--      通道交互日志/订单操作记录）
--       (payment-admin T1 / issue #39)
-- ============================================================================
-- Purpose: 插入「支付管理」目录及其下 5 个叶子菜单，承载 payment admin BFF 的侧边栏导航。
-- Context: Admin (sys_)
-- ============================================================================
-- Background (payment-admin spec, issue #38 / CONTEXT.md「支付管理菜单」):
--   前端 Soybean 路由需要「支付管理」目录 (directory, /payment) 作为支付相关页面的
--   路由前缀容器，其下挂 5 个叶子菜单。统计概览 sort_order=1 前置（运营看板优先）。
--   支付/退款详情不种菜单（前端弹窗/抽屉，见 CONTEXT.md「详情页 = 弹窗/抽屉优先」）。
--
--   ID 分配沿用保留段（已用：10/20/30/40/50/60/70；90 已删）：
--     id=80  支付管理        directory  (sort=3，夹应用管理 2 与系统管理 99 之间)
--     id=100 统计概览        menu       (sort=1)
--     id=110 支付订单        menu       (sort=2)
--     id=120 退款订单        menu       (sort=3)
--     id=130 通道交互日志    menu       (sort=4)
--     id=140 订单操作记录    menu       (sort=5)
--
--   SUPER_ADMIN 经框架 bypass 见全树，无需 role-menu 种子行。
--   幂等策略：INSERT ... ON CONFLICT (id) DO NOTHING（重复执行安全）。
--
--   不写种子快照测试（V12 已删、脆性高）；迁移正确性由 RoleAssignmentFlywaySchemaIntegrationTest
--   在独立 schema 跑全量 Flyway 链兜底。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) 支付管理 directory（id=80）—— 路由前缀容器
-- ----------------------------------------------------------------------------
INSERT INTO sys_admin_menus (
    id, menu_name, route_name, route_path, component,
    icon, icon_type, i18n_key,
    parent_id, sort_order, menu_type, status, keep_alive,
    created_at, updated_at
) VALUES (
    80,
    '支付管理',
    'payment',
    '/payment',
    'layout.base',
    'carbon:finance',
    1,  -- iconify
    'route.payment',
    NULL,
    3,
    1,  -- directory
    1,  -- active
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2) 统计概览 menu（id=100）—— sort=1 前置
-- ----------------------------------------------------------------------------
INSERT INTO sys_admin_menus (
    id, menu_name, route_name, route_path, component,
    icon, icon_type, i18n_key,
    parent_id, sort_order, menu_type, status, keep_alive,
    created_at, updated_at
) VALUES (
    100,
    '统计概览',
    'payment_stats',
    '/payment/stats',
    'view.payment_stats',
    'carbon:dashboard',
    1,  -- iconify
    'route.payment_stats',
    80,
    1,
    2,  -- menu
    1,  -- active
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3) 支付订单 menu（id=110）
-- ----------------------------------------------------------------------------
INSERT INTO sys_admin_menus (
    id, menu_name, route_name, route_path, component,
    icon, icon_type, i18n_key,
    parent_id, sort_order, menu_type, status, keep_alive,
    created_at, updated_at
) VALUES (
    110,
    '支付订单',
    'payment_order',
    '/payment/order',
    'view.payment_order',
    'carbon:currency',
    1,  -- iconify
    'route.payment_order',
    80,
    2,
    2,  -- menu
    1,  -- active
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 4) 退款订单 menu（id=120）
-- ----------------------------------------------------------------------------
INSERT INTO sys_admin_menus (
    id, menu_name, route_name, route_path, component,
    icon, icon_type, i18n_key,
    parent_id, sort_order, menu_type, status, keep_alive,
    created_at, updated_at
) VALUES (
    120,
    '退款订单',
    'payment_refund',
    '/payment/refund',
    'view.payment_refund',
    'carbon:currency-refund',
    1,  -- iconify
    'route.payment_refund',
    80,
    3,
    2,  -- menu
    1,  -- active
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 5) 通道交互日志 menu（id=130）—— payment PaymentLog（与银行/通道网关的机机交互留痕）
-- ----------------------------------------------------------------------------
INSERT INTO sys_admin_menus (
    id, menu_name, route_name, route_path, component,
    icon, icon_type, i18n_key,
    parent_id, sort_order, menu_type, status, keep_alive,
    created_at, updated_at
) VALUES (
    130,
    '通道交互日志',
    'payment_channel_log',
    '/payment/channel-log',
    'view.payment_channel_log',
    'carbon:exchange',
    1,  -- iconify
    'route.payment_channel_log',
    80,
    4,
    2,  -- menu
    1,  -- active
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 6) 订单操作记录 menu（id=140）—— payment OperationLog（行为者对订单的操作留痕）
-- ----------------------------------------------------------------------------
INSERT INTO sys_admin_menus (
    id, menu_name, route_name, route_path, component,
    icon, icon_type, i18n_key,
    parent_id, sort_order, menu_type, status, keep_alive,
    created_at, updated_at
) VALUES (
    140,
    '订单操作记录',
    'payment_operation',
    '/payment/operation',
    'view.payment_operation',
    'carbon:activity',
    1,  -- iconify
    'route.payment_operation',
    80,
    5,
    2,  -- menu
    1,  -- active
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
