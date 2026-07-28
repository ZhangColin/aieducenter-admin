# 03 — 迁移到框架新 `login` 签名、去掉 StpUtil workaround（Bug ④b）

**What to build:** cartisan-boot `.scratch/login-user-name` issue 01 落地后（框架采纳**破坏性补全签名**：两个 `login` 重载各加 `String userName`、不留旧重载），admin 的 login 调用迁到新签名（传昵称），删掉 02 里加的 `StpUtil.getSession().set(...)` workaround——admin 不再为写 userName 直接依赖 `StpUtil`（抽象不泄漏）。更新 ADR。框架落地时 admin 必须做此迁移（旧签名消失、否则编译不过），故提前挂账。详见 `.scratch/phase0-completion/spec.md`。

**Blocked by:** cartisan-boot `.scratch/login-user-name` issue 01 resolved + 新 cartisan-security jar 进 `~/.m2`（admin 侧先 `mvn install` 刷新本地制品）。**已就绪（2026-07-28）：框架 commit `efcb1e7` 已落地、jar 已 `mvn install`。**

**Status:** resolved

- [x] 前置：cartisan-boot issue 01 已 resolved、新 cartisan-security jar 已 `mvn install` 进 `~/.m2`
- [x] admin login 调用迁到框架新签名（传昵称）
- [x] 02 里加的 `StpUtil.getSession().set("userName", ...)` workaround 已删
- [x] 登录后 `RequestContext.userName` 仍非空（行为不回归）— `LoginWritesUserNameIntegrationTest` 绿
- [x] admin 不再直接依赖 `StpUtil` 来写 userName
- [x] ADR 更新：从"admin 侧 workaround（等框架）"→"框架已落地，迁移完成"
- [x] 全量套件绿（登录/认证行为不回归）
