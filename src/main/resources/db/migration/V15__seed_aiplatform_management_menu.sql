-- ============================================================================
-- V15: Seed menus —— AI 平台 directory + 六域叶子（订单/项目/沙箱/成本/单价表/
--      知识素材）
--       (aiplatform BFF T10 收口 / issue #72)
-- ============================================================================
-- Purpose: 插入「AI 平台」目录及其下六域叶子菜单，承载 aiplatform admin BFF 的侧边栏
--          导航——六域 34 端点（spec #62）自此对运营在统一后台一次全种齐。
-- Context: Admin (sys_)
-- ============================================================================
-- Background (aiplatform BFF spec, issue #62 / CONTEXT.md「AI 平台菜单」):
--   admin 作为 BFF 聚合 aiplatform backoffice 六域（订单/项目/沙箱/成本/知识素材/单价表），
--   前端 Soybean 路由需要「AI 平台」目录 (directory, /aiplatform) 作为六域页面的路由前缀
--   容器，其下挂 6 个叶子菜单。账号读口（按 externalId 极简档案）不种页面——嵌订单/项目
--   详情抽屉使用（CONTEXT.md「详情页 = 弹窗/抽屉优先」），权限码 admin:aiplatform:account:read
--   照常独立。订单/项目/沙箱/素材详情同为抽屉，不种菜单。菜单模型照 Soybean（ADR-0004）。
--
--   ID 分配沿用保留段（已用：10/20/30/40/50/60/70/80(payment)/81(account)/90-已删/
--   100-140(payment)/150(account)）：
--     id=82  AI 平台        directory  (sort=3，与 payment 同序、id 82>80 兜底排其后——
--                                    应用管理 2 之后、账号管理 4/系统管理 99 之前)
--     id=160 订单管理        menu       (sort=1)
--     id=170 项目管理        menu       (sort=2)
--     id=180 沙箱管理        menu       (sort=3)
--     id=190 成本中心        menu       (sort=4)
--     id=200 单价表          menu       (sort=5)
--     id=210 知识素材        menu       (sort=6)
--
--   SUPER_ADMIN 经框架 bypass 见全树，无需 role-menu 种子行。
--   幂等策略：INSERT ... ON CONFLICT (id) DO NOTHING（重复执行安全）。
--
--   不写种子快照测试（V12 已删、脆性高）；迁移正确性由 RoleAssignmentFlywaySchemaIntegrationTest
--   在独立 schema 跑全量 Flyway 链兜底。种子可见性（消费面行为）由
--   AiplatformMenuSeedVisibilityIntegrationTest 以 HTTP 外部行为钉住（非快照比对）。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) AI 平台 directory（id=82）—— 路由前缀容器
-- ----------------------------------------------------------------------------
INSERT INTO sys_admin_menus (
    id, menu_name, route_name, route_path, component,
    icon, icon_type, i18n_key,
    parent_id, sort_order, menu_type, status, keep_alive,
    created_at, updated_at
) VALUES (
    82,
    'AI 平台',
    'aiplatform',
    '/aiplatform',
    'layout.base',
    'carbon:machine-learning-model',
    1,  -- iconify
    'route.aiplatform',
    NULL,
    3,
    1,  -- directory
    1,  -- active
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2) 订单管理 menu（id=160）—— sort=1：订单清单/详情/源码包下载 + 报价改价/取消/重试归档
-- ----------------------------------------------------------------------------
INSERT INTO sys_admin_menus (
    id, menu_name, route_name, route_path, component,
    icon, icon_type, i18n_key,
    parent_id, sort_order, menu_type, status, keep_alive,
    created_at, updated_at
) VALUES (
    160,
    '订单管理',
    'aiplatform_order',
    '/aiplatform/order',
    'view.aiplatform_order',
    'carbon:shopping-cart',
    1,  -- iconify
    'route.aiplatform_order',
    82,
    1,
    2,  -- menu
    1,  -- active
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3) 项目管理 menu（id=170）—— sort=2：项目清单/详情 + 对话史/PRD/版本（详情抽屉内 tab）
-- ----------------------------------------------------------------------------
INSERT INTO sys_admin_menus (
    id, menu_name, route_name, route_path, component,
    icon, icon_type, i18n_key,
    parent_id, sort_order, menu_type, status, keep_alive,
    created_at, updated_at
) VALUES (
    170,
    '项目管理',
    'aiplatform_project',
    '/aiplatform/project',
    'view.aiplatform_project',
    'carbon:catalog',
    1,  -- iconify
    'route.aiplatform_project',
    82,
    2,
    2,  -- menu
    1,  -- active
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 4) 沙箱管理 menu（id=180）—— sort=3：沙箱清单/详情（漂移观测）+ 唤醒/休眠/重建/封存
-- ----------------------------------------------------------------------------
INSERT INTO sys_admin_menus (
    id, menu_name, route_name, route_path, component,
    icon, icon_type, i18n_key,
    parent_id, sort_order, menu_type, status, keep_alive,
    created_at, updated_at
) VALUES (
    180,
    '沙箱管理',
    'aiplatform_workspace',
    '/aiplatform/workspace',
    'view.aiplatform_workspace',
    'carbon:virtual-machine',
    1,  -- iconify
    'route.aiplatform_workspace',
    82,
    3,
    2,  -- menu
    1,  -- active
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 5) 成本中心 menu（id=190）—— sort=4：全局总览/unpriced 警示/项目成本清单/单项目下钻
-- ----------------------------------------------------------------------------
INSERT INTO sys_admin_menus (
    id, menu_name, route_name, route_path, component,
    icon, icon_type, i18n_key,
    parent_id, sort_order, menu_type, status, keep_alive,
    created_at, updated_at
) VALUES (
    190,
    '成本中心',
    'aiplatform_cost',
    '/aiplatform/cost',
    'view.aiplatform_cost',
    'carbon:analytics',
    1,  -- iconify
    'route.aiplatform_cost',
    82,
    4,
    2,  -- menu
    1,  -- active
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 6) 单价表 menu（id=200）—— sort=5：单价清单（含历史行）+ 原子改价/停用（开行不暴露）
-- ----------------------------------------------------------------------------
INSERT INTO sys_admin_menus (
    id, menu_name, route_name, route_path, component,
    icon, icon_type, i18n_key,
    parent_id, sort_order, menu_type, status, keep_alive,
    created_at, updated_at
) VALUES (
    200,
    '单价表',
    'aiplatform_price_entry',
    '/aiplatform/price-entry',
    'view.aiplatform_price_entry',
    'carbon:currency',
    1,  -- iconify
    'route.aiplatform_price_entry',
    82,
    5,
    2,  -- menu
    1,  -- active
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 7) 知识素材 menu（id=210）—— sort=6：素材清单/详情 + 停用⇄启用/删除
-- ----------------------------------------------------------------------------
INSERT INTO sys_admin_menus (
    id, menu_name, route_name, route_path, component,
    icon, icon_type, i18n_key,
    parent_id, sort_order, menu_type, status, keep_alive,
    created_at, updated_at
) VALUES (
    210,
    '知识素材',
    'aiplatform_material',
    '/aiplatform/material',
    'view.aiplatform_material',
    'carbon:knowledge-base',
    1,  -- iconify
    'route.aiplatform_material',
    82,
    6,
    2,  -- menu
    1,  -- active
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
