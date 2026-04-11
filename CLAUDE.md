# aieducenter-admin

管理后台网关后端。基于 cartisan-boot 框架开发。

## 📚 核心文档（必读）

### 1. [cartisan-boot 使用手册](docs/guide/cartisan-boot-使用手册.md)
**框架使用指南** - 涵盖所有 cartisan-boot 模块的能力清单、API 文档和使用示例

**核心模块**：
- `cartisan-core` - DDD 基础类型、异常体系、架构注解、断言工具
- `cartisan-test` - ArchUnit 规则（15条）、测试基类、Fixture 工具
- `cartisan-web` - 统一响应体、全局异常处理、请求上下文、防重提交、枚举选项
- `cartisan-data-jpa` - BaseRepository、事件发布、审计、软删除、@Condition 注解、枚举增强
- `cartisan-security` - 权限注解、@CurrentUser、SecurityContext、TenantContext
- `cartisan-ai` - 统一对话模型、Provider SPI、SSE 流式工具

### 2. [限界上下文代码编写规范](docs/guide/限界上下文代码编写规范.md)
**项目编码规范** - DDD 六边形架构落地指南、代码组织、测试规范、编码风格

**关键规范**：
- **架构理念**：六边形架构、小聚合原则、依赖倒置
- **层次实现**：领域层、应用层、基础设施层、北向接口层
- **横向规范**：数据库规范、测试规范（单元/集成/并发/性能）、编码风格
- **架构守护**：ArchUnit 规则、新建上下文检查清单

### 3. [团队踩坑经验库 (PITFALLS.md)](docs/PITFALLS.md)
**避坑指南** - 团队实战中遇到的问题及解决方案

## 限界上下文

本项目包含以下限界上下文：
- **admin** - 管理员、角色、权限、菜单管理

## 技术栈

- Java 21 / Spring Boot 3.4.x / Maven
- 基座框架：cartisan-boot（DDD、Web、Security、Data、AI、Event、Test）
- 缓存/消息：Redis
- 数据库：PostgreSQL
- 端口：8081

## 架构约束

### DDD 六边形架构
```
北向接口（Driving Side）: REST API | GraphQL | gRPC | MQ
    ↓
应用层（Application Layer）: 北向接口适配层 | 上下文出入口
    ↓
领域层（Domain Layer）: 核心业务逻辑 | 南向端口接口
    ↓
南向接口（Driven Side）: 密码加密 | 持久化 | 缓存 | 外部服务
    ↓
基础设施层（Infrastructure）: 南向接口的适配器实现
```

**核心原则**：
- 领域层零外部依赖（所有外部依赖通过端口接口解耦）
- 应用层是上下文出入口（北向接口 → 应用层 → 领域层）
- 限界上下文之间通过领域事件通信，禁止直接跨上下文调用
- 所有金额/虚拟币使用 BigDecimal / long，禁止浮点数
- 构造函数注入，禁止 @Autowired 字段注入

## 编码规范

### 包结构规范
```
com.aieducenter.{context}
├── package-info.java           # 上下文说明（@BoundedContext 注解）
├── domain/                     # 领域层
│   ├── aggregate/              # 聚合根
│   ├── entity/                 # 实体
│   ├── repository/             # 仓储接口
│   ├── service/                # 领域服务
│   ├── port/                   # 端口接口（推荐）
│   ├── error/                  # 领域错误定义
│   └── enums/                  # 领域枚举
├── application/                # 应用层
│   ├── dto/                    # DTO（command/query/response）
│   ├── mapper/                 # MapStruct 转换器
│   └── {Scenario}AppService.java
├── infrastructure/             # 基础设施层
│   ├── persistence/            # JPA Repository 实现
│   ├── redis/                  # Redis 适配器
│   └── messaging/              # MQ 适配器
└── endpoints/                  # 北向接口层
    ├── controller/             # REST API
    ├── api/                    # 外部 API
    └── listener/               # MQ Listener
```

### 代码规范
- **DTO**：使用 Java Record，构造函数校验不变量
- **聚合根**：继承 `AuditableSoftDeletable`（推荐），使用 `@Getter`/`@Setter` 注解
- **实体**：不继承 `AuditableSoftDeletable`（避免唯一索引冲突），单一代理主键
- **枚举**：实现 `BaseEnum<T>` 接口，Integer code 存储，框架自动转换
- **测试命名**：`given_{条件}_when_{操作}_then_{预期结果}`
- **测试断言**：使用 AssertJ，禁止无意义断言
- **集合初始化**：使用 hutool（`CollUtil.newHashSet()`、`MapUtil.newHashMap()`）

## 常用命令

- 编译：`mvn compile`
- 单元测试（无需 Docker）：`mvn test`
- 指定测试：`mvn test -Dtest=XxxTest`
- 全量检查（含 ArchUnit）：`mvn verify`
- 变异测试：`mvn org.pitest:pitest-maven:mutationCoverage`

## 开发流程

使用 Superpowers 技能驱动开发，按需求规模分层：

- **大需求**：先充分讨论，产出需求设计文档（含 Epic 拆解），再逐个 Epic 推进
- **Epic / 中需求**：讨论后产出 Backlog 文档（含 Feature 拆解），再逐个 Feature 推进
- **Feature / 小需求 / Bug**：直接用 Superpowers 技能（brainstorming -> writing-plans -> TDD -> verification）

阶段性完成后人工触发归档：提取有价值内容到 docs/guide/，然后清理过程文档。

## 快速参考

### cartisan-boot 核心注解
- `@BoundedContext` - 标注限界上下文（package-info.java）
- `@Aggregate` - 标注聚合根
- `@DomainService` - 标注领域服务
- `@Port(PortType)` - 标注端口接口（REPOSITORY/CLIENT/PUBLISHER）
- `@Adapter(PortType)` - 标注适配器实现
- `@EnumConvert(Enum.class)` - 枚举字段自动转换
- `@Condition` - 查询条件注解
- `@RequireAuth` / `@RequireRole` / `@RequirePermission` - 权限注解
- `@CurrentUser` - 注入当前用户 ID

### 常见错误码规范
格式：`{CONTEXT}_{NUMBER}`，如 `ADMIN_001`

### 数据库表命名规范
格式：`{prefix}_{table_name}`
- prefix：上下文前缀（`sys`）
- table_name：蛇形命名（snake_case）

### Flyway
- 历史表：`admin_flyway_schema_history`

### 安全路径
- 需认证路径：`/admin/**`

### 测试覆盖率目标
- 领域层：95%+
- 应用层：90%+
- 基础设施层：80%+
- 控制器层：70%+
