# BFF 金额出口用 Long（分）+ 北向形状逐字镜像 provider 契约（零加戏）

**决策**（[#55](https://github.com/ZhangColin/aieducenter-admin/issues/55) grill 定稿，2026-08-17）：

1. **金额一律 `Long`（分）**：BFF 对某能力域的 wire 镜像 DTO、北向 Response、北向 Query 中的金额字段全部 `Long`（分），与 provider 同型透传、零换算。**比率/均值字段**（`successRate`/`refundRate`/`approvalRate`/`avgAuditDurationMinutes`/`avgExecutionTimeMs` 等）保持 `BigDecimal`——provider 亦如此，金额与比率不同型是 provider 契约本身的决定。
2. **接受框架 Long→JSON string 序列化**：cartisan-web 全局对 `Long`/`long` 注册 `ToStringSerializer`，故北向金额在 JSON 里是 string（`"amount": "9900"`），与 `auditorId` 等既有 Long 字段同形态。前端按 string 处理（防精度丢失），算术入口统一转 `Number`。**不**为了"金额看起来像数字"而在 BFF 把 Long 包成 BigDecimal 出 number——那是为序列化形态服务的类型谎言。
3. **北向形状逐字镜像 provider**：字段名、类型、嵌套、信封、token 值一律照 provider 源码 Response（`application/dto/response|query/*.java`），**不增删字段、不重命名、不换形状、不顺手改善**。provider 没有的字段（"ghost"）删；provider 实有而 BFF 未暴露的字段也**不主动补**。
4. **单向变更流**：第一轮接入后 provider 契约若变更，不做前置跨仓门禁（OpenAPI 生成 wire DTO / 共享契约测试 **defer**）——等发现问题开新票重新对接。展现层需求在联调联测通过后，从 UI 起单向提（UI→BFF→provider），届时再决定改 UI、BFF 还是 provider。

**为什么**：#37 接入时 wire DTO 按 spec 文档理想化撰写（amount 作 BigDecimal、lifecycle 作平表 union、Backlog 作扁平字段、自造 ghost 字段），"在服务接口上加戏"导致前端↔BFF↔payment 三层对不上，#50/#55 两轮返工。核实 payment 真实契约：金额全 `long`（分）、BigDecimal 只用于比率——**干净且自洽，admin 单方面对齐即可，无需 payment 改动**。类型/形状撒谎虽常能"值上跑通"（Jackson 宽松反序列化掩盖），但信封级偏差（如 lifecycle 实返数组）会直接运行时炸。

**取舍**：放弃北向金额的 JSON number 形态（变 string）——前端 typings 与算术处理需联动改；放弃 BigDecimal 的"元语义直觉"——统一分的整数语义，换三层同型。防 provider 漂移靠纪律（对接时对照 provider 源码）而非机械门禁——接受"变更只能事后发现、开新票重接"的代价。

**后果**：
- 本次对齐范围（#55）：lifecycle 语义 9 字段形状 + 信封（provider 实返 `ApiResponse<List<事件>>` 扁平列表，无 orderNo 包装）、status-distribution 嵌套 `refundBacklog{pendingCount, pendingAmount}`、全部金额字段 Long、refund 出口删 `auditorId`/`auditedAt` ghost（**query 侧 `auditorId` 保留**——provider 支持该筛选）。
- 契约测试 fixture 的 Long 字段用 **string 形态**（`"9900"`）——那才是 provider 真实发出来的形状（框架 ToStringSerializer）。
- 旧立场作废：北向 Query javadoc「金额暂以元（BigDecimal）承」——事实契约一直是分（前端 `yuanToCents` 先行），文档错。
- admin-web 联动改 typings/组件（独立 issue 于 web 仓）。
- 平台级横切，兄弟 BFF 子上下文（account、未来钱包/Token计量）继承。

**关联**：[#55](https://github.com/ZhangColin/aieducenter-admin/issues/55)、[#50](https://github.com/ZhangColin/aieducenter-admin/issues/50)（枚举 Integer + *Name，ADR-0009）、[payment #21](https://github.com/ZhangColin/aieducenter-payment/issues/21)（payMode/accessType/paymentChannel 已补齐，ghost 解除）、[ADR-0009](0009-bff-enum-response-carries-display-name.md)、[ADR-0010](0010-platform-pagination-protocol.md)；架构仓 CONTEXT § 枚举出口约定。
