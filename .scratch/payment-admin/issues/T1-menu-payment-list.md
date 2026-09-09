## Parent
#38（支付后台管理 BFF 实现 spec）

## What to build
运营人员在「支付管理 → 支付订单」分页查看并筛选支付订单——贯穿菜单/出站客户端/应用层/控制器/权限/测试的 **tracer bullet**，一次打通 payment BFF 整条通路。两件事：

1. **种 V13 菜单**（支付管理目录 sort=3 + 统计概览/支付订单/退款订单/通道交互日志/订单操作记录 5 叶子，共 6 行；统计概览 sort=1 前置；详情页不种）。`ON CONFLICT (id) DO NOTHING`，不写种子快照测试，SUPER_ADMIN 经 bypass 见全树、无 role-menu 行。元数据（route_name/path/component/icon/i18n/sort）见 #38 与 CONTEXT.md「支付管理菜单」。
2. **PaymentClient 地基 + 支付订单列表**：`PaymentClient`（infrastructure 裸 `@Component`，复用框架 `OpenApiClient` 自动签名、注入 payment baseUrl、`OpenApiClientException→DomainException` 错误翻译，镜像 `AppRegistryClient`）；`GET /api/admin/payment/payments`（分页+筛选：paymentOrderNo/businessOrderNo/businessSystemName/status 多选/payMode/accessType/paymentChannel/金额区间/createdAt/paidAt 区间）；`admin:payment:read`；payment BFF 集成测试范式（`@SpringBootTest`+`@MockBean PaymentClient`）。

## Acceptance criteria
- [ ] V13 迁移落库，6 行菜单出现在 `GET /menus/tree`，SUPER_ADMIN 无 role-menu 行即可见
- [ ] `PaymentClient` 以 admin-console 身份签名调用，错误按 HTTP 状态翻译为 ADMIN_* DomainException
- [ ] `GET /api/admin/payment/payments` 返回分页+筛选结果，分页形状对齐 `/apps`
- [ ] 挂 `admin:payment:read`，无权限者 403（RBAC 集成测试覆盖）
- [ ] BFF 集成测试（mock PaymentClient）覆盖 DTO 映射/筛选映射/分页契约/错误翻译
- [ ] ArchUnit 既有规则通过（PaymentClient 在 infrastructure、为 @Component）

## Blocked by
无 —— 可立即开工
