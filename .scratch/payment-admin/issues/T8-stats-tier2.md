## Parent
#38

## What to build
运营人员在「统计概览」看 tier-2 细分：按业务系统 / 按通道 / 异常 / 操作员活动。4 个 tier-2 stats 端点透传，复用 T7 范式。加 `GET /stats/by-business-system`、`/stats/by-channel`、`/stats/anomalies`、`/stats/operations/activity`。

## Acceptance criteria
- [ ] 4 个 tier-2 stats 端点透传 payment 返回
- [ ] 挂 `admin:payment:read`
- [ ] BFF 集成测试覆盖透传契约/错误翻译

## Blocked by
__BLOCKER__ + 外部前提：payment 仓 #18（tier-2 实现）落地后方可联调（BFF 透传代码可先写、mock 验）
