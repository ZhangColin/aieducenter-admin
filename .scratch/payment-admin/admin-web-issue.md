## 背景

admin-bff 侧的「支付后台管理」spec 已定稿：[ZhangColin/aieducenter-admin#38](https://github.com/ZhangColin/aieducenter-admin/issues/38)（parent [#37](https://github.com/ZhangColin/aieducenter-admin/issues/37)），端点 + 入参/出参契约已锁定。本 issue 是 **admin-web 侧的前端页面**实现。

## 要做什么

按 admin-bff 的 V13 菜单 + 端点契约，实现「支付管理」一级目录及其页面。

### 1. 菜单 / 路由（V13 元数据，admin-bff 已种、经 `/menus/tree` 下发）

| 菜单 | route_name | route_path | component | icon | i18n_key | sort |
|---|---|---|---|---|---|---|
| 支付管理（目录） | `payment` | `/payment` | `layout.base` | `carbon:finance` | `route.payment` | 3 |
| 统计概览 | `payment_stats` | `/payment/stats` | `view.payment_stats` | `carbon:dashboard` | `route.payment_stats` | 1 |
| 支付订单 | `payment_order` | `/payment/order` | `view.payment_order` | `carbon:currency` | `route.payment_order` | 2 |
| 退款订单 | `payment_refund` | `/payment/refund` | `view.payment_refund` | `carbon:currency-refund` | `route.payment_refund` | 3 |
| 通道交互日志 | `payment_channel_log` | `/payment/channel-log` | `view.payment_channel_log` | `carbon:exchange` | `route.payment_channel_log` | 4 |
| 订单操作记录 | `payment_operation` | `/payment/operation` | `view.payment_operation` | `carbon:activity` | `route.payment_operation` | 5 |

- 统计概览前置（sort=1），点「支付管理」目录默认落统计概览。
- **详情页不种菜单**：支付/退款详情走**列表页内弹窗/抽屉**（参考应用管理的 720px Modal 模式，[admin-web#32](https://github.com/ZhangColin/aieducenter-admin-web/issues/32)）；「订单生命周期」作为详情抽屉内的一个 tab。

### 2. 端点契约

完整契约见 [admin-bff#38](https://github.com/ZhangColin/aieducenter-admin/issues/38) 的 Implementation Decisions。端点面 `/api/admin/payment/**`：
- **列表**（分页+筛选，形状对齐 `/apps`）：`/payments` / `/refunds` / `/payment-logs`（通道交互日志）/ `/operation-logs`（订单操作记录）
- **详情/生命周期**：`/payments/{no}` / `/refunds/{no}` / `/orders/{no}/lifecycle`
- **写**：`POST /refunds/{no}/audit`（approve/reject）、`POST /payments/{no}/notifications/resend`、`POST /refunds/{no}/notifications/resend`
- **统计**：tier-1（overview / status-distribution / gateway-health / operations-audit）+ tier-2（by-business-system / by-channel / anomalies / operations-activity）

### 3. 按钮显隐（按 permission claims，非菜单元数据）

REQ-9 的 menu `buttons` 字段搁置（[admin#18](https://github.com/ZhangColin/aieducenter-admin/issues/18)）。前端按 `/auth/current` 返回的 permission claims 自控写按钮显隐：
- `admin:payment:refund:audit` → 退款审核按钮
- `admin:payment:notification:resend` → 通知重发按钮
- `admin:payment:bank:query` → 主动查行按钮（**仅当 admin-bff 决定暴露**，待 [admin#46](https://github.com/ZhangColin/aieducenter-admin/issues/46) 决策；不暴露则不渲染）
- `admin:payment:read` → 所有只读页面的访问

### 4. 仪表盘

「统计概览」**单页多区块**，消费 tier-1（+ tier-2）stats 端点。架构上 widget 不必与单一端点 1:1——日后可能跨服务聚合（payment/钱包/Token），前端按区块设计、留组合余地。

## 依赖

- admin-bff 端点实现按 [admin#38 的 ticket 链](https://github.com/ZhangColin/aieducenter-admin/issues/38)：T1（[#39](https://github.com/ZhangColin/aieducenter-admin/issues/39) = 菜单 + 支付订单列表）先行，其余按依赖序。前端可待 T1 落地后启动脚手架（菜单 + 列表页先通）。
- payment 侧 #9–#18 落地后方可端到端联调。

## 不在范围

- admin-bff 后端实现（[admin#38](https://github.com/ZhangColin/aieducenter-admin/issues/38) 票集 #39–#48）
- payment 服务侧（payment 仓 #9–#18）
