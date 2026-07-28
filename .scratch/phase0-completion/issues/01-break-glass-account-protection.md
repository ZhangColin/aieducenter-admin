# 01 — 破窗账号保护（不可删/不可禁）+ 删 `system` 列（Bug ③）

**What to build:** 内置破窗号 `admin`(id=1) 不可删、不可禁、可改密；普通管理员可正常建/删/禁。删除 `system` 列（V4 迁移），实体与 Flyway schema 重新对齐——"生产 `ddl-auto=none` 下建管理员因 `system NOT NULL` 漏列而 500"的潜伏 bug 随之消失。`AdminUserResponse` 暴露"是否破窗号"派生字段。`assignRoles` 不允许从破窗号移除 `SUPER_ADMIN`（守住"全权救援"韧性）。授权（谁是超管）继续走已有的 `SUPER_ADMIN` 角色 + 框架 `AuthorizationBypassResolver`，不引入 per-user 标记位。详见 `.scratch/phase0-completion/spec.md`。

**Blocked by:** None — 可立即开工。

**Status:** ready-for-agent

- [ ] 删除破窗号 `admin`(id=1) 被拒（标准错误体，命中如 `SYSTEM_ADMIN_CANNOT_DELETE` 错误码），不软删
- [ ] 禁用破窗号被拒（`updateStatus → DISABLED` 抛领域错误）
- [ ] 破窗号可改密（`resetPassword` 正常，不被守卫拦）
- [ ] 建普通管理员成功（集成测试 `@SpringBootTest` seam 验证）
- [ ] 删/禁普通管理员成功
- [ ] `system` 列已删（V4 迁移 `DROP COLUMN system`），实体不映射、schema 无此列——实体与 Flyway 重新对齐（消除生产建管理员 500 的根因）
- [ ] `count()<=1` last-admin 检查已删（破窗号永在，规则成死逻辑）
- [ ] `AdminUserResponse` 含"是否破窗号"派生字段（按保留 ID `== 1` 判定，不入库）
- [ ] `assignRoles` 从破窗号移除 `SUPER_ADMIN` 被拒
- [ ] 创建管理员 API 不暴露"破窗号/system"开关（新建账号一律普通账号）
- [ ] 集成测试覆盖：删/禁破窗号被拒、建/删普通管理员成功
- [ ] 聚合单测覆盖：破窗号 `markAsDeleted`/`disable` 抛领域错误、普通账号正常
