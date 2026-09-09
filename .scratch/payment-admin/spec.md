# 支付后台管理 BFF 实现 spec（消费 payment 契约，源 #37）

## Problem Statement

统一后台（admin）目前**没有任何支付运营管理面**。运营人员（Operator）无法在后台：查看支付/退款订单、审核退款、补发失败的通知、排查银行通道问题、或看到支付健康度。payment 是一个能力域，对操作员没有可见的管理入口。

后果：退款审核无人处理、通知失败无法补救、通道异常无可视化、运营对支付状态零可见。本 spec 在 admin 内建 **payment admin BFF**，把 payment 的能力以**运营可操作**的形态聚合到统一后台。

## Solution

在 admin 现有单上下文内新增 **`payment` 子包**（支付管理上下文，≠ 财务上下文），作为 BFF：admin 以自身 `admin-console` 身份经 cartisan-openapi 签名调用 payment，对前端暴露 `/api/admin/payment/**` 一组端点，种 V13 菜单（「支付管理」目录 + 5 叶子），按权限码控写操作，并在写请求体里透传当前 Operator 身份。**业务逻辑与审计归 payment**（system of record），admin 只做调接口 + DTO 转换 + 聚合（见 [ADR-0007](../../docs/adr/0007-admin-bff-outbound-clients-are-flat-components.md)：BFF 出站客户端为裸 `@Component`）。

## User Stories

**读 · 列表**
1. 作为 Operator，我想分页查看支付订单列表，以便定位具体订单。
2. 作为 Operator，我想按 paymentOrderNo / businessOrderNo / businessSystemName / status(多选) / payMode / accessType / paymentChannel / 金额区间 / createdAt 区间 / paidAt 区间 筛选支付订单。
3. 作为 Operator，我想分页查看退款订单列表，以便处理退款。
4. 作为 Operator，我想按 refundOrderNo / paymentOrderNo / businessOrderNo / businessSystemName / status / auditType / auditorId / 退款金额区间 / createdAt 区间 筛选退款订单。
5. 作为 Operator，我想查看**通道交互日志**（payment `PaymentLog`：与银行/通道网关的机机交互留痕），以便排查通道问题。
6. 作为 Operator，我想按 paymentOrderNo / refundOrderNo / logType(多选) / bankInterface / success / returnCode / createdAt 区间 筛选通道交互日志。
7. 作为 Operator，我想查看**订单操作记录**（payment `OperationLog`：行为者对订单的操作留痕），以便合规追溯。
8. 作为 Operator，我想按 targetType / targetNo / operation(多选) / operatorId / operatorSystem / result / createdAt 区间 筛选订单操作记录。
9. 作为 Operator，我想让所有列表的分页请求/响应形状与 admin 现有列表端点（如 `/apps`）一致，以便前端统一处理。

**读 · 详情与生命周期**
10. 作为 Operator，我想查看支付订单的完整详情（聚合），以便了解订单全貌。
11. 作为 Operator，我想查看退款订单的完整详情。
12. 作为 Operator，我想查看一个订单的**生命周期视图**（按时间合并该单的 PaymentLog + OperationLog），以便端到端追溯发生了什么。
13. 作为 Operator，我想从列表页以**弹窗/抽屉**打开支付/退款详情（而非侧边栏独立路由），以便快速查看不打断列表上下文。

**写 · 运营操作**
14. 作为持有 `admin:payment:refund:audit` 权限的 Operator，我想**通过**一个退款，以便退款继续执行。
15. 作为持有 `admin:payment:refund:audit` 权限的 Operator，我想**拒绝**一个退款，以便驳回。
16. 作为审核人，我想让我的 Operator 身份（id + name）作为 auditor 落在 payment 的 `OperationLog`（`auditType=MANUAL`），以便审核可归属。
17. 作为持有 `admin:payment:notification:resend` 权限的 Operator，我想重发某支付单的结果通知给业务系统，以便补救漏投。
18. 作为持有 `admin:payment:notification:resend` 权限的 Operator，我想重发某退款单的结果通知。
19. 作为 Operator，我想让通知重发**不改订单状态**（仅补发投递，非施加状态——payment ADR-0001）。

