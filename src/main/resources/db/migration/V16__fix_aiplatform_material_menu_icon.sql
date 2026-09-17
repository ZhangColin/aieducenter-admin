-- ============================================================================
-- V16: Fix AI 平台「知识素材」菜单图标 —— carbon:knowledge-base 在 iconify 不存在
-- ============================================================================
-- Purpose: 将 id=210（aiplatform_material）菜单的 icon 修正为 iconify carbon 集中
--          真实存在的 carbon:document-multiple-01（文档堆叠，契合「素材」语义）。
-- Context: Admin (sys_)
-- ============================================================================
-- Background: V15 种子为知识素材菜单选了 carbon:knowledge-base，但该名不在
--   iconify carbon 图标集（api.iconify.design/carbon/knowledge-base.svg → 404），
--   前端 iconify 渲染落空、侧边栏该项无图标（同批其余六枚图标均 200 存在）。
--   V15 已随 T10 落库、Flyway 迁移不回头改，故以新迁移 UPDATE 修正；替代名
--   经 iconify API 校验存在（200）。
-- ============================================================================

UPDATE sys_admin_menus
SET icon = 'carbon:document-multiple-01', updated_at = CURRENT_TIMESTAMP
WHERE id = 210;
