## Parent
#49（平台账号管理 BFF 实现 spec）

## What to build
运营人员在「账号管理 → 账号列表」分页搜索平台账号——贯穿菜单 / 出站客户端 / 应用层 / 控制器 / 权限 / 测试的 **tracer bullet**，一次打通 account BFF 整条通路。两件事：

1. **种 V14 菜单**（账号管理 directory sort=4 + 账号列表 leaf，共 2 行）。`ON CONFLICT (id) DO NOTHING`，不写种子快照测试，SUPER_ADMIN 经 bypass 见全树、无 role-menu 行。元数据（route_name/path/component/icon/i18n/sort/id）见 spec「菜单种子 V14」与 CONTEXT.md。
2. **AccountClient 地基 + 账号列表**：`AccountClient`（infrastructure 裸 `@Component`，复用框架 `OpenApiClient` 自动签名、注入 identity baseUrl、`OpenApiClientException→DomainException` 错误翻译，镜像 `PaymentClient`）；`application.yml` 新增 `admin.identity.base-url`；`GET /api/admin/accounts`（分页+筛选：email/phone/userId/status/locked/注册时间区间）；`admin:account:read`；account BFF 集成测试范式（`@SpringBootTest`+`@MockBean AccountClient`）。

## Acceptance criteria
- [ ] V14 迁移落库，2 行菜单出现在 `GET /menus/tree`，SUPER_ADMIN 无 role-menu 行即可见
- [ ] `AccountClient` 以 admin-console 身份签名调用，错误按 HTTP 状态翻译为 ADMIN_* DomainException
- [ ] `application.yml` 有 `admin.identity.base-url`（环境变量覆盖占位，仿 payment）
- [ ] `GET /api/admin/accounts` 返回分页+筛选结果，分页形状对齐 `/apps`
- [ ] 挂 `admin:account:read`，无权限者 403（RBAC 集成测试覆盖）
- [ ] BFF 集成测试（mock AccountClient）覆盖 DTO 映射 / 筛选映射 / 分页契约 / 错误翻译
- [ ] ArchUnit 既有规则通过（AccountClient 在 infrastructure、为 @Component，ADR-0007）

## Blocked by
无 —— 可立即开工（client/appservice 可先写、mock 验，待 identity #70 搜索契约冻结再接真）