**统计 · 仪表盘**
20. 作为 Operator，我想看支付总览（支付/退款笔数·金额·成功率·净额，按时间分桶趋势），以便一眼掌握支付健康度。
21. 作为 Operator，我想看订单状态分布（各状态在途笔数·金额 + 退款待审核积压）。
22. 作为 Operator，我想看通道健康（各银行接口调用次数·成功率·平均耗时·返回码分布）。
23. 作为 Operator，我想看审核统计（审核笔数·通过率·平均审核时长·按审核人聚合）。
24. 作为 Operator，当 payment 实现 tier-2 后，我想看按业务系统 / 按通道 / 异常 / 操作员活动 的细分统计。

**权限**
25. 作为超管（SUPER_ADMIN），我想**自动**看到「支付管理」全树（经框架 bypass），无需显式角色-菜单分配。
26. 作为**无** `refund:audit` 权限的 Operator，我想被禁止审核（端点 403 / 前端按钮隐藏）。
27. 作为**无** `notification:resend` 权限的 Operator，同上禁止重发。
28. 作为只读 Operator，我想能访问所有列表/详情/仪表盘，但看不到任何写操作按钮。

**集成 / 基础设施**
29. 作为 admin 系统，我想以 `admin-console` 身份对出站调用签名，以便 payment 验签识别我。
30. 作为 admin 系统，我想把当前 Operator 身份放进**写请求体**（`auditorId`/`auditorName`，非 header），以便 payment 不感知"这是 admin-bff"。
31. 作为 admin 系统，我想把 payment 的错误翻译成 admin 的 `ADMIN_*` 错误码（携带正确 HTTP 状态），以便前端拿到一致错误。
32. 作为 admin 系统，我想依赖 payment 的 `OperationLog` 作为操作审计的权威源，以便无双源不一致。

**主动查行（可选）**
33. 作为持有 `admin:payment:bank:query` 权限的 Operator（若暴露），我想触发主动查行以把本地状态对齐银行真相。

**演进**
34. 作为未来需求，仪表盘 widget 可能跨多个服务（payment/钱包/Token）取数；BFF 架构应**允许跨服务聚合**，不把「payment 端点 ↔ widget」焊死成 1:1。

## Implementation Decisions

**上下文与边界**
- 新增 `payment` 子包（`com.aieducenter.admin.payment`），落 admin 现有单上下文；**暂不**引入 `CONTEXT-MAP` 多上下文拆分。
- **支付管理上下文 ≠ 财务上下文**：前者运营写操作 + 运营看板；后者只读收入确认（未建）。
- admin 角色 = BFF（调接口 + DTO 转换 + 聚合），不持业务逻辑、不记业务审计。

**菜单种子 V13**（新 Flyway 迁移，`ON CONFLICT (id) DO NOTHING`，不写种子快照测试，SUPER_ADMIN 经 bypass 见全树、无 role-menu 行）：

| 菜单 | route_name | route_path | component | icon | i18n_key | menu_type | sort_order | parent_id |
|---|---|---|---|---|---|---|---|---|
| 支付管理 | `payment` | `/payment` | `layout.base` | `carbon:finance` | `route.payment` | directory(1) | 3 | NULL |
| 统计概览 | `payment_stats` | `/payment/stats` | `view.payment_stats` | `carbon:dashboard` | `route.payment_stats` | menu(2) | 1 | 80 |
| 支付订单 | `payment_order` | `/payment/order` | `view.payment_order` | `carbon:currency` | `route.payment_order` | menu(2) | 2 | 80 |
| 退款订单 | `payment_refund` | `/payment/refund` | `view.payment_refund` | `carbon:currency-refund` | `route.payment_refund` | menu(2) | 3 | 80 |
| 通道交互日志 | `payment_channel_log` | `/payment/channel-log` | `view.payment_channel_log` | `carbon:exchange` | `route.payment_channel_log` | menu(2) | 4 | 80 |
| 订单操作记录 | `payment_operation` | `/payment/operation` | `view.payment_operation` | `carbon:activity` | `route.payment_operation` | menu(2) | 5 | 80 |

ID：directory=80；leaves=100/110/120/130/140。支付/退款详情**不种菜单**（前端弹窗/抽屉，见 `CONTEXT.md`「详情页 = 弹窗/抽屉优先」）；生命周期 = 详情抽屉内 tab、无菜单。菜单模型完全照 Soybean（[ADR-0004](../../docs/adr/0004-menu-model-follows-soybean.md)）。

