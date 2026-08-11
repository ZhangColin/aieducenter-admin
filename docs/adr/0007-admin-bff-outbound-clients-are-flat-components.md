# admin 出站客户端为裸 @Component（BFF 不走 @Port/@Adapter）

cartisan-boot 的 `@Port(CLIENT)` / `@Adapter(CLIENT)` 是**业务服务**南向端口的 DDD 规范（Domain 定义 Port、Infra 实现 Adapter，意在把领域逻辑与外部依赖解耦），CLAUDE.md 与限界上下文编码规范列为推荐。但 **admin 是统一后台 BFF**，职责 = 调下游能力域 + DTO 转换 + 跨服务聚合（见 [`../../CONTEXT.md`](../../CONTEXT.md)「admin 是 BFF」），**没有值得解耦的领域厚度**——为其立 Port/Adapter 是仪式而无收益（Port 接口与 Adapter 一一对应、别无实现）。

故 admin 的出站服务客户端（`AppRegistryClient`、`PaymentClient`、未来的钱包/Token 客户端）为 **infrastructure 包内裸 `@Component`**，应用层（`*AppService`）直接构造注入，**不走** `@Port/@Adapter`。`AppRegistryClient` 在本 ADR 之前已如此、运行良好；[issue #37](https://github.com/ZhangColin/aieducenter-admin/issues/37) grill（2026-08-11）确认 `PaymentClient` 沿用，并据「BFF 不持业务逻辑」推广到未来所有出站服务客户端。

**注意**：真正的南向端口（如 `PasswordEncoder`——Domain 定义、有切换实现的可能）仍走 `@Port/@Adapter`；本 ADR 仅针对**出站 HTTP 服务客户端**这一类。

**后果**：CLAUDE.md / 编码规范推荐 @Port/@Adapter 的条款**对 admin 出站服务客户端不适用**，未来工程师勿把 `AppRegistryClient`/`PaymentClient` 当违规去"修"。若日后要统一为 @Port/@Adapter，须另立 ADR 取代本条、并**一次性迁移全部出站客户端**（避免半 Port、半裸 Component 的混合更难维护）。
