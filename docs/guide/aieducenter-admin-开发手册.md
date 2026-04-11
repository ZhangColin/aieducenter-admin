# cartisan-boot 使用手册

> **版本**：v0.9 | **日期**：2026-03-29
> **模块**：Core + Test + Web + Data-JPA + Event + Security + Data-Query + AI

---

## 一、模块能力清单

### 1.1 cartisan-core 模块

| 能力 | 说明 |
|------|------|
| **DDD 基础类型** | 聚合根、实体、值对象、领域事件、标识符 |
| **异常体系** | 统一错误码接口 + 业务异常层次 |
| **架构注解** | DDD 分层标记注解（限界上下文、聚合、端口、适配器） |
| **断言工具** | Design by Contract 风格的前置/后置条件断言 |
| **RedisKey 工具** | Redis Key 管理（前缀、过期时间） |

### 1.2 cartisan-test 模块

| 能力 | 说明 |
|------|------|
| **ArchUnit 规则** | DDD 分层、命名规范、禁止规则的自动验证 |
| **Testcontainers** | PostgreSQL + Redis 集成测试基类 |
| **API 测试** | MockMvc 测试基类 + 断言辅助 |
| **Fixture 工具** | 随机数据生成器 + 对象构建器 |

### 1.3 cartisan-web 模块

| 能力 | 说明 |
|------|------|
| **统一响应体** | `ApiResponse<T>`、`PageResponse<T>`、`FieldError` |
| **全局异常处理** | `@ControllerAdvice` 自动捕获异常并转换为响应 |
| **请求上下文** | `RequestContext` 存储 requestId、clientIp（ThreadLocal） |
| **DomainMapper** | MapStruct 批量转换默认方法（List/Set） |
| **TreeNode** | 树结构数据支持（前端树组件） |
| **防重提交** | `@PreventResubmit` 注解（基于 Redis） |
| **请求日志** | `RequestLogFilter` 记录请求信息 |
| **MDC 集成** | requestId 自动放入 MDC |
| **Jackson 配置** | 全局序列化配置（Long→String、日期格式等） |
| **自动响应包装** | `AutoResponseAdvice` 可选功能 |
| **自动配置** | Spring Boot AutoConfiguration 零配置启用 |

### 1.4 cartisan-data-jpa 模块

| 能力 | 说明 |
|------|------|
| **BaseRepository** | 约束 T 必须是 `AggregateRoot<?>`，继承 JPA + Specification |
| **事件自动发布** | Repository save() 时自动发布领域事件 |
| **审计支持** | `@CreatedDate`、`@LastModifiedDate`、`@CreatedBy`、`@LastModifiedBy` |
| **软删除** | `@SQLRestriction` 自动过滤已删除记录 |
| **分布式 ID** | TSID 生成器（42 位时间戳 + 22 位随机数） |
| **@Condition 注解** | 简化 JPA Specification 查询（11 种条件类型） |
| **Druid 集成** | 支持 Druid 数据源，提供 SQL 监控、慢 SQL 记录、防火墙功能 |
| **枚举增强** | `BaseEnum` + `@EnumConvert` 实现 Enum ↔ Integer 自动转换 |

### 1.5 cartisan-event 模块

| 能力 | 说明 |
|------|------|
| **事件发布器** | `DomainEventPublisher` 接口 + Spring 实现 |
| **事务监听** | 支持 `@TransactionalEventListener(phase=AFTER_COMMIT)` |
| **自动配置** | Spring Boot AutoConfiguration 零配置启用 |

### 1.6 cartisan-security 模块

| 能力 | 说明 |
|------|------|
| **权限注解** | `@RequireAuth`、`@RequireRole`、`@RequirePermission` |
| **权限扫描** | `PermissionScanner` 扫描代码中的权限注解，自动采集权限定义 |
| **MVC 拦截器** | `SecurityInterceptor` 处理鉴权逻辑 |
| **异常处理** | `SecurityExceptionHandler` 处理 Sa-Token 异常（401/403） |
| **安全上下文** | `SecurityContext` 获取当前用户信息 |
| **多租户上下文** | `TenantContext` 获取租户 ID（Header > Session 优先级） |
| **租户过滤器** | `TenantContextFilter` 解析租户 ID，兼容 Virtual Threads |
| **认证服务** | `AuthenticationService` 接口 + Sa-Token 实现 |
| **@CurrentUser 注解** | Controller 方法参数直接注入当前用户 ID |
| **自动配置** | Spring Boot AutoConfiguration 零配置启用 |

### 1.7 cartisan-data-query 模块

| 能力 | 说明 |
|------|------|
| **jOOQ 自动配置** | `DSLContext` Bean 自动配置（PostgreSQL 方言、SQL 日志） |
| **多租户查询** | `JooqTenantSupport.eqTenantId()` 租户过滤条件生成 |
| **自动配置** | Spring Boot AutoConfiguration 零配置启用 |

### 1.8 cartisan-ai 模块

| 能力 | 说明 |
|------|------|
| **统一对话模型** | `ChatMessage`、`ChatRequest`、`ChatResponse`、`TokenUsage`、`ChatStreamEvent` |
| **Provider SPI** | `ModelProvider` 接口，支持同步调用和流式调用 |
| **Provider Registry** | 按提供商 ID 或模型名称查找 Provider |
| **OpenAI Provider** | 支持 OpenAI API（同步 + SSE 流式） |
| **DeepSeek Provider** | 兼容 OpenAI 协议，支持 DeepSeek API |
| **Anthropic Provider** | 支持 Anthropic Claude API（独立协议） |
| **SSE 流式工具** | `SseHelper` 将 `Flux<ChatStreamEvent>` 转换为 `SseEmitter` |
| **ModelUsageListener** | Token 使用量监听扩展点 |
| **自动配置** | Spring Boot AutoConfiguration 零配置启用 |

---

## 二、核心概念和 API

### 2.1 DDD 基础类型（com.cartisan.core.domain）

| 接口/类 | 方法 | 说明 |
|---------|------|------|
| `AggregateRoot` | - | 聚合根标记接口 |
| `AbstractAggregateRoot<T>` | `registerEvent(event)` | 注册领域事件 |
| | `getDomainEvents()` | 获取待发布事件列表 |
| | `clearDomainEvents()` | 清空事件列表 |
| `DomainEntity<T, ID>` | `getId()` | 获取实体 ID |
| | `sameIdentityAs(other)` | 判断是否为同一实体 |
| `ValueObject<T>` | `sameValueAs(other)` | 判断值是否相等 |
| `Identity<T>` | `value()` | 获取标识符值 |
| `DomainEvent` | `eventId()` | 事件 ID（UUID） |
| | `occurredAt()` | 发生时间 |
| | `aggregateId()` | 聚合根 ID |
| | `eventType()` | 事件类型名 |

### 2.1.1 BaseEnum 接口（com.cartisan.core.domain）

| 接口/方法 | 说明 |
|-----------|------|
| `BaseEnum<T>` | 业务枚举基础接口，包含 code/name 映射 |
| `getCode()` | 获取枚举的整数值（存储到数据库） |
| `getName()` | 获取枚举的显示名称 |
| `parseByCode(Class, Integer)` | 根据 code 查找枚举（找不到返回 null） |
| `requireByCode(Class, Integer)` | 根据 code 查找枚举（找不到抛异常） |

### 2.1.2 @EnumConvert 注解（com.cartisan.data.jpa.annotation）

| 注解 | 属性 | 说明 |
|------|------|------|
| `@EnumConvert` | `value` | 指定枚举类型，配合 `UniversalEnumConverter` 使用 |

### 2.1.3 UniversalEnumConverter（com.cartisan.data.jpa.converter）

| 类 | 方法 | 说明 |
|----|------|------|
| `UniversalEnumConverter<E>` | `convertToDatabaseColumn(E)` | Enum → Integer（写数据库） |
| | `convertToEntityAttribute(Integer)` | Integer → Enum（读数据库） |

### 2.1.4 BaseEnum 序列化支持（com.cartisan.web.config）

| 类 | 说明 |
|----|------|
| `BaseEnumSerializer` | Jackson 序列化器：BaseEnum → Integer code |
| `BaseEnumDeserializer` | Jackson 反序列化器：Integer → BaseEnum（使用 ContextualDeserializer） |

### 2.2 异常体系（com.cartisan.core.exception）

| 类 | 说明 |
|----|------|
| `CodeMessage` | 错误码接口：`code()`, `message()`, `httpStatus()` |
| `BaseCodeMessage` | HTTP 规范错误码枚举（11 个）+ 通用业务错误码（4 个） |
| `CartisanException` | 异常基类，支持参数化消息 |
| `DomainException` | 领域层异常（业务规则违反） |
| `ApplicationException` | 应用层异常（用例/流程问题） |

### 2.3 架构注解（com.cartisan.core.stereotype）

| 注解 | 目标 | 用途 |
|------|------|------|
| `@BoundedContext` | PACKAGE | 标注限界上下文 |
| `@Aggregate` | TYPE | 标注聚合根 |
| `@DomainService` | TYPE | 标注领域服务 |
| `@Port(PortType)` | TYPE | 标注端口接口 |
| `@Adapter(PortType)` | TYPE | 标注适配器实现 |

### 2.4 断言工具（com.cartisan.core.util.Assertions）

| 方法 | 异常类型 | 用途 |
|------|---------|------|
| `require(condition, codeMessage, args)` | `DomainException` | 前置条件断言 |
| `ensure(condition, message)` | `IllegalStateException` | 后置条件断言 |
| `requirePresent(optional)` | `DomainException` | Optional 存在性断言（快捷版） |
| `requirePresent(optional, codeMessage)` | `DomainException` | Optional 存在性断言（完整版） |

### 2.5 ArchUnit 规则（com.cartisan.test.archunit）

| 类 | 规则数 | 说明 |
|----|--------|------|
| `CartesianLayeringRules` | 4 | DDD 分层规则 |
| `CartesianNamingRules` | 4 | 命名规范规则 |
| `CartesianProhibitionRules` | 3 | 禁止规则 |
| `CartesianArchRules` | 11 | 聚合全部规则 |

### 2.6 Testcontainers（com.cartisan.test.container）

| 类 | 容器 | 说明 |
|----|------|------|
| `PostgresTestContainer` | PostgreSQL 16 | `@ServiceConnection` 自动注入 |
| `RedisTestContainer` | Redis 7 | `@ServiceConnection` 自动注入 |

### 2.7 测试基类（com.cartisan.test.base）

| 类 | 继承关系 | 提供能力 |
|----|----------|----------|
| `IntegrationTestBase` | - | 容器启动 + 数据清理 |
| `ApiTestBase` | `IntegrationTestBase` | + MockMvc |

### 2.8 Fixture 工具（com.cartisan.test.fixture）

