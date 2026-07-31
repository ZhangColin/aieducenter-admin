# ADR-0004：菜单模型完全采用 Soybean 路由生成器（推翻 REQ-1 三值模型）

- **状态**：Accepted；待实现（REQ-8，issue #11）
- **日期**：2026-07-31
- **关联**：[issue #11](https://github.com/ZhangColin/aieducenter-admin/issues/11) REQ-8；**推翻 REQ-1**（[issue #1](https://github.com/ZhangColin/aieducenter-admin/issues/1)）与 CONTEXT.md 旧「菜单树与节点类型」术语；**作废 REQ-6** Material Symbols 图标约定；术语见 [CONTEXT.md「菜单」](../../CONTEXT.md)

## 背景

REQ-1（几天前刚交付）把菜单类型定为 **`MENU`(1)/`GROUP`(2)/`DIVIDER`(3)** 三值：MENU=可路由叶子(path 必填)、GROUP=分组容器(path=null)、DIVIDER=同级分隔线。配套落地了一整套：聚合不变量 `AdminMenu.applyTypeAndPath`（`ADMIN_014_3`：MENU 必有 path、GROUP/DIVIDER path=null）、`MenuTreeAssembler` 的可见性派生（祖先链补全 + 排序 + 裁空 GROUP / 悬空 DIVIDER）、V5 种子菜单（按 GROUP 结构建）。REQ-6 又定了图标用 Material Symbols。

现选 **Soybean Admin v2 做 admin 配套前端**。Soybean 的菜单是其**路由生成器**（`@elegant-router`）的数据源——每条菜单带 Vue Router 渲染所需的全部元数据，CRUD 编辑、动态路由读取。而 Soybean 的 `menuType` 枚举**只有 `directory(1)` / `menu(2)` 两值，没有 DIVIDER**；图标用 **iconify + `iconType`**（`SvgIcon` 原生渲染），非 Material Symbols。

用户 2026-07-31 拍板总原则："选了 Soybean 做后台就是完全配套"——Soybean 的系统管理 = 功能规约，菜单字段/类型/图标一律以 Soybean 源码为准，后端不自创。

## 决策

**菜单模型完全照 Soybean；本项目原三值模型作废、当不存在。**

1. `AdminMenu` 扩成 Soybean 路由生成器全字段：`menuType`(directory/menu)、`routeName`、`component`、`i18nKey`、`icon`+`iconType`、`order`、`keepAlive`/`constant`/`multiTab`/`hideInMenu`、`activeMenu`、`href`、`fixedIndexInTab`、`query`、`buttons`(REQ-9)、`status`。
2. **type / path / icon 的一切语义以 Soybean 源码为准**，后端不再自创不变量。旧 `applyTypeAndPath` / `ADMIN_014_3` path 规则、`MenuTreeAssembler` 的 DIVIDER 裁剪分支、REQ-6 的 Material Symbols 约定——**全部作废**。
3. **V6 迁移重建种子菜单**（V5 的 GROUP 结构作废），icon 改 iconify id（如 `mdi:xxx`）。
4. 端点：扁平分页 `GET /menus`（Soybean 菜单表格，集合根分页、对齐 `/users`/`/roles`）+ 树 `GET /menus/tree`（父级选择器/角色分配）。页名选择器由前端构建期派生，不向后端要。

## 否决

- **保留 DIVIDER 兼容旧 nav-tree**：Soybean menuType 无此概念，留着是遗物，徒增前后端模型分歧。
- **directory 的 path 强制 null（= 旧 GROUP 换名）**：偏离 Soybean（其 directory 作路由前缀容器通常带 path）；用户明确"完全配套"，后端不自创语义。
- **新旧两套模型渐进并存**：双模型 + 双端点语义更乱；菜单尚未大规模投产，一次切换成本最低。

## 结果

- ✅ 菜单与 Soybean 路由生成器严格配套，前端零适配摩擦。
- ⚠️ 代价：推翻刚交付的 REQ-1（DIVIDER / path 不变量 / 树裁剪逻辑）与 REQ-6（图标）。可接受——尚未大规模投产，且总原则已定。
- 📋 实现清单：`MenuType` 改两值 → `AdminMenu` 扩字段 → 删 `applyTypeAndPath` 旧不变量 → `MenuTreeAssembler` 删 DIVIDER 裁剪 → `MenuResponse`/`Create|UpdateMenuCommand` 扩字段 → V6 重建种子 → 改写相关测试 → CONTEXT.md 术语已重写。

## 参见

- [CONTEXT.md「菜单 = Soybean 路由生成器数据源」](../../CONTEXT.md)
- REQ-1：[issue #1](https://github.com/ZhangColin/aieducenter-admin/issues/1)（被本 ADR 推翻）
- 完整需求：前端仓 `docs/backend-requirements/REQ-8-soybean-system-mgmt-full-alignment.md`
