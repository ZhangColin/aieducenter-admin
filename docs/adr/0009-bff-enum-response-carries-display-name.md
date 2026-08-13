# BFF 对外响应的枚举字段须配 *Name 中文名（前端直读，取代前端 i18n 枚举映射）

**决策**：平台统一规则——服务出口（BFF 对外响应）凡有枚举字段（如 `status`），**必须同时给出对应中文名**（如 `statusName`）；前端直接拿 `*Name` 显示，**不再在端侧做枚举→中文 i18n 映射**。展示文案归后端出口统一负责。消费方（前端）只消费 `*Name`，零枚举映射表。

**为什么**：文案归属若分散在前端（每端各自维护枚举 code→中文映射），会与后端枚举定义双轨漂移、且新增枚举值要前后端同步改。把展示名收敛到出口（后端序列化时一并给出 `*Name`），枚举真源唯一、前端零映射。payment v1 响应已按此提供 `*Name`（`statusName` / `payModeName` / `accessTypeName` / `paymentChannelName` / `auditTypeName` / `operationName` / `targetTypeName`、状态分布各分桶 `statusName` 等）；admin BFF 此前 wire 镜像 DTO 漏收这些字段 → Jackson 丢弃 → 前端只剩原始 code（[#50](https://github.com/ZhangColin/aieducenter-admin/issues/50)）。

**取舍**：放弃前端 i18n 的多语言灵活性——当前平台仅中文，无需多语言枚举文案；若将来要多语言，应在出口层按 `Accept-Language` 解析 `*Name`，而非退回前端映射。代价是出口 DTO 字段数增加（每个枚举多一个 `*Name`），可接受。

**后果**：
- 新建/改 BFF 对外 Response DTO 时，枚举字段一律配 `*Name` 中文名；wire 镜像 DTO 也收 `*Name`，并在 `toXxx` 映射透传（**不**在 admin 侧做 code→中文翻译——admin 作为 BFF 不拥有各能力域的枚举语义，翻译归能力域出口）。
- 凡能力域出口**尚未提供** `*Name` 的枚举字段，是能力域侧契约缺口——向能力域提 issue，**不在 BFF 臆造映射**（#50 核对发现 payment 的 `payMode`/`accessType` 未暴露，已提 [payment #21](https://github.com/ZhangColin/aieducenter-payment/issues/21)；admin 侧 wire 与 payment 真实契约的整体对齐见 [#55](https://github.com/ZhangColin/aieducenter-admin/issues/55)）。
- 本规则是平台级横切，兄弟 BFF 子上下文继承（镜像 payment 子上下文的横切决策）。
- 取代旧立场：此前若干 BFF DTO javadoc 写「展示文案（i18n）由前端按枚举名映射」——**该立场作废**，#50 已改写。

**关联**：[#50](https://github.com/ZhangColin/aieducenter-admin/issues/50)、[payment #21](https://github.com/ZhangColin/aieducenter-payment/issues/21)、[#55](https://github.com/ZhangColin/aieducenter-admin/issues/55)；架构仓 CONTEXT § 枚举出口约定。