| 类 | 方法示例 | 说明 |
|----|----------|------|
| `FixtureStrings` | `randomString()`, `randomEmail()` | 字符串随机生成 |
| `FixtureNumbers` | `randomInt()`, `randomAmount()` | 数字/金额随机生成 |
| `FixtureDates` | `pastDays(7)`, `futureDays(3)` | 日期随机生成 |
| `FixtureBuilder<T>` | `of(clazz).with(name, value).build()` | 对象构建器 |

### 2.9 Web 响应体（com.cartisan.web.response）

| 类/Record | 方法/字段 | 说明 |
|-----------|----------|------|
| `ApiResponse<T>` | `code`, `message`, `data`, `requestId`, `errors` | 统一响应字段 |
| | `ok(T data)` | 成功响应（带数据） |
| | `ok()` | 成功响应（无数据） |
| | `error(CodeMessage)` | 错误响应（枚举） |
| | `error(CodeMessage, Object...)` | 错误响应（参数化） |
| | `error(int, String)` | 错误响应（自定义） |
| | `validationError(List<FieldError>)` | 校验失败响应 |
| `PageResponse<T>` | `items`, `total`, `page`, `size` | 分页响应字段 |
| `FieldError` | `field`, `message`, `errorCode` | 字段级错误 |

### 2.10 请求上下文（com.cartisan.web.context）

| 类 | 方法 | 说明 |
|----|------|------|
| `RequestContext` | `getRequestId()` | 获取请求追踪 ID（可能为 null） |
| | `getClientIp()` | 获取客户端 IP（可能为 null） |
| `RequestContextFilter` | - | 自动初始化 RequestContext（@Component） |

### 2.11 BaseRepository（com.cartisan.data.jpa.repository）

| 接口 | 约束 | 说明 |
|----|------|------|
| `BaseRepository<T, ID>` | `T extends AggregateRoot<?>` | 继承 JpaRepository + JpaSpecificationExecutor |
| | `ID extends Serializable` | ID 类型约束 |
| `BaseRepositoryImpl` | 重写 `save()` | JPA save 后自动发布领域事件 |

### 2.12 审计与软删除（com.cartisan.data.jpa.domain）

| 类/接口 | 字段/注解/方法 | 说明 |
|----|----------|------|
| `Auditable` | `@CreatedDate createdAt` | 创建时间（LocalDateTime，自动填充） |
| | `@LastModifiedDate lastModifiedDate` | 修改时间（LocalDateTime，自动更新） |
| | `@CreatedBy createdBy` | 创建人ID（Long，需 AuditorAware） |
| | `@LastModifiedBy lastModifiedBy` | 修改人ID（Long，需 AuditorAware） |
| `AuditableSoftDeletable` | 继承 `Auditable`，实现 `SoftDeletable` | 可审计且可软删除实体基类 |
| | `boolean deleted` | 软删除标记 |
| | `@SQLRestriction("deleted = false")` | 查询自动过滤 |
| | `markAsDeleted()` | 标记为已删除（领域方法），供 Repository.delete() 调用 |
| `SoftDeletable` | `markAsDeleted()` | 软删除接口方法 |
| | `getDeleted()` | 获取软删除标记值 |

**自动软删除支持**：

| 方法 | 行为 |
|------|------|
| `BaseRepositoryImpl.delete(T)` | 实现 `SoftDeletable` 接口的实体调用 `markAsDeleted()` + `save()`，其他实体物理删除 |
| `BaseRepositoryImpl.deleteById(ID)` | 先查找实体，再调用 `delete()` |
| `BaseRepositoryImpl.deleteAll(Iterable)` | 混合处理：软删除实体 UPDATE，其他 DELETE |
| `BaseRepositoryImpl.deleteAll()` | 软删除实体批量 UPDATE，其他物理删除 |
| `BaseRepositoryImpl.deleteAllById()` | 批量按 ID 删除 |

**重要设计取舍**：
- 软删除复用 `save()` 的事件发布逻辑
- 通过 `JpaRepositoryFactoryEntryCustomizer` 全局配置，业务端无需手动指定 `repositoryBaseClass`
- JPQL `@Query` 查询不受 `@SQLRestriction` 影响，需手动添加软删除条件（见 DATA-005）

### 2.13 TSID 生成器（com.cartisan.data.jpa.id）

| 类 | 方法 | 说明 |
|----|------|------|
| `TsidGenerator` | `generate()` | 生成时间排序的全局唯一 Long ID |
| | `toInstant(long tsid)` | 从 TSID 提取生成时间 |
| | `newInstance()` | 创建默认实例（ThreadLocalRandom） |
| | `withRandom(Random)` | 测试用：指定随机数源 |

### 2.14 领域事件发布器（com.cartisan.event）

| 接口/类 | 方法 | 说明 |
|---------|------|------|
| `DomainEventPublisher` | `publish(DomainEvent)` | 发布领域事件 |
| `SpringDomainEventPublisher` | - | 委托给 Spring ApplicationEventPublisher |

### 2.15 权限注解（com.cartisan.security.annotation）

| 注解 | 目标 | 说明 |
|------|------|------|
| `@RequireAuth` | TYPE/METHOD | 需要登录 |
| `@RequireRole` | TYPE/METHOD | 需要指定角色（OR 逻辑） |
| `@RequirePermission` | METHOD | 需要指定权限（单值，支持 name 和 scope 属性） |

**@RequirePermission 属性说明：**
- `value`: 权限 code，格式 `{context}:{module}:{action}`
- `name`: 权限显示名称（可选，空字符串时使用 code）
- `scope`: 权限作用域（可选，空字符串时转为 null）

### 2.16 SecurityContext（com.cartisan.security.context）

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `getCurrentUserId()` | `Long` / `null` | 获取当前用户 ID |
| `getCurrentUsername()` | `String` / `null` | 获取当前用户名（登录 ID） |
| `hasRole(String role)` | `boolean` | 判断是否拥有角色 |
| `hasPermission(String permission)` | `boolean` | 判断是否拥有权限 |
| `isAuthenticated()` | `boolean` | 判断是否已登录 |

### 2.17 TenantContext（com.cartisan.security.context）

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `getCurrentTenantId()` | `Long` / `null` | 获取当前租户 ID |
| `hasTenant()` | `boolean` | 判断是否有租户上下文 |
| `requireTenant()` | `Long` | 获取租户 ID，不存在抛异常 |

**存储机制**：使用 `ScopedValue`（Java 21+），兼容 Virtual Threads，作用域结束自动清理。

### 2.18 AuthenticationService（com.cartisan.security.authentication）

| 接口 | 方法 | 说明 |
|------|------|------|
| `AuthenticationService` | `login(Long loginId)` | 创建登录会话 |
| | `logout()` | 销毁当前会话 |
| | `getTokenInfo()` | 获取当前 Token 信息 |
| | `authenticate(username, password)` | 业务层扩展点（默认抛异常） |

### 2.19 TokenInfo（com.cartisan.security.authentication）

| 字段 | 类型 | 说明 |
|------|------|------|
| `token` | `String` | Token 值 |
| `loginId` | `Long` | 用户标识 |
| `expireTime` | `Instant` | 过期时间 |

### 2.20 异常处理器（com.cartisan.security.config）

| 异常类型 | HTTP 状态码 | 响应消息 |
|---------|------------|---------|
| `NotLoginException` | 401 UNAUTHORIZED | 未登录或登录已过期 |
| `NotRoleException` | 403 FORBIDDEN | 无权限访问 |
| `NotPermissionException` | 403 FORBIDDEN | 无权限访问 |

### 2.21 配置属性（com.cartisan.security.config.properties）

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `cartisan.security.interceptor.path-patterns` | `List<String>` | `["/**"]` | 拦截器生效路径 |
| `cartisan.security.interceptor.exclude-path-patterns` | `List<String>` | `["/error", "/actuator/**"]` | 排除路径 |

### 2.22 @CurrentUser 注解（com.cartisan.security.annotation）

| 注解/类 | 目标/方法 | 说明 |
|---------|----------|------|
| `@CurrentUser` | PARAMETER | Controller 方法参数注解，注入当前用户 ID |
| `CurrentUserMethodArgumentResolver` | `supportsParameter()` | 判断参数是否支持解析（有注解 + 类型为 Long 或 Optional&lt;Long&gt;） |
| | `resolveArgument()` | 从 SecurityContext 获取用户 ID 并注入 |

**支持的参数类型**：
- `@CurrentUser Long userId` — 必需登录，未登录抛 `NotLoginException`（401）
- `@CurrentUser Optional<Long> userId` — 可选登录，未登录返回 `Optional.empty()`

**执行时序**：Filter → Interceptor（@RequireAuth 检查）→ 参数解析（@CurrentUser）→ Controller

### 2.23 PermissionScanner（com.cartisan.security.permission）

| 接口/类 | 方法 | 说明 |
|---------|------|------|
| `PermissionScanner` | `scanAll()` → `List<Permission>` | 扫描全部权限 |
| | `scanByScope(String scope)` → `List<Permission>` | 按作用域扫描权限（null = 无作用域） |
| `Permission` | `code()` → `String` | 权限 code |
| | `name()` → `String` | 显示名称 |
| | `scope()` → `String` | 作用域（可能为 null） |
| `DefaultPermissionScanner` | - | 默认实现，自动注册为 Spring Bean |

**使用示例：**

```java
@Service
public class PermissionInitService {
    private final PermissionScanner permissionScanner;

    public void initPermissions() {
        // 扫描指定作用域
        List<Permission> adminPermissions = permissionScanner.scanByScope("admin");

        // 扫描全部权限
        List<Permission> allPermissions = permissionScanner.scanAll();

        // 同步到权限管理表
        permissionRepository.syncPermissions(allPermissions);
    }
}
```

### 2.24 DSLContext 自动配置（com.cartisan.data.query.config）

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `cartisan.data-query.jooq.sql-logging` | `boolean` | `false` | 是否启用 SQL 执行日志 |

**自动配置类**：`JooqAutoConfiguration`
- 条件：存在 `DataSource` 且无用户自定义 `DSLContext`
- 方言：固定为 `SQLDialect.POSTGRES`
- Bean：可被用户自定义配置覆盖

### 2.25 JooqTenantSupport（com.cartisan.data.query.support）

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `eqTenantId(TableField<?, Long>)` | `Condition` | 生成租户等值过滤条件 |

**行为**：
- 有租户上下文时：返回 `tenantIdField.eq(tenantId)`
- 无租户上下文时：返回 `DSL.noCondition()`（不添加过滤）

**依赖说明**：需要 `cartisan-security` 模块（可选依赖）

### 2.26 AI 对话模型（com.cartisan.ai.model）