**出站客户端**
- 新增 `PaymentClient`（infrastructure 裸 `@Component`，镜像 `AppRegistryClient`）：构造注入框架 `OpenApiClient` + payment base-url（`application.yml` 配置），复用框架自动 HMAC-SHA256 签名。**不走** `@Port/@Adapter`（[ADR-0007](../../docs/adr/0007-admin-bff-outbound-clients-are-flat-components.md)）。
- **凭据复用**：admin 以自身 `admin-console` 身份签名；payment 向 app-registry 查 secret 验签、对所有登记调用方一视同仁——**无需新凭据、无需新签名客户端**（issue #37 接入清单第 1、2 项复用框架既有能力）。
- **错误翻译**：`OpenApiClientException` → `DomainException`（携带 `ADMIN_*` `CodeMessage`），按下游 HTTP 状态映射（404→404、409→409 等），逐方法 catch-and-rethrow（与 `AppManagementAppService` 一致）。

**端点面**（北向 `/api/admin/payment/**`，镜像 payment 路径）
- 读·列表（分页+筛选）：`/payments`、`/refunds`、`/payment-logs`（通道交互日志）、`/operation-logs`（订单操作记录）。
- 读·详情/视图：`/payments/{paymentOrderNo}`、`/refunds/{refundOrderNo}`、`/orders/{orderNo}/lifecycle`。
- 写·运营：`POST /refunds/{refundOrderNo}/audit`（approve/reject）、`POST /payments/{paymentOrderNo}/notifications/resend`、`POST /refunds/{refundOrderNo}/notifications/resend`。
- 统计（透传）：tier-1 = `/stats/payments/overview`、`/stats/orders/status-distribution`、`/stats/gateway/health`、`/stats/operations/audit`；tier-2 = `/stats/by-business-system`、`/stats/by-channel`、`/stats/anomalies`、`/stats/operations/activity`。
- 可选：`POST /payments/{paymentOrderNo}/query`（主动查行，是否暴露自决）。
- 分页/筛选契约对齐 admin 现有列表（与 `/apps` 同形），筛选字段映射 payment 的查询参数。

