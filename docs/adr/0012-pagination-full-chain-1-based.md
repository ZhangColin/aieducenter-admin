# 分页全链 1-based：±1 换算唯一收 cartisan-boot（supersede ADR-0010）

**日期**：2026-09-16　|　**状态**：已接受　|　**议题**：[#73](https://github.com/ZhangColin/aieducenter-admin/issues/73)

**决策**：平台分页协议改为**全链 1-based**——请求 `page` 与响应 `page` 同语义（第 1 页是 1）。所有 ±1 换算
**唯一收 cartisan-boot** 两个出口，业务仓（含本仓）主代码零手工换算：

- **北向绑定**：列表端点 controller 统一用框架 `Pagination`（`com.cartisan.web.request`）绑定——
  `page` 缺省 1、`page<1` 静默贴边 1；`size` 缺省 20、上限 100、越界贴边；`sort` 走框架
  （`toPageRequest()` / `toPageRequest(Sort defaultSort)` / `toPageRequest(Set<String> allowedFields)`）。
- **回显**：自有域查询结果经 `PageResponse.of(Page)` 集中做 1-based（`Page.getNumber()+1` 收在框架内）；
  BFF 域透传下游回显（迁移后各服务回显==请求页码）。
- **出站**：`*Client` 上 wire 的 `page` 与北向**同值直传**，无 ±1。

**为什么**：ADR-0010 的「请求 0-based / 响应 1-based」虽三域一致，但请求/响应语义不同构，前端每接一个域
都要重记一条规则、且北向换算散落各仓（AppService `+1` 与 `*Client` `-1` 在 wire 上抵消——两步抵消零价值、
双倍出错面）。平台四域服务端已全部迁完并合入各仓 develop（app-registry b56125c/#24、payment 693fb3d/#22、
identity e5e0a56/#78、aiplatform ac93504/#187-194——2026-09-16 对照服务端源码逐环核实），框架
`Pagination.toPageRequest()`（`isub`）与 `PageResponse.of()`（`iadd`）已就位于 cartisan-web 0.1.0-SNAPSHOT
（javap 核实），扫清了换算收口的最后障碍。

**本仓两种出口模式**（按域归位，新端点照抄）：

| 域 | 北向绑定 | 出站 | 回显 |
|---|---|---|---|
| 自有域（users/roles/menus） | `Pagination` 参数 + `pagination.toPageRequest()`（roles 用 `toPageRequest(DEFAULT_SORT)` 承接原 `@PageableDefault` 兜底排序） | 本仓 JPA | `PageResponse.of(page.map(...))` |
| BFF 域（payment/account/app） | `Pagination` 参数 | `pagination.page()/size()` 同值传 `*Client`，wire 直传 | 透传下游回显 `new PageResponse<>(items, page.total(), page.page(), page.size())` |
| aiplatform 域 | **既有形态保留**：`@RequestParam(defaultValue = "1")`（issue #62 交付形态） | wire 本就 1-based 直传 | 透传 provider 回显 |

**aiplatform 域的既有差异**（不重构）：其列表端点用 `@RequestParam(defaultValue = "1")` 接页码而非
`Pagination` 对象——wire 等价（1-based 直传 + 回显透传），仅绑定形式不同；已交付端点不做无谓 churn。
差异点：不走框架的 size 贴边/sort 能力（aiplatform 各端点自定 size 默认值）。若未来需统一，另开 issue
单独迁移。

**行为变化**：

- 北向请求 `page` 从 0-based 改 1-based——**破坏性变更**，与 admin-web 同窗切换（admin-web #55）。
- `page<1`（含 `page=0`）从 Spring `Pageable` 的 0-based 首页语义改为框架贴边 1（同首页，行为等价）；
  `size` 从 Spring 默认 20/无上限改为 20 缺省/100 封顶。
- app-registry 附带：不传 sort 时默认 **createdAt 降序**（最新登记在前，app-registry #24）——排序语义归
  下游，BFF 不传 sort、不复刻。
- 角色列表默认排序（sortOrder 升序 + id 升序兜底，issue #19）由 `@PageableDefault(sort=...)` 迁到
  `toPageRequest(DEFAULT_SORT)`，行为不变（客户端 `?sort=` 仍可覆盖）。

**mock 须钉真实回显契约**（继承 ADR-0010 教训）：给下游列表端点造 mock/fixture 时，`page` 按
「==请求页码」构造；验证下游语义以兄弟仓服务端源码为准，本仓 mock 只证明「admin 假设过什么」。

**测试钉法**（issue #73 验收）：

- 北向契约（`PaginationNorthboundContractIntegrationTest`，HTTP 层）：`page=1` 首页+回显 1、
  `page=0` 贴边 1、size 缺省 20、超 100 贴边 100。
- wire 契约（stub transport 打到真实 deserialization seam）：PaymentClient 四列表端点 +
  AccountClient + AppRegistryClient 各至少一例，断言出站 `page` 直传（北向 `page=1` → wire 即 `page=1`）；
  各域响应信封形状一并钉死（payment/identity 为 `ApiResponse<PageResponse>` 信封、app-registry 裸
  `PageResponse`）。

**关联**：supersede [ADR-0010](0010-platform-pagination-protocol.md)；[#73](https://github.com/ZhangColin/aieducenter-admin/issues/73)
（本仓收口）；admin-web #55（前端同窗切换）；identity #78 / payment #22 / app-registry #24 /
aiplatform #187-194（服务侧迁移）。