| 类/Record | 字段/方法 | 说明 |
|----------|----------|------|
| `Role` | `SYSTEM / USER / ASSISTANT` | 消息角色枚举 |
| `ChatMessage` | `role()`, `content()` | 单条对话消息 |
| `ChatRequest` | `model`, `messages`, `temperature`, `maxTokens`, `stream` | 对话请求 |
| | `withStream(boolean)` | 创建流式/非流式请求副本 |
| `ChatResponse` | `content()`, `model()`, `usage()` | 对话响应 |
| `TokenUsage` | `promptTokens()`, `completionTokens()`, `totalTokens()` | Token 使用统计 |
| `ChatStreamEvent` | `delta()`, `finished()`, `usage()` | 流式事件 |

### 2.27 ModelProvider SPI（com.cartisan.ai.provider）

| 接口/类 | 方法 | 说明 |
|---------|------|------|
| `ModelProvider` | `id()` → `String` | 提供商标识（openai/anthropic/deepseek） |
| | `supportedModels()` → `List<String>` | 支持的模型列表 |
| | `chat(ChatRequest)` → `ChatResponse` | 同步调用 |
| | `chatStream(ChatRequest)` → `Flux<ChatStreamEvent>` | 流式调用 |
| `ModelProviderRegistry` | `getProvider(providerId)` → `ModelProvider` | 按 ID 查找 Provider |
| | `getProviderByModel(modelName)` → `ModelProvider` | 按模型名查找 Provider |
| | `listProviders()` → `List<ModelProvider>` | 列出所有 Provider |
| | `chat(providerId, request)` → `ChatResponse` | 通过 Registry 调用 |
| | `chatStream(providerId, request)` → `Flux<ChatStreamEvent>` | 通过 Registry 流式调用 |
| `ModelUsageListener` | `onUsage(providerId, model, usage)` | Token 使用监听器（扩展点） |

### 2.28 SSE 流式工具（com.cartisan.ai.sse）

| 类 | 方法 | 说明 |
|----|------|------|
| `SseHelper` | `toSse(Flux<ChatStreamEvent>)` → `SseEmitter` | 转换为 SSE（无回调） |
| | `toSse(Flux<ChatStreamEvent>, Consumer<TokenUsage>)` → `SseEmitter` | 转换为 SSE（usage 回调） |
| `SseProperties` | `timeout`（默认 30 秒） | SSE 超时配置 |

### 2.29 RedisKey 工具（com.cartisan.core.util.RedisKey）

| 类/方法 | 说明 |
|---------|------|
| `RedisKey.of(prefix, expireSeconds)` | 创建带过期时间的 Key |
| `RedisKey.permanent(prefix)` | 创建永不过期的 Key |
| `key(suffix)` | 生成完整的 Redis Key（格式：`prefix:suffix`） |
| `expireSeconds()` | 获取过期时间（秒），0 表示永不过期 |
| `isPermanent()` | 判断是否为永久 Key |

### 2.30 DomainMapper（com.cartisan.web.mapper）

| 接口/方法 | 说明 |
|----------|------|
| `DomainMapper<S, T>` | MapStruct 转换器基接口 |
| `convert(S source)` | 转换单个对象（需由 MapStruct 生成） |
| `convertList(List<S> sources)` | 批量转换 List（默认方法） |
| `convertSet(Set<S> sources)` | 批量转换 Set（默认方法） |

**注意**：`convertList` 和 `convertSet` 在输入为 null 或空时返回空集合。

### 2.31 TreeNode（com.cartisan.web.support）

| 类/方法 | 说明 |
|---------|------|
| `TreeNode<T>` | 树节点泛型类 |
| `TreeNode(id, name, parentId)` | 基本构造 |
| `TreeNode(id, name, parentId, children)` | 完整构造 |
| `TreeNodeBuilder.build(nodes, idMapper, parentIdMapper, rootParentId)` | 构建树形结构 |

### 2.32 @PreventResubmit（com.cartisan.web.resubmit）

| 注解/类 | 属性/方法 | 说明 |
|---------|----------|------|
| `@PreventResubmit` | `delaySeconds`（默认 20） | 防重提交时间窗口（秒） |
| | `prefix`（默认 ""） | Redis key 前缀 |
| `ResubmitLock` | `lock(key, delaySeconds)` | 基于 Redis 的分布式锁 |
| `ResubmitAspect` | - | AOP 切面，拦截注解方法 |

### 2.33 Jackson 全局配置（com.cartisan.web.config）

| 配置项 | 说明 |
|--------|------|
| `Long → String` | 解决 JavaScript Long 精度问题 |
| `LocalDateTime → ISO 8601` | 标准日期时间格式 |
| `BigDecimal → 禁止科学计数法` | 保持金额精度 |
| `Enum → 字符串` | 枚举值序列化为字符串 |
| `忽略未知属性` | 反序列化时忽略未知字段 |

### 2.34 RequestLogFilter（com.cartisan.web.filter）

| 类 | 说明 |
|----|------|
| `RequestLogFilter` | 记录 HTTP 请求基本信息（requestId、IP、方法、URI） |

**排除路径**：`/swagger-ui`、`/v3/api-docs`、`/swagger-resources`、`/druid`、`/actuator`

### 2.35 @Condition 注解（com.cartisan.data.jpa.specification）

| 注解/枚举 | 说明 |
|----------|------|
| `@Condition` | 查询条件注解（propName、type、blurry） |
| `ConditionType` | 11 种查询类型：EQUAL、NOT_EQUAL、GREATER、GREATER_EQUAL、LESS、LESS_EQUAL、INNER_LIKE、LEFT_LIKE、RIGHT_LIKE、IN、BETWEEN |
| `ConditionSpecifications.fromAnnotation(query)` | 从注解生成 Specification |

**详细使用指南**：[condition-annotation.md](condition-annotation.md)

### 2.36 AutoResponseAdvice（com.cartisan.web.response）

| 配置项 | 说明 |
|--------|------|
| `cartisan.web.auto-response.enabled` | 启用自动响应包装（默认 false） |

**排除路径**：`/swagger-ui`、`/v3/api-docs`、`/actuator`

**详细使用指南**：[optional-features.md](optional-features.md)

---

## 三、使用示例

### 3.1 定义聚合根

```java
// ID 定义（推荐使用 Record）
public record OrderId(String value) implements Identity<String> {
    public OrderId {
        Objects.requireNonNull(value, "orderId cannot be null");
    }
}

// 领域事件
public class OrderCreatedEvent extends DomainEvent {
    private final String customerId;
    private final BigDecimal totalAmount;

    public OrderCreatedEvent(String orderId, String customerId, BigDecimal totalAmount) {
        super(orderId);
        this.customerId = customerId;
        this.totalAmount = totalAmount;
    }
}

// 聚合根
@Aggregate
public class Order extends AbstractAggregateRoot<Order> implements AggregateRoot {
    private OrderId id;
    private OrderStatus status;
    private List<OrderItem> items;

    public Order(String customerId, List<OrderItem> items) {
        this.id = new OrderId(UUID.randomUUID().toString());
        this.status = OrderStatus.PENDING;
        this.items = new ArrayList<>(items);

        BigDecimal totalAmount = calculateTotal();
        registerEvent(new OrderCreatedEvent(id.value(), customerId, totalAmount));
    }

    public void ship() {
        Assertions.require(
            this.status != OrderStatus.SHIPPED,
            OrderError.CANNOT_SHIP_SHIPPED
        );

        this.status = OrderStatus.SHIPPED;
        registerEvent(new OrderShippedEvent(id.value()));
    }

    public OrderId getId() {
        return id;
    }
}
```

### 3.2 使用 BaseEnum 枚举增强

```java
// 1. 定义业务枚举，实现 BaseEnum 接口
@Getter
@AllArgsConstructor
public enum UserStatus implements BaseEnum<UserStatus> {
    ACTIVE(1, "激活"),
    INACTIVE(0, "未激活");

    private final Integer code;
    private final String name;
}

// 2. 实体中使用 @EnumConvert 注解
@Entity
@Table(name = "users")
public class User {
    @Id
    private Long id;

    @EnumConvert(UserStatus.class)
    @Column(name = "status")
    private UserStatus status;
}

// 3. DTO 中直接使用枚举类型
public record UserResponse(
    Long id,
    UserStatus status,        // 自动序列化为 code（Integer）
    String statusName         // MapStruct 自动调用 getName()
) {}

// 4. Controller 中自动反序列化
@PostMapping("/users")
public ApiResponse<Void> createUser(@RequestBody CreateUserRequest request) {
    // request.status() 已是 UserStatus 枚举
    userService.create(request);
    return ApiResponse.ok();
}

// JSON 请求示例：
// {
//   "name": "张三",
//   "status": 1          // 自动转换为 UserStatus.ACTIVE
// }
//
// JSON 响应示例：
// {
//   "code": 200,
//   "data": {
//     "id": 123,
//     "status": 1,       // UserStatus.ACTIVE.getCode()
//     "statusName": "激活"
//   }
// }
```

**数据流：**
- **前端 → 数据库**：JSON `{status: 1}` → Jackson 反序列化为 `UserStatus.ACTIVE` → JPA 转换为 `1` → 数据库
- **数据库 → 前端**：数据库 `1` → JPA 转换为 `UserStatus.ACTIVE` → Jackson 序列化为 `1` → JSON

### 3.3 使用异常体系

```java
// 领域层 - 业务规则违反
public class Order extends AbstractAggregateRoot<Order> {
    public void cancel() {
        Assertions.require(
            this.status != OrderStatus.COMPLETED,
            OrderError.CANNOT_CANCEL_COMPLETED
        );
        this.status = OrderStatus.CANCELLED;
    }
}

// 应用层 - 用例前置条件
public class OrderApplicationService {
    public OrderDto getOrder(Long orderId) {
        // 快捷版：标准 404 场景
        Order order = Assertions.requirePresent(
            orderRepository.findById(orderId)
        );
        return OrderDto.from(order);
    }

    public void cancelOrder(Long orderId, Long userId) {
        // 完整版：区分不同资源类型
        Order order = Assertions.requirePresent(
            orderRepository.findById(orderId),
            OrderError.ORDER_NOT_FOUND
        );

        Assertions.require(
            order.belongsToUser(userId),
            OrderError.NOT_ORDER_OWNER
        );

        order.cancel();
    }
}
```

### 3.4 使用架构注解

```java
// package-info.java - 标注限界上下文
@BoundedContext(name = "OrderManagement", subDomain = SubDomain.CORE)
package com.cartisan.order;

// 端口接口
@Port(PortType.REPOSITORY)
public interface OrderRepository extends BaseRepository<Order, OrderId> {
}

// 适配器实现
@Adapter(PortType.REPOSITORY)
public class JpaOrderRepository implements OrderRepository {
    // ...
}

// 领域服务
@DomainService
public class OrderPricingService {
    // 不属于任何聚合根的定价逻辑
}
```

### 3.5 使用 ArchUnit 规则

