# admin-web 同步稿：平台账号管理 UI（配合 admin BFF #49）

> 来源：admin [issue #49](https://github.com/ZhangColin/aieducenter-admin/issues/49) grill 定稿（spec：`.scratch/account-admin/spec.md`）。
> 这是 admin-web 侧需配合做的 UI 清单。后端契约（DTO 字段）最终以 identity #67–#72 实现为准。

## 背景

admin 后端在建「平台账号管理」BFF（消费 identity 的 `/api/account/*` 管理端点），admin-web 需配合做前端页面。**管的是终端用户账号（Account），不是后台员工（Operator/系统管理）**——两者勿混。

## 菜单（后端 V14 种子，admin-web 路由生成器对齐）

| 菜单 | route_name | route_path | component | icon | i18n_key | menu_type | sort_order |
|---|---|---|---|---|---|---|---|
| 账号管理 | `account` | `/account` | `layout.base` | `carbon:user` | `route.account` | directory | 4 |
| 账号列表 | `account_list` | `/account/list` | `view.account_list` | `carbon:user-multiple` | `route.account_list` | menu | 1 |

- 目录「账号管理」sort=4，位于「支付管理(3)」与「系统管理(99)」之间。
- **账号详情不进菜单**——走列表页内的**抽屉/弹窗**（与 payment 详情同模式，见 admin CONTEXT.md「详情页 = 弹窗/抽屉优先」）。
- route_name / component / icon 为后端提案，前端可对齐微调。

## 列表页（`/account/list`）

- 分页搜索，筛选条件：email / phone / userId / status / locked / 注册时间区间。
- 分页请求/响应形状与现有列表（`/apps`、`/payment/order`）一致，复用既有分页组件。
- 行操作：查看详情（开抽屉）。

## 详情抽屉

- 展示 identity management 详情字段（状态 / 锁定 / 资料 等——最终字段以 identity #67 详情契约为准）。
- 抽屉内操作按钮区（见下）。

## 操作按钮（按权限 claims 显隐）

后端端点（北向 `/api/admin/accounts/**`）：

| 操作 | 端点 | 权限码（预案） | 备注 |
|---|---|---|---|
| 封号 | `POST /accounts/{userId}/disable` body `{reason}` | `admin:account:write` | reason 必填；identity 自动踢所有会话 |
| 解封 | `POST /accounts/{userId}/activate` | `admin:account:write` | |
| 解除系统锁定 | `POST /accounts/{userId}/unlock` | `admin:account:write` | 解除登录失败次数等触发的系统锁，区别于封号 |
| 强制下线 | `POST /accounts/{userId}/sessions/revoke` | `admin:account:write` | 一键 revoke 全部会话、**不改状态**；无会话列表 |

- 读权限 `admin:account:read`：列表 / 详情。
- 写权限 `admin:account:write`（预案，后端与 payment 权限统一时可能拆为 status / session）——前端按 `/auth/current` 的 permission claims 自控按钮显隐（REQ-9 button 级元数据仍搁置，走 claims）。
- 封号需弹窗填 reason；其余操作二次确认。

## 明确不做（勿在 UI 里出现）

- ❌ **密码相关**：重置密码 / 清密码 / 强制改密——admin 不处理终端用户密码（合规边界，admin [ADR-0008](https://github.com/ZhangColin/aieducenter-admin/blob/develop/docs/adr/0008-admin-does-not-handle-end-user-passwords.md)）。identity 提供这三个端点，但 admin 不暴露、前端也不要做入口。
- ❌ **会话列表视图**：identity 无会话列表端点；强制下线仅一个「一键下线」按钮。
- ❌ **账号仪表盘/统计**：v1 不做（需求由后台反向提）。
- ❌ **创建账号/注册**：identity 域职责，非后台。

## 依赖

- 后端 spec 契约锁定（本稿即锁定稿，字段级待 identity #67–#72）。
- identity #67（gate+详情）/ #70（搜索）契约为联调前提；UI 可先按本稿搭壳。
