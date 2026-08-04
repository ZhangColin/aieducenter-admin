-- ============================================================================
-- V11: Seed menu —— 应用详情（hideInMenu 详情页路由注册）
--       (REQ-15-T5 / issue #30)
-- ============================================================================
-- Purpose: 插入「应用详情」叶子菜单，hideInMenu=true 使其不出现在 sidebar
--          但由 GET /menus/my 返回以驱动前端 vue-router 注册详情页路由。
-- Context: Admin (sys_)
-- ============================================================================
-- Background (REQ-15-T5, issue #30):
--   前端动态路由模式下，路由由 GET /menus/my 返回的菜单树驱动注册。
--   app_detail（/app/list/:id，应用详情页）是 hideInMenu: true 的非菜单详情页，
--   不在后端种子菜单中 → /menus/my 不返回 → vue-router 未注册 → 详情按钮无反应。
--   本迁移补一条 app_detail 种子记录，挂 app directory(id=40) 下。
--
--   ID 分配：id=90，与 app(40)/app_list(70) 同一 ID 空间，取未用值。
--
--   幂等策略：INSERT ... ON CONFLICT (id) DO NOTHING（重复执行安全）。
-- ============================================================================

INSERT INTO sys_admin_menus (
    id, menu_name, route_name, route_path, component,
    icon, icon_type, i18n_key,
    parent_id, sort_order, menu_type, status, keep_alive, hide_in_menu,
    created_at, updated_at
) VALUES (
    90,
    '应用详情',
    'app_detail',
    '/app/list/:id',
    'view.app_detail',
    'carbon:application',
    1,  -- iconify
    'route.app_detail',
    40,
    2,
    2,  -- menu
    1,  -- active
    FALSE,
    TRUE,  -- hideInMenu: sidebar 不可见，URL 可达
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