```java
// 业务项目中继承即可获得全部规则
@AnalyzeClasses(packages = "com.aieducenter")
public class ArchitectureTest extends CartisanArchRules {
    // 完成！所有规则自动生效
}

// 或选择性使用
@AnalyzeClasses(packages = "com.aieducenter")
public class ArchitectureTest {
    @ArchTest
    static final ArchRules layering = ArchRules.in(CartisanLayeringRules.class);

    @ArchTest
    static final ArchRules prohibition = ArchRules.in(CartisanProhibitionRules.class);
    // 不要 naming 规则
}
```

### 3.6 使用 Testcontainers 基类

```java
// Repository 集成测试
class OrderRepositoryTest extends IntegrationTestBase {
    @Autowired
    private OrderRepository orderRepository;

    @Test
    void shouldSaveOrder() {
        // 数据库已清理（@BeforeEach TRUNCATE）
        Order order = new Order("customer-123", List.of());
        orderRepository.save(order);

        assertThat(orderRepository.findById(order.getId())).isPresent();
    }
}

// Controller API 测试
class OrderControllerTest extends ApiTestBase {
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateOrder() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("customer-123");
        String json = objectMapper.writeValueAsString(request);

        mvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").exists());
    }
}
```

### 3.7 使用 Fixture 工具

```java
class OrderServiceTest {
    @Test
    void shouldCreateOrder() {
        // 字符串生成
        String orderId = FixtureStrings.randomString("ORDER-");
        String email = FixtureStrings.randomEmail();

        // 数字生成
        Long customerId = FixtureNumbers.randomId();
        BigDecimal amount = FixtureNumbers.randomAmount();

        // 日期生成
        LocalDateTime orderDate = FixtureDates.now();
        LocalDateTime dueDate = FixtureDates.futureDays(7);

        // 对象构建
        Order order = FixtureBuilder.of(Order.class)
            .with("id", new OrderId(orderId))
            .with("customerId", customerId)
            .build();
    }

    @Test
    void shouldGenerateRepeatableData_whenSeedSet() {
        // 设置种子，测试可重复
        FixtureSeeds.setGlobalSeed(12345L);

        String str1 = FixtureStrings.randomString();
        String str2 = FixtureStrings.randomString();

        assertThat(str1).isEqualTo(str2);  // 相同种子 → 相同序列

        FixtureSeeds.resetSeed();
    }
}
```

### 3.8 使用 ApiResponse 响应体

```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    // 成功响应（带数据）
    @GetMapping("/{id}")
    public ApiResponse<OrderDto> getOrder(@PathVariable Long id) {
        Order order = orderService.findById(id);
        return ApiResponse.ok(OrderDto.from(order));
    }

    // 成功响应（无数据）
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteOrder(@PathVariable Long id) {
        orderService.delete(id);
        return ApiResponse.ok();
    }
}
```

### 3.9 使用 RequestContext

```java
// 在任何地方获取请求上下文
@Service
public class OrderService {

    public void createOrder(CreateOrderRequest request) {
        String requestId = RequestContext.getRequestId();
        String clientIp = RequestContext.getClientIp();

        log.info("Creating order, requestId={}, clientIp={}", requestId, clientIp);
        // ...
    }
}

// requestId 生成逻辑（RequestContextFilter 自动执行）：
// 1. 优先从 X-Request-Id Header 读取
// 2. 否则生成 UUID
```

### 3.10 定义 Repository（泛型约束）

```java
// 聚合根
@Entity
public class Order extends AbstractAggregateRoot<Order> {
    @Id
    private Long id;

    public void ship() {
        registerEvent(new OrderShippedEvent(id));
    }
}

// Repository 接口（T 必须是 AggregateRoot<?>）
public interface OrderRepository extends BaseRepository<Order, Long> {
    // 继承全部 JPA 方法 + Specification
    // save() 时自动发布领域事件
}

// 使用
@Service
public class OrderService {
    private final OrderRepository orderRepository;

    public void createOrder(Order order) {
        order.registerEvent(new OrderCreatedEvent(order.getId()));
        orderRepository.save(order);  // 自动发布事件
    }
}
```

### 3.11 使用审计和软删除基类

```java
// 仅审计
@Entity
public class Product extends Auditable {
    @Id private Long id;
    private String name;
    // 自动拥有：createdAt, lastModifiedDate, createdBy, lastModifiedBy
}

// 审计 + 软删除
@Entity
public class Order extends AuditableSoftDeletable {
    @Id private Long id;
    private String status;
    // 自动拥有：审计字段 + deleted（带 @SQLRestriction）
}

// 软删除操作
orderRepository.delete(order);  // UPDATE SET deleted = true
orderRepository.findAll();      // 自动过滤 deleted = true
```

### 3.12 使用 TSID 生成器

```java
@Entity
public class Order extends AbstractAggregateRoot<Order> {

    @Id
    private Long id;

    @PrePersist
    void generateId() {
        if (id == null) {
            id = tsidGenerator.generate();
        }
    }
}

// 或在 Service 层生成
@Service
public class OrderService {
    private final TsidGenerator tsidGenerator;

    public Long createOrder() {
        Long orderId = tsidGenerator.generate();
        Instant createTime = tsidGenerator.toInstant(orderId);
        // ...
        return orderId;
    }
}
```

### 3.13 监听领域事件

```java
@Component
public class OrderEventHandler {

    // 事务提交后执行（推荐）
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCreatedEvent event) {
        // 发送通知、调用外部服务等
        notificationService.sendOrderCreated(event);
    }

    // 事务内同步执行
    @EventListener
    public void handle2(OrderShippedEvent event) {
        // 同库操作，如更新其他聚合根
    }
}
```

### 3.14 使用权限注解

```java
// 类级别注解
@RestController
@RequireAuth  // 类内所有方法都需要登录
@RequestMapping("/api/v1/users")
public class UserController {
    @GetMapping("/me")
    public ApiResponse<User> getCurrentUser() { ... }
}

// 方法级别注解
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @RequireRole({"admin"})
    @PostMapping("/users")
    public ApiResponse<Void> createUser() { ... }

    @RequirePermission(
        value = "admin:user:read",
        name = "平台管理 / 用户管理 / 查看",
        scope = "admin"
    )
    @GetMapping("/users")
    public ApiResponse<List<User>> listUsers() { ... }

    @RequirePermission("admin:user:write")
    @PostMapping("/users")
    public ApiResponse<Void> createUser() { ... }
}

// 方法覆盖类注解
@RestController
@RequireAuth  // 默认需要登录
@RequestMapping("/api/v1/public")
public class PublicController {

    @GetMapping("/info")
    public ApiResponse<Info> getInfo() { ... }  // 需要登录

    @RequireAuth(false)  // 覆盖类注解，允许匿名访问
    @GetMapping("/ping")
    public ApiResponse<String> ping() { ... }
}
```

**权限 Code 规范：** 采用 3 级结构 `{context}:{module}:{action}`
- context: 限界上下文（如 admin）
- module: 业务模块（如 user）
- action: 操作（如 read/write/delete）

### 3.15 使用 SecurityContext

```java
@Service
public class OrderService {

    public void createOrder(CreateOrderRequest request) {
        // 推荐用法：先检查是否登录
        if (SecurityContext.isAuthenticated()) {
            Long userId = SecurityContext.getCurrentUserId();
            String username = SecurityContext.getCurrentUsername();

            // 判断角色/权限
            boolean isAdmin = SecurityContext.hasRole("admin");
            boolean canCreate = SecurityContext.hasPermission("order:create");

            // 使用用户信息...
        }
    }

    // 或者：对返回值做 null 检查
    public void updateOrder(Long orderId, UpdateOrderRequest request) {
        Long userId = SecurityContext.getCurrentUserId();
        if (userId != null) {
            // 使用 userId...
        }
    }
}
```

### 3.16 使用 TenantContext

```java
@Service
public class OrderService {

    public void createOrder(CreateOrderRequest request) {
        // 获取租户 ID（可能为 null）
        Long tenantId = TenantContext.getCurrentTenantId();

        // 判断是否有租户上下文
        if (TenantContext.hasTenant()) {
            // 使用租户 ID...
            Order order = new Order(tenantId, request);
            orderRepository.save(order);
        }
    }

    // 强制必须有租户上下文
    public void deleteOrder(Long orderId) {
        Long tenantId = TenantContext.requireTenant();  // 无租户抛异常
        Order order = orderRepository.findByIdAndTenantId(orderId, tenantId)
            .orElseThrow();
        orderRepository.delete(order);
    }
}
```

### 3.17 使用 AuthenticationService

```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authService;

    // 业务层验证密码后调用 login
    @PostMapping("/login")
    public ApiResponse<TokenInfo> login(@RequestBody LoginRequest request) {
        // 1. 业务层验证密码
        User user = userService.validatePassword(request.getUsername(), request.getPassword());

        // 2. 调用认证服务创建会话
        TokenInfo tokenInfo = authService.login(user.getId());

        // 3. 可选：设置租户 ID 到 Session
        StpUtil.getSession().set("tenantId", user.getTenantId());

        return ApiResponse.ok(tokenInfo);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.ok();
    }

    @GetMapping("/token-info")
    public ApiResponse<TokenInfo> getTokenInfo() {
        TokenInfo tokenInfo = authService.getTokenInfo();
        if (tokenInfo == null) {
            return ApiResponse.error(401, "未登录");
        }
        return ApiResponse.ok(tokenInfo);
    }
}
```

### 3.18 使用 @CurrentUser 注解

```java
// 必需登录场景
@RestController
@RequestMapping("/api/users")
public class UserController {

    // 方式一：只用 @CurrentUser
    @GetMapping("/profile")
    public ApiResponse<UserProfile> getProfile(@CurrentUser Long userId) {
        // 未登录会在参数解析时抛 NotLoginException → 401
        return ApiResponse.ok(userService.getProfile(userId));
    }

    // 方式二：@RequireAuth + @CurrentUser（推荐，语义更明确）
    @RequireAuth
    @GetMapping("/profile")
    public ApiResponse<UserProfile> getProfile(@CurrentUser Long userId) {
        // 未登录会在拦截器阶段被拦截，不会到达参数解析
        return ApiResponse.ok(userService.getProfile(userId));
    }

    @PutMapping("/profile")
    public ApiResponse<Void> updateProfile(@CurrentUser Long userId,
                                           @RequestBody UpdateProfileCommand cmd) {
        userService.updateProfile(userId, cmd);
        return ApiResponse.ok();
    }
}

// 可选登录场景（允许匿名访问）
@RestController
@RequestMapping("/api/preferences")
public class PreferencesController {

    @GetMapping
    public ApiResponse<Preferences> getPreferences(@CurrentUser Optional<Long> userId) {
        if (userId.isPresent()) {
            return ApiResponse.ok(preferencesService.getForUser(userId.get()));
        }
        return ApiResponse.ok(preferencesService.getDefault());
    }

    // 简化写法
    @GetMapping("/widgets")
    public ApiResponse<Widgets> getWidgets(@CurrentUser Optional<Long> userId) {
        return ApiResponse.ok(widgetsService.getWidgets(userId.orElse(null)));
    }
}
```

