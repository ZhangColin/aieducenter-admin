# 02 — 登录写 userName（admin 侧补丁，立即生效）（Bug ④a）

**What to build:** admin 登录成功后，会话写入 `userName`（取昵称），使后续每个请求的 `RequestContext.userName` 被框架 `SecurityFilter` 正确填充、非 null。这是 cartisan-security `login()` 根因（会话建立与 userName 写入割裂）落地前的 **admin 侧补丁**——用 `StpUtil.getSession().set("userName", 昵称)`，今天就让 userName 可用，不被框架节奏卡。详见 `.scratch/phase0-completion/spec.md`。

**Blocked by:** None — 可立即开工（与 01 独立）。

**Status:** ready-for-agent

- [ ] 登录成功后，会话 `userName` == 登录用户昵称
- [ ] 后续请求 `RequestContext.userName` 非 null（`SecurityFilter` 从 session 读到）
- [ ] 集成测试覆盖（login-session seam：login 后断言会话 `userName`；不新建测试专用 controller）
- [ ] 现有登录/认证行为不回归（token 正常签发、登出正常、`/current-admin` 契约不变）