**权限码**（controller 方法级 `@RequirePermission`，沿用 `admin:<domain>:<action>` 约定）
- `admin:payment:read` —— 所有列表/详情/生命周期/日志/统计 GET。
- `admin:payment:refund:audit` —— 退款审核。
- `admin:payment:notification:resend` —— 支付/退款通知重发。
- `admin:payment:bank:query` —— 主动查行（若暴露）。
- **不做** menu `buttons` 字段（REQ-9 搁置，[issue #18](https://github.com/ZhangColin/aieducenter-admin/issues/18)）；前端按 `/auth/current` 的 permission claims 自控按钮显隐。

**操作者身份透传**
- 写请求体放 `auditorId = RequestContext.getUserId()` / `auditorName = RequestContext.getUserName()`（cartisan-core，`SecurityFilter` 填充）。**零 Sa-Token、零 DB、零新注解**（`@CurrentUser` 不存在，已提 [cartisan-boot #18](https://github.com/ZhangColin/cartisan-boot/issues/18)）。

**审计**
- 业务操作权威审计 = payment `OperationLog`（payment ADR-0002）。admin **不本地记账**，仅透传身份 + 读回展示。admin 无 DB 迁移（不持任何 payment 数据），**除 V13 菜单种子外无 schema 变更**。

**仪表盘**
- 当下 **纯透传** payment stats；架构**留跨服务聚合余地**（日后 widget 可能组合 payment/钱包/Token），不把端点↔widget 焊死 1:1。

**删除模型**：admin 三聚合物理删除（[ADR-0005](../../docs/adr/0005-soft-delete-to-physical-delete.md)）——本 spec 不新增聚合，无关。

## Testing Decisions

**好测试的原则**：只测外部行为（HTTP 响应 / DTO 形状 / 错误码 / 身份透传契约），不测内部调用序列——**例外**：操作者身份透传（验证 `RequestContext`→请求体 `auditorId/auditorName`）是契约的一部分，需断言。AssertJ 断言，测试命名 `given_{条件}_when_{操作}_then_{预期}`。

**主 seam（新，最高）—— payment BFF 集成测试**：`@SpringBootTest` + `@MockBean PaymentClient`，镜像现有 `AppBffIntegrationTest`。真实 Spring 上下文跑 controller → appservice → client（client mock），一次断言：DTO 映射（payment wire → admin response）、筛选字段映射、分页契约、操作者身份透传、错误翻译（payment 状态码 → `ADMIN_*`）。覆盖除菜单种子外的全部端点行为。
- **不**写 `PaymentClient` 直测（与 `AppRegistryClient` 一致：不经 MockWebServer、client bean 直接 mock）。
- **不**写 controller standalone 单测（集成测试已更高位覆盖；可后补，非必需）。

**复用现有 seam（无新增）**：
- **菜单种子 V13**：`RoleAssignmentFlywaySchemaIntegrationTest` 模式——独立 PG schema + Flyway + `ddl-auto=none`，验迁移落库。先例：`RoleAssignmentFlywaySchemaIntegrationTest`。
- **权限强制**：`RbacEnforcementIntegrationTest` 模式——真实 Sa-Token 过滤链，断言 payment 端点 `@RequirePermission` 对无权者 403。先例：`RbacEnforcementIntegrationTest`。

**可选守护（新，低成本）**：`ArchitectureTest` 加**一条** ArchUnit 规则——「infrastructure 出站服务客户端为 `@Component`、非 `@Port/@Adapter`」，钉 [ADR-0007](../../docs/adr/0007-admin-bff-outbound-clients-are-flat-components.md)、防回退。加进现有测试类、非新类。

## Out of Scope

- **admin-web 前端页面**（菜单路由元数据已定、详情走弹窗/抽屉、按钮按 claims 显隐、dashboard 单页多区块）—— spec 契约锁定后**另发 admin-web issue**。
- **payment 服务侧实现**（payment 仓 #9–#18）—— 本 spec 是消费视角，最终字段以 payment 实现契约为准。
- **创建 / 取消 / 预下单支付**（`POST /payments`、`/prepay`、`/cancel`）—— 业务系统职责。
- **强制施加订单状态**（手动取消/强制关单/标记成功）—— payment ADR-0001 明确不存在此类 admin 端点。
- **REQ-9 按钮（button）级权限元数据** —— 搁置，归 issue #18 统一处理。
- **财务上下文**（只读收入确认 + append-only 冲销）—— 不同关注点，另立。
- **admin 本地审计表** —— 审计归 payment `OperationLog`，admin 不记。
- **tier-2 统计在 payment 侧的实现** —— payment 仓 #18；admin 待其就绪后透传。

## Further Notes

- **契约源**：[issue #37](https://github.com/ZhangColin/aieducenter-admin/issues/37)（payment 提供的消费视角契约范围）。各端点最终字段以 payment 实现时的契约为准。
- **联调前提**：payment 仓 #9–#18 落地后方可联调；**设计与 BFF 搭建可先于其进行**（client/appservice 可先写、用 mock 验，待 payment 就绪再接真）。
- **术语**：见 [`CONTEXT.md`](../../CONTEXT.md)「支付管理上下文 / admin 是 BFF / BFF 不记审计 / BFF 仪表盘跨服务聚合 / 详情页弹窗优先 / 退款审核 / 通知重发 / 支付管理菜单」。
- **相关 ADR**：[0004 菜单照 Soybean](../../docs/adr/0004-menu-model-follows-soybean.md)、[0005 物理删除](../../docs/adr/0005-soft-delete-to-physical-delete.md)、[0007 BFF 出站客户端裸 @Component](../../docs/adr/0007-admin-bff-outbound-clients-are-flat-components.md)。
- **框架依赖**：[cartisan-boot #18](https://github.com/ZhangColin/cartisan-boot/issues/18)（`@CurrentUser` 文档漂移；非阻塞，admin 用 `RequestContext`）。
- **Ticket 拆分**（依赖序，挂本 spec 为 parent）：T1 菜单种子 V13 / T2 PaymentClient+配置 / T3 读·列表 / T4 读·详情+生命周期 / T5 写·审核+重发 / T6 统计 tier-1 / T7 统计 tier-2 / T8 bank-query（可选）。T1、T2 无依赖可先行；T3–T8 依赖 T2；admin-web issue 依赖本 spec 契约锁定。