**@CurrentUser 与 @RequireAuth 的区别**：

| 注解 | 作用时机 | 适用场景 |
|------|---------|---------|
| `@RequireAuth` | 拦截器阶段 | 整个接口需要登录 |
| `@CurrentUser Long userId` | 参数解析阶段 | 需要使用 userId，未登录抛异常 |
| `@CurrentUser Optional<Long> userId` | 参数解析阶段 | 允许匿名访问，已登录可获取 userId |

### 3.19 配置拦截器路径

```yaml
# 仅保护 API 路径（默认是 /**）
cartisan:
  security:
    interceptor:
      path-patterns:
        - "/api/**"
        - "/admin/**"

# 完整配置示例
cartisan:
  security:
    interceptor:
      path-patterns:
        - "/api/**"
        - "/admin/**"
        - "/internal/**"
      exclude-path-patterns:
        - "/api/public/**"
        - "/api/health"
        - "/error"
        - "/actuator/**"
```

### 3.20 使用 jOOQ 自动配置

```java
// 引入依赖后，DSLContext 自动注入可用
@Service
public class UserService {
    private final DSLContext dsl;

    public UserService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<User> findActiveUsers() {
        return dsl.selectFrom(USER)
            .where(USER.STATUS.eq("ACTIVE"))
            .orderBy(USER.CREATED_AT.desc())
            .fetchInto(User.class);
    }

    // 复杂查询示例：JOIN + 聚合
    public List<OrderSummary> getOrderSummaries(LocalDate startDate) {
        return dsl.select(
                USER.ID,
                USER.NAME,
                DSL.count.ORDER_ID().as("orderCount"),
                DSL.sum(ORDER.TOTAL_AMOUNT).as("totalAmount")
            )
            .from(USER)
            .leftJoin(ORDER).on(ORDER.USER_ID.eq(USER.ID))
            .where(ORDER.CREATED_AT.ge(startDate))
            .groupBy(USER.ID, USER.NAME)
            .fetchInto(OrderSummary.class);
    }
}
```

### 3.21 启用 SQL 日志

```yaml
# application.yml
cartisan:
  data-query:
    jooq:
      sql-logging: true  # 启用 SQL 执行日志
```

### 3.22 使用多租户查询

```java
import static com.cartisan.data.query.support.JooqTenantSupport.eqTenantId;

@Service
public class UserService {
    private final DSLContext dsl;

    // 查询时自动添加租户过滤
    public List<User> listUsers() {
        return dsl.selectFrom(USER)
            .where(eqTenantId(USER.TENANT_ID))  // 自动根据当前租户过滤
            .fetchInto(User.class);
    }

    // 组合条件查询
    public List<User> listActiveUsers() {
        return dsl.selectFrom(USER)
            .where(
                USER.STATUS.eq("ACTIVE")
                .and(eqTenantId(USER.TENANT_ID))  // 租户过滤 + 其他条件
            )
            .fetchInto(User.class);
    }

    // 无租户上下文时，eqTenantId 返回 noCondition()，不影响查询
    public List<User> listAllUsersForAdmin() {
        return dsl.selectFrom(USER)
            .where(eqTenantId(USER.TENANT_ID))  // 管理员可能无租户限制
            .fetchInto(User.class);
    }
}
```

### 3.23 jOOQ 代码生成配置

在业务项目 `build.gradle.kts` 中添加：

```kotlin
plugins {
    id("nu.studer.jooq") version "8.2.1"
}

dependencies {
    // jOOQ 代码生成器依赖
    jooqGenerator("org.jooq:jooq-codegen")
    jooqGenerator("org.jooq:jooq-meta")
    jooqGenerator("org.postgresql:postgresql")
}

jooq {
    configuration {
        generator {
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
            }
            generate {
                isJavaTimeTypes = true  // 使用 java.time 类型
            }
            target {
                packageName = "com.example.db"  // 生成代码的包名
                directory = "build/generated/jooq"
            }
        }
    }
}

// 关键：先执行 Flyway 迁移，再生成 jOOQ 代码
tasks.named<nu.studer.jooq.GenerateJooqTask>("generateJooq") {
    dependsOn("flywayMigrate")
}
```

生成后使用：

```java
import static com.example.db.Tables.*;

// 类型安全的 DSL 查询
List<UserRecord> users = dsl.selectFrom(USER)
    .where(USER.AGE.gt(18))
    .fetch();
```

### 3.24 使用 cartisan-ai 同步调用

```java
@Service
public class AiService {
    private final ModelProviderRegistry registry;

    // 通过 Registry 调用（推荐，支持动态切换 Provider）
    public String chat(String providerId, String userMessage) {
        ChatRequest request = new ChatRequest(
            "gpt-4o-mini",  // 或其他模型名
            List.of(
                new ChatMessage(Role.SYSTEM, "You are a helpful assistant."),
                new ChatMessage(Role.USER, userMessage)
            ),
            0.7,    // temperature
            null,   // maxTokens
            false   // stream
        );

        ChatResponse response = registry.chat(providerId, request);
        return response.content();
    }

    // 直接注入特定 Provider
    public String chatWithOpenAi(String userMessage) {
        // OpenAiProvider 会自动注入
        ModelProvider provider = registry.getProvider("openai");
        // ... 同上
    }
}
```

### 3.25 使用 cartisan-ai 流式调用（SSE）

```java
@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final ModelProviderRegistry registry;
    private final SseHelper sseHelper;

    // 返回 SSE 流
    @GetMapping("/chat/stream")
    public SseEmitter chatStream(
            @RequestParam(defaultValue = "openai") String providerId,
            @RequestParam String message) {

        ChatRequest request = new ChatRequest(
            "gpt-4o-mini",
            List.of(new ChatMessage(Role.USER, message)),
            null, null, true  // stream = true
        );

        Flux<ChatStreamEvent> events = registry.chatStream(providerId, request);

        // 转换为 SSE，流结束时记录 Token 使用
        return sseHelper.toSse(events, usage -> {
            log.info("Token usage: prompt={}, completion={}",
                usage.promptTokens(), usage.completionTokens());
        });
    }

    // 或者返回 Flux（让客户端处理 Reactor 类型）
    @GetMapping(value = "/chat/flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatStreamEvent> chatFlux(@RequestParam String message) {
        ChatRequest request = new ChatRequest(
            "gpt-4o-mini",
            List.of(new ChatMessage(Role.USER, message)),
            null, null, true
        );

        return registry.chatStream("openai", request);
    }
}
```

### 3.26 配置 cartisan-ai Provider

```yaml
# application.yml
cartisan:
  ai:
    # OpenAI 配置
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com/v1  # 可选，支持代理/Azure
    # DeepSeek 配置
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com/v1
    # Anthropic 配置
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      base-url: https://api.anthropic.com
    # SSE 超时配置
    sse:
      timeout: 30s  # 默认 30 秒
```

**条件装配规则**：
- 只有配置了对应 `api-key` 的 Provider 才会被创建
- 至少需要配置一个 Provider，`ModelProviderRegistry` 才会被创建

### 3.27 实现 ModelUsageListener

```java
@Component
public class TokenUsageLogger implements ModelUsageListener {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageLogger.class);

    @Override
    public void onUsage(String providerId, String model, TokenUsage usage) {
        log.info("AI Usage - Provider: {}, Model: {}, Prompt: {}, Completion: {}, Total: {}",
            providerId, model, usage.promptTokens(), usage.completionTokens(), usage.totalTokens());

        // 可以写入数据库、发送监控告警等
    }
}
```

### 3.28 使用 RedisKey 工具

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final StringRedisTemplate redisTemplate;

    // 定义带过期时间的 Key（1 小时）
    private static final RedisKey USER_CACHE_KEY = RedisKey.of("user:cache", 3600);

    // 定义永不过期的 Key
    private static final RedisKey SYSTEM_CONFIG_KEY = RedisKey.permanent("system:config");

    public void cacheUser(Long userId, String userData) {
        String key = USER_CACHE_KEY.key(String.valueOf(userId));
        redisTemplate.opsForValue().set(key, userData, USER_CACHE_KEY.expireSeconds(), TimeUnit.SECONDS);
    }

    public String getUserCache(Long userId) {
        String key = USER_CACHE_KEY.key(String.valueOf(userId));
        return redisTemplate.opsForValue().get(key);
    }
}
```

### 3.29 使用 DomainMapper 批量转换

```java
@Mapper(componentModel = "spring")
public interface UserMapper extends DomainMapper<User, UserResponse> {
    // convert 方法由 MapStruct 自动生成
}

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;

    public UserResponse getUser(Long id) {
        User user = userRepository.findById(id);
        return userMapper.convert();  // 单个对象
    }

    public List<UserResponse> listUsers() {
        List<User> users = userRepository.findAll();
        return userMapper.convertList(users);  // List 批量转换
    }

    public Set<UserResponse> setUsers() {
        Set<User> users = userRepository.findAllAsSet();
        return userMapper.convertSet(users);  // Set 批量转换
    }
}
```

### 3.30 使用 TreeNode 构建树结构

```java
@Service
@RequiredArgsConstructor
public class DepartmentService {
    private final DepartmentRepository departmentRepository;

    public List<TreeNode<Long>> getDepartmentTree() {
        // 1. 从数据库获取扁平的部门列表
        List<Department> departments = departmentRepository.findAll();

        // 2. 转换为 TreeNode
        List<TreeNode<Long>> nodes = departments.stream()
            .map(dept -> new TreeNode<>(
                dept.getId(),
                dept.getName(),
                dept.getParentId()
            ))
            .toList();

        // 3. 构建树形结构
        return TreeNodeBuilder.build(
            nodes,
            id -> String.valueOf(id),           // ID 映射函数
            parentId -> String.valueOf(parentId), // 父 ID 映射函数
            0L                                    // 根节点的父 ID
        );
    }
}
```

### 3.31 使用 @PreventResubmit 防重提交

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    @PreventResubmit(delaySeconds = 10, prefix = "createUser")
    public ApiResponse<Void> createUser(@RequestBody CreateUserRequest request) {
        userService.create(request);
        return ApiResponse.ok();
    }

    @PostMapping("/batch")
    @PreventResubmit(delaySeconds = 30)  // 使用默认前缀
    public ApiResponse<Void> batchCreate(@RequestBody List<CreateUserRequest> requests) {
        userService.batchCreate(requests);
        return ApiResponse.ok();
    }
}
```

### 3.32 使用 @Condition 注解查询

```java
// 定义查询 DTO
public record ProductQuery(
    @Condition(type = ConditionType.INNER_LIKE) String name,
    @Condition(propName = "stock", type = ConditionType.GREATER_EQUAL) Integer minStock
) {}

// Repository 使用
public interface ProductRepository extends BaseRepository<Product, Long> {
    default List<Product> findByCondition(ProductQuery query) {
        return findAll(ConditionSpecifications.fromAnnotation(query));
    }
}
```

