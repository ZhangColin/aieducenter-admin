# 平台分页协议：请求 0-based / 响应 1-based（BFF 出站 +1/-1 抵消、回显透传）

**决策**：平台统一分页协议——凡 `PageResponse{items,total,page,size}` 外壳：**请求 `page` 0-based**（Spring `Pageable` 语义），**响应 `page` 1-based**（回显「页码+1」）。北向（前端 ↔ admin BFF）与能力域服务（identity / payment 等）同此约定。admin BFF 出站换算链：AppService 把 `Pageable` 页码 **+1** 作 1-based 中间表示传给 `*Client`，`*Client` 上 wire 前 **-1** 还原 0-based（两步在 wire 上抵消），能力域回显 1-based 后 BFF **原样透传**（不得再 +1）。

**为什么**：三个域共用同一 `PageResponse` 外壳却若各持一种 `page` 语义，前端每接一个域都要重猜一次（admin-web #50 对接 account 时即因此踩坑、写出错向的 `accountTransform` +1 适配）。反向统一（全平台改「响应 0-based」）要动 system-manage + payment 全部端点与测试，代价不可接受——既有主流即「请求 0-based / 响应 1-based」，正向归一成本最低。

**现状核实**（[#56](https://github.com/ZhangColin/aieducenter-admin/issues/56) triage 逐环验证，2026-08-17）：三域**本已全部符合**协议，无需任何生产行为变更——

| 域 | 请求 `page` | 响应 `page` |
|---|---|---|
| system-manage（users/roles/menus） | 0-based | 1-based |
| payment（4 列表端点） | 0-based | 1-based（payment 各 QueryAppService 回显 `getPageNumber()+1`） |
| account（`GET /api/admin/accounts`） | 0-based | 1-based（identity #70 冻结契约：请求 `Pageable` 0-based、回显 `页码+1`；admin 透传） |

#56 的「account 响应 0-based」前提**不成立**：该错觉源于本仓三处测试把 identity 回显误建模为 wire 裸页码（0-based）——`AccountBffIntegrationTest` 两处 mock（断言 `page()==0`）与 `AccountClientListEnvelopeContractTest` 的 JSON fixture；前端 REQ-18「本地源码核实」读到的正是这些 mock。#56 已按此纠正改造成「修测试钉契约 + 本 ADR」，生产代码零变更。

**取舍**：放弃「响应也 0-based」的全程一致性（请求/响应语义不同构，心智上多一条规则）；换来的是不动任何既有端点与前端惯例。1-based 响应对人读 JSON 更直观（第 1 页是 1），且是框架 `Pageable` + 手工回显的自然产物。

**后果**：
- admin 新建 BFF 列表端点：镜像既有模式——AppService `pageable.getPageNumber() + 1` → `*Client` `page - 1` 上 wire → 响应构造**透传** `page.page()`（禁止再 +1，否则 2-based 回归）。
- **mock 须钉真实回显契约**：给下游列表端点造 mock/fixture 时，`page` 按「wire 请求页码 + 1」构造，不得以 wire 裸页码充回显——本 ADR 的直接教训（验证下游语义以兄弟仓服务端源码为准，本仓 mock 只证明「admin 假设过什么」）。
- 能力域新列表端点：请求 0-based（`@PageableDefault`）+ 回显 `getPageNumber()+1`，与 identity #70 / payment 各端点同款；不符合即向该域能力域提 issue，不在 BFF 臆造换算。
- 前端：请求侧 `-1`（1-based UI → 0-based 请求）是本协议的固有适配，保留；响应侧 `pageNum` 直读 1-based（admin-web `ad09607` 后账号页与 payment/system-manage 同惯例）。

**关联**：[#56](https://github.com/ZhangColin/aieducenter-admin/issues/56)（拍板 + 前提纠正 + 测试修正）、admin-web REQ-18（需求源，[admin-web #50](https://github.com/ZhangColin/aieducenter-admin-web/issues/50) 对接发现）、[identity #70](https://github.com/ZhangColin/aieducenter-identity/issues/70)（identity 列表契约冻结）。