> **详细说明**：参见 [5.1 @Condition 注解详细说明](#五一详细功能指南)

### 3.33 启用自动响应包装

```yaml
# application.yml
cartisan:
  web:
    auto-response:
      enabled: true
```

启用后 Controller 可以直接返回数据：

```java
@GetMapping("/{id}")
public User getById(@PathVariable Long id) {
    return userService.findById(id);
}
```

> **详细说明**：参见 [5.9 AutoResponseAdvice 详细说明](#五一详细功能指南)

---

## 四、注意事项

### 4.1 DDD 相关

#### 4.1.1 架构规则

| 规则 | 说明 |
|------|------|
| **DDD-001** | DomainEntity 接口泛型方法中调泛型参数方法，必须先 `getClass()` 检查再强转 |
| **DDD-002** | ValueObject 的 `sameValueAs` 可直接委托 `equals` |
| **DDD-003** | 领域事件应自动生成 `eventId` 和 `occurredAt`，`aggregateId` 由子类提供 |
| **STYLE-003** | 使用 Record 实现 ValueObject 和 Identity |

#### 4.1.2 设计原则

cartisan-boot 提供 DDD 基础设施，但不强制 DDD 教条。以下是务实的设计取舍：

**聚合根是否必须避免使用 @Setter？**

否。cartisan-boot 不强制要求 DDD 封装原则。

- 没有业务逻辑的简单属性，直接用 `@Setter` 即可
- `changeName(newName)` 与 `setName(newName)` 在没有业务约束时没有本质区别
- 多参数一起修改时，如果参数间没有业务约束，写 `changeInfo(name, code, description)` 这种方法只会增加重载负担
- **只有当多个参数间存在业务约束时，才需要单独写业务方法**

```java
// ✅ 简单属性直接用 @Setter
@Entity
public class User extends AbstractAggregateRoot<User> {
    @Setter private String name;
    @Setter private String email;
}

// ✅ 有业务约束时写业务方法
@Entity
public class Order extends AbstractAggregateRoot<Order> {
    private OrderStatus status;
    private LocalDateTime completedAt;

    // 状态和完成时间有业务约束，必须一起修改
    public void complete() {
        require(this.status != OrderStatus.COMPLETED, "订单已完成");
        this.status = OrderStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }
}
```

**应用服务层是否可以直接调用聚合根的 setter？**

可以。

- 务实做法，应用服务层直接调用 `role.setName(command.name())` 是允许的
- cartisan-boot 不强制要求应用服务层必须调用聚合根的业务方法

```java
// ✅ 允许：应用服务层直接调用 setter
@Service
@RequiredArgsConstructor
public class RoleApplicationService {
    private final RoleRepository roleRepository;

    public void updateRole(UpdateRoleCommand command) {
        Role role = roleRepository.findById(command.id()).orElseThrow();
        role.setName(command.name());     // 直接调用 setter
        role.setCode(command.code());     // 直接调用 setter
        roleRepository.save(role);
    }
}
```

**ID 是否必须用强类型值对象（如 record）？**

否，不强求。

- `record AdminUserRoleId(Long adminId, Long roleId)` 这种强类型值对象会增加实现复杂度
- cartisan-boot 允许直接使用 `Long` 等基本类型作为 ID

```java
// ✅ 允许：直接使用 Long 作为 ID
@Entity
public class User extends AbstractAggregateRoot<User> {
    @Id
    private Long id;
}

// ✅ 也可以：使用强类型值对象（推荐用于复杂 ID 场景）
@Entity
public class Order extends AbstractAggregateRoot<Order> {
    @Id
    @Column(name = "id")
    private OrderId id;  // record OrderId(String value) implements Identity<String>
}
```

**原则总结**

cartisan-boot 的设计理念：**提供能力，不强求风格**。

- 框架提供 DDD 基础设施（`AggregateRoot`、`DomainEntity`、`ValueObject` 等）
- 是否严格遵循 DDD 风格由业务团队决定
- 代码应该简洁务实，避免为了教条增加不必要的抽象

### 4.2 JPA / 数据访问

| 规则 | 说明 |
|------|------|
| **DATA-001** | JPA `save()` 后必须用原始 entity 发布事件，而非返回值 |
| **DATA-002** | Repository 不是 Spring Bean，依赖注入用静态持有者模式 |
| **DATA-003** | `@MappedSuperclass` 需要添加 `@EntityListeners(AuditingEntityListener.class)` |
| **DATA-004** | `@SQLRestriction` 在 `@MappedSuperclass` 上可能无法正确继承，子类重复声明才保险 |
| **DATA-005** | JPQL `@Query` 查询不受 `@SQLRestriction` 影响，需手动添加软删除条件 |
| **DATA-006** | 自动软删除通过 `instanceof` 判断类型，软删除调用 `markAsDeleted()` + `save()`，非软删除实体物理删除 |
| **DATA-007** | `@EnumConvert` 用于 BaseEnum 字段，自动注册 `UniversalEnumConverter` 实现枚举与 Integer 转换 |
| **DATA-008** | BaseEnum Jackson 序列化为 code，反序列化通过 `ContextualDeserializer` 获取目标枚举类型 |
| **REL-001** | 关联表应使用单一代理主键（Long），而非 JPA 复合主键（@IdClass/@EmbeddedId） |
| **REL-002** | 关联表业务唯一性通过数据库 `@UniqueConstraint` 约束保证 |
| **REL-003** | 关联表若需实现 `DomainEntity`，必须添加独立主键字段 |

#### 关联表主键设计

**背景**：JPA 关联表（Join Table）需要实现 `DomainEntity<T, ID>` 接口，但复合主键（`@IdClass` 或 `@EmbeddedId`）与 `DomainEntity` 的单一 ID 类型设计不兼容。

**决策**：关联表应使用单一的代理主键，而非 JPA 复合主键。

```java
@Entity
@Table(name = "sys_admin_user_roles", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"admin_id", "role_id"})
})
public class AdminUserRole implements DomainEntity<AdminUserRole, Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // 代理主键

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    // 业务唯一性由数据库唯一约束保证
}
```

**理由**：

| 理由 | 说明 |
|------|------|
| **框架兼容性** | `DomainEntity<T, ID>` 的 ID 参数为单一类型，复合主键无法直接映射 |
| **JPA 支持完整** | 单一主键与 `@GeneratedValue`、软删除、审计、`BaseRepository` 等功能无缝集成 |
| **性能** | 单字段主键索引更高效，外键关联更简单 |
| **代码简洁** | 无需自定义 `sameIdentityAs`，框架默认实现即满足需求 |
| **可维护性** | 独立代理主键便于日志追踪和调试 |

**适用场景**：
- 所有使用 `@IdClass` 或 `@EmbeddedId` 的 JPA 关联表实体
- 需要实现 `DomainEntity<T, ID>` 接口的领域实体
- 业务上需要通过数据库唯一约束保证组合唯一性的场景

### 4.3 枚举增强

| 规则 | 说明 |
|------|------|
| **ENUM-001** | 业务枚举必须实现 `BaseEnum<T>` 接口，提供 `code`/`name` 映射 |
| **ENUM-002** | 实体枚举字段使用 `@EnumConvert(枚举类.class)` 注解，自动注册 JPA Converter |
| **ENUM-003** | BaseEnum 序列化为 Integer code，避免枚举顺序变化导致数据不一致 |
| **ENUM-004** | `parseByCode()` 找不到返回 null，`requireByCode()` 找不到抛异常 |

**重要设计取舍：**
- 使用 `@EnumConvert` + `UniversalEnumConverter` 而非 `@Convert` 直接注解：自动注册，业务代码更简洁
- code 值稳定：不依赖枚举 `ordinal()`，增删枚举值不影响已有数据
- Jackson 自动配置：全局生效，无需在枚举类上添加 `@JsonFormat` 或 `@JsonCreator`

### 4.4 Spring Boot / 自动配置

| 规则 | 说明 |
|------|------|
| **BOOT-001** | 使用 `JpaRepositoryFactoryEntryCustomizer` 全局配置 `repositoryBaseClass` |
| **TOOL-007** | `@Component` 默认 bean 名称可能与自动配置冲突，需显式指定如 `@Component("cartisanXxx")` |

### 4.5 分布式 ID / TSID

| 规则 | 说明 |
|------|------|
| **ID-001** | 纯随机 TSID 测试需要容忍小量重复（≤0.2%），不应要求 100% 唯一 |
| **ID-002** | 无锁随机数生成使用 `ThreadLocalRandom`，不用 `synchronized` |

### 4.6 工具配置

| 规则 | 说明 |
|------|------|
| **TOOL-005** | Testcontainers 与 Docker Engine 29 需要版本 1.21.4+ |
| **TOOL-006** | PIT 变异测试是 Phase 5 必跑门禁，杀死率 ≥ 70% |
| **TOOL-004** | `@TestConfiguration` 不能使用工具类模式（私有构造抛异常） |
| **TEST-003** | Spring Boot Test 依赖分层：`api` 暴露给业务，`implementation` 本模块使用 |

### 4.7 代码风格

| 规则 | 说明 |
|------|------|
| **STYLE-001** | 领域接口应包含完整 JavaDoc 和使用示例 |
| **STYLE-002** | JavaDoc 中必须转义 HTML 特殊字符：`<` → `&lt;`，`>` → `&gt;` |

### 4.8 测试

| 规则 | 说明 |
|------|------|
| **TEST-001** | 使用 AssertJ 而非 JUnit 断言 |
| **TEST-002** | 测试方法命名遵循 `given_{条件}_when_{操作}_then_{预期结果}` |
| **ASRT-001** | `require()` 抛 DomainException（4xx），`ensure()` 抛 IllegalStateException（500） |
| **ASRT-002** | 工具类私有构造函数应抛出异常，而非返回 null |

### 4.9 Security

#### 4.9.1 规则

| 规则 | 说明 |
|------|------|
| **SECURITY-001** | Sa-Token 包路径是 `cn.dev33.satoken`，不是 `cn.dev33.sa-token` |
| **SECURITY-002** | Sa-Token Session 类是 `SaSession`，不是 `Session` |
| **SECURITY-003** | TenantContext 使用 `ScopedValue`，先 `isBound()` 再 `get()` |
| **SECURITY-004** | MockMvc 集成测试需要测试专用 Controller，不能直接调用 `StpUtil.login()` |
| **SECURITY-005** | `@Component` Bean 名称需显式指定（如 `@Component("cartisanXxx")`）避免冲突 |
| **SECURITY-006** | `@CurrentUser Long` 未登录时调用 `StpUtil.checkLogin()` 抛异常，与 `SecurityInterceptor` 一致 |

#### 4.9.2 scope 参数管理

`@RequirePermission` 的 `scope` 参数在各 Controller 中重复出现时，推荐使用常量类管理：

```java
// 在限界上下文下定义常量类
// com.aieducenter.admin.constants.AdminScopes
public final class AdminScopes {
    public static final String ADMIN = "admin";
    public static final String USER = "user";
    public static final String COURSE = "course";
}

// Controller 中使用
@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    @RequirePermission(
        value = "admin:user:read",
        name = "平台管理 / 用户管理 / 查看",
        scope = AdminScopes.ADMIN  // 使用常量
    )
    @GetMapping
    public ApiResponse<List<User>> listUsers() { ... }
}
```

**说明：**
- 常量类放在 `{限界上下文}.constants` 包下，与 domain 平级
- 每个限界上下文可以定义自己的 scope 常量
- 避免硬编码字符串散落在各 Controller 中

### 4.10 jOOQ / Data-Query

| 规则 | 说明 |
|------|------|
| **QUERY-001** | `generateJooq` 任务必须依赖 `flywayMigrate`，确保先生成 schema 再生成代码 |
| **QUERY-002** | jOOQ 代码生成目录为 `build/generated/jooq`，需在 IDEA 中标记为 Generated Sources Root |
| **QUERY-003** | `JooqTenantSupport` 需要 `cartisan-security` 可选依赖，无租户上下文时返回 `noCondition()` |
| **QUERY-004** | jOOQ 版本由 `cartisan-dependencies` BOM 管理，业务项目无需显式指定版本 |

#### QUERY-001：代码生成任务依赖

```kotlin
// ❌ 错误：缺少任务依赖，可能生成与当前 schema 不一致的代码
tasks.named<nu.studer.jooq.GenerateJooqTask>("generateJooq") {
    // 空配置
}

// ✅ 正确：先生成 schema，再生成代码
tasks.named<nu.studer.jooq.GenerateJooqTask>("generateJooq") {
    dependsOn("flywayMigrate")
}
```

#### QUERY-002：IDEA 识别生成目录

```bash
# 方式一：通过 Gradle 同步
./gradlew cleanIdea idea

# 方式二：IDEA 中手动标记
# 右键 build/generated/jooq → Mark Directory as → Generated Sources Root
```

#### QUERY-003：JooqTenantSupport 可选依赖

```kotlin
// cartisan-data-query/build.gradle.kts
dependencies {
    // 可选依赖：运行时由使用方提供
    compileOnly(project(":cartisan-security"))
}
```

使用时需引入 security：

```kotlin
// 业务项目/build.gradle.kts
dependencies {
    implementation(project(":cartisan-data-query"))
    implementation(project(":cartisan-security"))  // 使用 JooqTenantSupport 时需要
}
```

### 4.11 AI / cartisan-ai

| 规则 | 说明 |
|------|------|
| **AI-001** | `ChatRequest.withStream()` 创建副本，避免修改原请求 |
| **AI-002** | `ModelProviderRegistry` 的 Listener 异常不中断流程，仅记录 WARN |
| **AI-003** | `SseHelper` 的 `usageCallback` 仅在流完成且有 usage 时触发 |
| **AI-004** | Provider 条件装配基于 `api-key` 配置，无 key 则不创建 Bean |

### 4.12 Web 基础设施

#### 4.12.1 规则

| 规则 | 说明 |
|------|------|
| **WEB-001** | `@PreventResubmit` 需要 Redis 环境，无 Redis 时不生效 |
| **WEB-002** | `AutoResponseAdvice` 对 String 类型特殊处理，避免二次序列化 |
| **WEB-003** | `TreeNodeBuilder` 需要 ID 类型转换，使用 Function 映射 |
| **WEB-004** | `RequestLogFilter` 自动排除 swagger、druid、actuator 路径 |
| **WEB-005** | MDC requestId 自动清理，请求结束无需手动处理 |

#### 4.12.2 Controller 返回值设计

**Controller 是否必须返回 `ApiResponse<T>`？**

否，cartisan-boot 支持自动包装。

启用 `AutoResponseAdvice` 后，Controller 可以直接返回数据：

```yaml
# application.yml
cartisan:
  web:
    auto-response:
      enabled: true
```

```java
// ✅ 启用自动包装后，可以直接返回数据
@GetMapping("/{id}")
public User getById(@PathVariable Long id) {
    return userService.findById(id);
}

// ✅ 也可以继续使用 ApiResponse（显式声明）
@GetMapping("/{id}")
public ApiResponse<User> getById(@PathVariable Long id) {
    return ApiResponse.ok(userService.findById(id));
}
```

**排除路径：** `/swagger-ui`、`/v3/api-docs`、`/actuator`

**设计取舍：**
- 提供能力，不强求风格
- 自动包装开启后，代码更简洁
- 关键接口仍可显式使用 `ApiResponse` 提高可读性

### 4.13 数据查询

| 规则 | 说明 |
|------|------|
| **QUERY-005** | `@Condition` 注解 BigDecimal 类型有类型推断限制，建议使用 Integer/Long |
| **QUERY-006** | `@Condition` 的 `blurry` 属性使用 OR 连接多字段 LIKE 查询 |
| **QUERY-007** | `@Condition` 注解 null 和空字符串自动跳过，不生成查询条件 |

---

## 五、详细功能指南

### 5.1 @Condition 注解详细说明

`@Condition` 注解用于标注查询 DTO 字段，指定查询条件类型，配合 JPA Specification 使用，避免手动编写 Predicate 构建逻辑。

#### 5.1.1 ConditionType 枚举（11 种查询类型）

**相等性比较**
| 类型 | SQL 示例 | 说明 |
|------|----------|------|
| `EQUAL` | `WHERE field = value` | 相等查询（默认） |
| `NOT_EQUAL` | `WHERE field != value` | 不相等查询 |

**大小比较**
| 类型 | SQL 示例 | 说明 |
|------|----------|------|
| `GREATER_EQUAL` | `WHERE field >= value` | 大于等于 |
| `GREATER` | `WHERE field > value` | 大于 |
| `LESS_EQUAL` | `WHERE field <= value` | 小于等于 |
| `LESS` | `WHERE field < value` | 小于 |

**模糊查询**
| 类型 | SQL 示例 | 说明 |
|------|----------|------|
| `INNER_LIKE` | `WHERE field LIKE '%value%'` | 中间模糊查询 |
| `LEFT_LIKE` | `WHERE field LIKE '%value'` | 左模糊查询 |
| `RIGHT_LIKE` | `WHERE field LIKE 'value%'` | 右模糊查询 |

**集合与区间查询**
| 类型 | SQL 示例 | 说明 |
|------|----------|------|
| `IN` | `WHERE field IN (value1, value2, ...)` | IN 查询 |
| `BETWEEN` | `WHERE field BETWEEN value1 AND value2` | 区间查询 |

#### 5.1.2 注解属性说明

```java
public @interface Condition {
    String propName() default "";           // 实体属性名
    ConditionType type() default EQUAL;     // 查询条件类型
    String blurry() default "";             // 多字段模糊搜索
}
```

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `propName` | `""` | 实体属性名，空字符串表示使用与字段名相同的名称 |
| `type` | `EQUAL` | 查询条件类型 |
| `blurry` | `""` | 多字段模糊搜索，逗号分隔，使用 OR 连接 |

#### 5.1.3 定义查询 DTO

```java
import com.cartisan.data.jpa.specification.Condition;
import com.cartisan.data.jpa.specification.ConditionType;
import java.util.List;

public record ProductQuery(
    @Condition(type = ConditionType.INNER_LIKE) String name,
    @Condition(propName = "stock", type = ConditionType.GREATER_EQUAL) Integer minStock,
    @Condition(propName = "stock", type = ConditionType.LESS_EQUAL) Integer maxStock,
    @Condition(type = ConditionType.EQUAL) String category,
    @Condition(propName = "category", type = ConditionType.IN) List<String> categories,
    @Condition(propName = "stock", type = ConditionType.BETWEEN) List<Integer> stockRange,
    @Condition(blurry = "name,category") String keyword
) {}
```

#### 5.1.4 Repository 使用

```java
public interface ProductRepository extends BaseRepository<Product, Long> {
    default List<Product> findByCondition(ProductQuery query) {
        return findAll(ConditionSpecifications.fromAnnotation(query));
    }
}
```

#### 5.1.5 嵌套属性路径

```java
public record OrderQuery(
    @Condition(propName = "user.name", type = ConditionType.INNER_LIKE)
    String userName
) {}
```

#### 5.1.6 注意事项

- **null 和空字符串自动跳过**：不会生成对应的查询条件
- **Record 类推荐**：不可变性、简洁性、构造函数校验
- **BigDecimal 类型限制**：大小比较建议使用 Integer/Long
- **IN 和 BETWEEN 查询**：IN 支持 Collection，BETWEEN 需要 2 个元素的 List

---

### 5.2 Druid 数据源

`cartisan-data-jpa` 模块支持集成 Druid 数据源，提供 SQL 监控、慢 SQL 记录、防火墙等功能。

#### 5.2.1 启用方式

在业务项目 `application.yml` 中配置：

```yaml
spring:
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    url: jdbc:postgresql://localhost:5432/mydb
    username: user
    password: pass
```

#### 5.2.2 监控页面配置

```yaml
spring:
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      stat-view-servlet:
        enabled: true
        login-username: admin
        login-password: admin
```

访问地址：`http://localhost:8080/druid/index.html`

#### 5.2.3 慢 SQL 记录配置

```yaml
spring:
  datasource:
    druid:
      filter:
        stat:
          enabled: true
          log-slow-sql: true
          slow-sql-millis: 1000
```

#### 5.2.4 SQL 防火墙配置

```yaml
spring:
  datasource:
    druid:
      filter:
        wall:
          enabled: true
          config:
            multi-statement-allow: true
```

#### 5.2.5 注意事项

- 框架默认使用 HikariCP，需显式配置 `spring.datasource.type` 才切换到 Druid
- 生产环境建议配置监控页面登录密码
- `RequestLogFilter` 已排除 `/druid/*` 路径，监控页面访问不会被记录

---

### 5.3 RedisKey 工具详细说明

统一管理 Redis Key 的前缀和过期时间。

#### 5.3.1 创建 RedisKey

```java
// 带过期时间的 Key（1 小时）
private static final RedisKey USER_CACHE_KEY = RedisKey.of("user:cache", 3600);

// 永不过期的 Key
private static final RedisKey SYSTEM_CONFIG_KEY = RedisKey.permanent("system:config");
```

#### 5.3.2 API 说明

| 方法 | 说明 |
|------|------|
| `RedisKey.of(prefix, expireSeconds)` | 创建带过期时间的 Key |
| `RedisKey.permanent(prefix)` | 创建永不过期的 Key |
| `key(suffix)` | 生成完整的 Redis Key（格式：`prefix:suffix`） |
| `expireSeconds()` | 获取过期时间（秒），0 表示永不过期 |
| `isPermanent()` | 判断是否为永久 Key |

#### 5.3.3 使用示例

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final StringRedisTemplate redisTemplate;
    private static final RedisKey USER_CACHE = RedisKey.of("user:info", 600);

    public void cacheUser(Long userId, String userData) {
        String key = USER_CACHE.key(String.valueOf(userId));
        redisTemplate.opsForValue().set(key, userData, USER_CACHE.expireSeconds(), TimeUnit.SECONDS);
    }
}
```

---

### 5.4 DomainMapper 详细说明

提供 MapStruct 批量转换的默认实现。

#### 5.4.1 定义 Mapper

```java
@Mapper(componentModel = "spring")
public interface UserMapper extends DomainMapper<User, UserResponse> {
    // convert 方法由 MapStruct 自动生成
}
```

#### 5.4.2 @Mapping 注解

```java
@Mapper
public interface UserMapper extends DomainMapper<User, UserResponse> {
    @Mapping(source = "fullName", target = "name")
    @Mapping(target = "email", ignore = true)
    UserResponse toResponse(User user);
}
```

#### 5.4.3 qualifiedByName

```java
@Mapper
public interface UserMapper extends DomainMapper<User, UserResponse> {
    @Named("nullableToEmpty")
    default String nullableToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Mapping(target = "email", qualifiedByName = "nullableToEmpty")
    UserResponse toResponse(User user);
}
```

#### 5.4.4 Lombok 集成

框架已配置 `lombok-mapstruct-binding`，支持映射 Lombok `@Builder`：

```java
@Builder
public class UserResponse {
    private Long id;
    private String name;
}
```

---

### 5.5 TreeNode 树结构详细说明

为前端树组件提供统一的树结构数据。

#### 5.5.1 TreeNode 结构

```json
{
  "id": 1,
  "name": "部门名称",
  "parentId": 0,
  "children": [{"id": 2, "name": "子部门", "parentId": 1}]
}
```

#### 5.5.2 构建树结构

```java
List<TreeNode<Long>> tree = TreeNodeBuilder.build(
    nodes,
    id -> String.valueOf(id),
    parentId -> String.valueOf(parentId),
    0L
);
```

---

### 5.6 @PreventResubmit 防重提交详细说明

防止用户在短时间内重复提交表单。

#### 5.6.1 注解属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `delaySeconds` | int | 20 | 防重提交时间窗口（秒） |
| `prefix` | String | "" | Redis key 前缀 |

#### 5.6.2 使用示例

```java
@PostMapping
@PreventResubmit(delaySeconds = 10, prefix = "createUser")
public ApiResponse<Void> createUser(@RequestBody CreateUserRequest request) {
    userService.create(request);
    return ApiResponse.ok();
}
```

---

### 5.7 Jackson 全局配置详细说明

统一 JSON 序列化/反序列化行为。

#### 5.7.1 配置项

| 配置项 | 说明 |
|--------|------|
| `Long → String` | 解决 JavaScript Long 精度问题 |
| `LocalDateTime → ISO 8601` | 标准日期时间格式 |
| `BigDecimal → 禁止科学计数法` | 保持金额精度 |
| `Enum → 字符串` | 提高可读性 |
| `忽略未知属性` | 避免字段不匹配导致失败 |

---

### 5.8 RequestLogFilter 详细说明

记录 HTTP 请求基本信息。

#### 5.8.1 排除路径

| 路径前缀 | 说明 |
|----------|------|
| `/swagger-ui` | Swagger UI 文档 |
| `/v3/api-docs` | OpenAPI 文档 |
| `/druid` | Druid 监控 |
| `/actuator` | Spring Boot Actuator |

---

### 5.9 MDC 集成详细说明

将 `requestId` 放入 MDC 便于日志追踪。

#### 5.9.1 logback 配置

```xml
<pattern>[%X{requestId}] - %msg%n</pattern>
```

#### 5.9.2 异步场景

```java
String requestId = MDC.get("requestId");
CompletableFuture.runAsync(() -> {
    MDC.put("requestId", requestId);
    log.info("异步任务也有 requestId");
    MDC.clear();
});
```

---

### 5.10 AutoResponseAdvice 详细说明

自动将 Controller 返回值包装为统一的 `ApiResponse` 格式。

#### 5.10.1 配置方式

```yaml
cartisan:
  web:
    auto-response:
      enabled: true
```

#### 5.10.2 排除路径

- `/swagger-ui`、`/v3/api-docs`、`/actuator`

---

### 5.11 用户踢出功能详细说明

强制用户下线，使其 Token 失效。

#### 5.11.1 API

```java
void kickout(Long loginId);  // 根据 loginId 踢出
void kickoutByUsername(String username);  // 需业务层实现
```

---

### 5.12 jOOQ 代码生成配置

#### 5.12.1 最小化配置

```kotlin
plugins {
    id("nu.studer.jooq") version "8.2.1"
}

jooq {
    configuration {
        generator {
            database { name = "org.jooq.meta.postgres.PostgresDatabase" }
            generate { isJavaTimeTypes = true }
            target { packageName = "com.example.db" }
        }
    }
}

tasks.named<nu.studer.jooq.GenerateJooqTask>("generateJooq") {
    dependsOn("flywayMigrate")
}
```

---

## 六、CQRS 架构说明

### 6.1 读写分离设计

| 模块 | 职责 | 技术 |
|------|------|------|
| **cartisan-data-jpa** | 写侧（Command） | JPA + Hibernate |
| **cartisan-data-query** | 读侧（Query） | jOOQ + DSL |

### 6.2 典型使用场景

```java
// 写：使用 JPA 保存聚合根
@Service
public class OrderService {
    private final OrderRepository orderRepository;  // JPA

    public void createOrder(CreateOrderRequest request) {
        Order order = new Order(request.getCustomerId(), request.getItems());
        orderRepository.save(order);  // 自动发布领域事件
    }
}

// 读：使用 jOOQ 高效查询
@Service
public class OrderQueryService {
    private final DSLContext dsl;  // jOOQ

    public Page<OrderDto> queryOrders(OrderQuery query, Pageable pageable) {
        // 类型安全的 DSL 查询
        List<OrderDto> orders = dsl.select(
                ORDER.ID,
                ORDER.CUSTOMER_ID,
                ORDER.STATUS,
                ORDER.TOTAL_AMOUNT
            )
            .from(ORDER)
            .where(buildConditions(query))
            .orderBy(OrderConstant)
            .limit(pageable.getPageSize())
            .offset(pageable.getOffset())
            .fetchInto(OrderDto.class);

        long total = dsl.fetchCount(ORDER);
        return new PageImpl<>(orders, pageable, total);
    }
}
```

---

#### TOOL-008 / SECURITY-001：Sa-Token 包路径

```java
// ❌ 错误：包路径不是 cn.dev33.sa-token
import cn.dev33.sa-token.stp.StpUtil;

// ✅ 正确：包路径是 cn.dev33.satoken
import cn.dev33.satoken.stp.StpUtil;
```

#### TOOL-009 / SECURITY-002：Sa-Token Session 类

```java
// ❌ 错误：没有 cn.dev33.satoken.session.Session
import cn.dev33.satoken.session.Session;

// ✅ 正确：Session 类是 SaSession
import cn.dev33.satoken.session.SaSession;
```

#### SECURITY-003：ScopedValue 使用方式

```java
// ❌ 错误：直接 get() 可能抛 NoSuchElementException
public static Long getCurrentTenantId() {
    return TENANT_ID.get();
}

// ✅ 正确：先检查 isBound()，再 get()
public static Long getCurrentTenantId() {
    if (!TENANT_ID.isBound()) {
        return null;
    }
    return TENANT_ID.get();
}

// ✅ 或使用 getOrDefault()
public static Long getCurrentTenantId() {
    return ScopedValue.getOrDefault(TENANT_ID, null);
}
```

#### TEST-004 / SECURITY-004：MockMvc 集成测试方式

```java
// ❌ 错误：直接调用 StpUtil.login()，Sa-Token 上下文未初始化
@Test
void test() {
    StpUtil.login(100L);
    mvc.perform(get("/api/users"))
        .andExpect(status().isOk());
}

// ✅ 正确：创建测试专用 Controller，通过 HTTP 请求触发登录
@RestController
@RequestMapping("/test/auth")
class TestAuthController {
    @PostMapping("/login")
    public ApiResponse<Void> login(@RequestParam Long userId) {
        StpUtil.login(userId);
        return ApiResponse.ok();
    }
}

@Test
void test() throws Exception {
    mvc.perform(post("/test/auth/login?userId=100"))
        .andExpect(status().isOk());
    // 现在 Sa-Token 上下文已正确初始化
}
```

---

## 七、依赖说明

### 5.1 cartisan-core

```
零外部依赖，仅使用 JDK 标准库
```

### 5.2 cartisan-test

```
api 依赖：
- JUnit 5
- AssertJ
- Mockito
- ArchUnit
- Spring Boot Test
- Testcontainers

implementation 依赖：
- Spring Test
- Spring Boot Starter Data Redis
```

### 5.3 cartisan-web

```
api 依赖：
- cartisan-core

implementation 依赖：
- Spring Boot Starter Web
- Spring Boot Starter Validation
```

### 5.4 cartisan-data-jpa

```
api 依赖：
- cartisan-core

implementation 依赖：
- Spring Boot Starter Data JPA
- Hibernate Core（传递）

compileOnly 依赖：
- Druid Spring Boot 3 Starter 1.2.23（可选，业务需显式配置才生效）
```

### 5.5 cartisan-event

```
api 依赖：
- cartisan-core

implementation 依赖：
- Spring Context
- Spring Boot AutoConfigure
```

### 5.6 cartisan-security

```
api 依赖：
- cartisan-core
- cartisan-web

implementation 依赖：
- Sa-Token 1.45.0（sa-token-spring-boot3-starter）
```

### 5.7 cartisan-data-query

```
api 依赖：
- cartisan-web

implementation 依赖：
- jOOQ 3.19.29

compileOnly 依赖：
- cartisan-security（可选，用于 JooqTenantSupport）
```

### 5.8 cartisan-ai

```
api 依赖：
- cartisan-core
- spring-webflux（Flux 类型出现在公开 SPI 中）

implementation 依赖：
- spring-boot-starter

可选依赖（由使用方提供）：
- spring-boot-starter-web（SseEmitter 需要）
- spring-boot-starter-webflux（WebClient 需要）
```

---

## 八、参考文档

### 6.1 设计文档

- [cartisan-boot-设计文档.md](../cartisan-boot-设计文档.md)

### 6.2 开发指南

- [AI协作开发SOP.md](../sop/AI协作开发SOP.md)
- [团队踩坑经验库 (PITFALLS.md)](../PITFALLS.md)

> **说明**：MapStruct、jOOQ、@Condition、Web 基础设施、可选功能等详细使用说明已整合到本文档"五、详细功能指南"章节。

---

**文档结束** | 更新日期：2026-03-29
