# 团队规则库 (SKILL)

> 定位：团队的"经验手册"——记录所有值得沉淀的踩坑经验和铁律。AI 在开发前会读取它，避免重复犯错。
>
> cartisan-boot 框架层的规则见：[cartisan-boot/docs/skills/SKILL.md](../../cartisan-boot/docs/skills/SKILL.md)

---

## DDD / 领域建模

### 规则 DDD-001：Entity.sameIdentityAs() 需要类型检查

**问题**：由于 Java 泛型类型擦除，接口方法签名中的 `T` 在运行时被擦除为 `Object`，无法直接调用 `other.getId()`。

**正确做法**：
```java
@SuppressWarnings("unchecked")
default boolean sameIdentityAs(T other) {
    if (other == null) return false;
    if (this.getClass() != other.getClass()) return false;
    ID otherId = ((Entity<T, ID>) other).getId();
    return Objects.equals(this.getId(), otherId);
}
```

**记忆口诀**：接口泛型方法里调泛型参数的方法？必须先检查类型再强转。

---

### 规则 DDD-002：ValueObject 的 sameValueAs 可直接委托 equals

**原因**：`equals()` 方法接受 `Object` 类型，不需要处理类型擦除问题。

```java
public interface ValueObject<T> {
    default boolean sameValueAs(T other) {
        if (other == null) return false;
        return this.equals(other);  // ✅ 简洁
    }
}
```

---

### 规则 DDD-003：领域事件应自动生成元数据

**推荐做法**：
- `eventId`：自动生成 UUID
- `occurredAt`：自动设置为 `Instant.now()`
- `aggregateId`：由子类提供并验证非空

```java
public abstract class DomainEvent {
    private final String eventId;
    private final Instant occurredAt;
    private final String aggregateId;

    protected DomainEvent(String aggregateId) {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = Instant.now();
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
    }
}
```

---

## 代码风格

### 规则 STYLE-001：领域接口应包含完整 JavaDoc 和使用示例

**要求**：
- 每个公共接口/类必须有类级别 JavaDoc
- 包含功能描述、使用场景、示例代码
- 示例代码应完整可运行

**模板**：
```java
/**
 * [一句话描述]。
 *
 * <p>[详细描述]。</p>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 完整可运行的示例
 * }</pre>
 *
 * @since 0.1.0
 */
```

---

### 规则 STYLE-002：JavaDoc 中必须转义 HTML 特殊字符

**问题**：JavaDoc 解析器会将 `<` 和 `>` 解析为 HTML 标签，导致编译失败。

**正确做法**：
```java
/**
 * 分页查询参数。
 * <p>参数自动校验：
 *   <li>page &lt; 1 时修正为 1</li>
 *   <li>size &gt; 100 时修正为 100</li>
 * </p>
 */
```

**常用 HTML 实体**：
| 字符 | 实体 |
|------|------|
| `<` | `&lt;` |
| `>` | `&gt;` |
| `&` | `&amp;` |

---

### 规则 STYLE-003：使用 Record 实现 ValueObject 和 Identity

```java
// ✅ ValueObject
public record Money(BigDecimal amount, String currency) implements ValueObject<Money> {
    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}

// ✅ Identity
public record TenantId(Long value) implements Identity<Long> {
    public TenantId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Tenant ID must be positive");
        }
    }
}
```

---

## 测试

### 规则 TEST-001：使用 AssertJ 而非 JUnit 断言

```java
import static org.assertj.core.api.Assertions.assertThat;

// ✅ AssertJ：链式调用，语义清晰
assertThat(actual).isEqualTo(expected);
assertThat(list).hasSize(3).containsExactly("a", "b", "c");

// ❌ JUnit：不支持链式调用
assertEquals(expected, actual);
```

---

### 规则 TEST-002：测试方法命名使用 should_when 模式

**模板**：
```java
@Test
void should_{预期结果}_when_{条件}() {
    // When
    ...

    // Then
    ...
}
```

**示例**：
```java
@Test
void shouldThrowInsufficientBalanceException_whenBalanceInsufficient() {
    // When & Then
    assertThatThrownBy(() -> chatService.chat(tenantId, request))
        .isInstanceOf(InsufficientBalanceException.class);
}
```

**命名规范**：
- 必须使用 `should_when` 格式，不使用 `given_when_then`
- `should` 后面描述预期结果
- `when` 后面描述触发条件
- 测试方法内部仍推荐使用 Given/When/Then 注释分块

---

### 规则 TEST-003：MockMvc 集成测试需要测试专用 Controller

**问题**：直接在测试中调用 `StpUtil.login()` 后使用 MockMvc，Sa-Token 上下文未初始化。

**正确做法**：创建测试专用 Controller，通过 HTTP 请求触发登录
```java
// ✅ 通过 MockMvc 请求登录，Sa-Token 上下文正确初始化
@RestController
@RequestMapping("/test/auth")
public class TestAuthController {
    @GetMapping("/login/{userId}")
    public ApiResponse<Map<String, String>> login(@PathVariable Long userId) {
        StpUtil.login(userId);
        String token = StpUtil.getTokenValue();
        return ApiResponse.ok(Map.of("token", token));
    }
}

// 测试中先登录获取 token
String token = extractToken(mvc.perform(get("/test/auth/login/100"))
    .andReturn()
    .getResponse()
    .getContentAsString());

// 使用 token 访问受保护端点
mvc.perform(get("/api/conversations").header("satoken", token))
    .andExpect(status().isOk());
```

---

## Spring Data JPA / 数据访问

### 规则 DATA-001：JPA save() 后必须用原始 entity 发布事件

**问题**：`SimpleJpaRepository.save()` 返回的可能是一个新实例，不是原始传入的实体。

```java
@Override
public <S extends T> S save(S entity) {
    S savedEntity = super.save(entity);
    publishDomainEvents(entity);  // ✅ 使用原始 entity
    return savedEntity;
}
```

**记忆口诀**：JPA save 返回值 ≠ 原始参数，后处理必须用原参数。

---

### 规则 DATA-002：@MappedSuperclass 需要添加 @EntityListeners

**问题**：审计字段没有被自动填充。

**正确做法**：
```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)  // ✅ 必须添加
public abstract class Auditable {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

---

### 规则 DATA-003：JPQL @Query 查询不受 @SQLRestriction 影响

**问题**：使用 `@Query` 编写 JPQL 时，软删除过滤不生效。

```java
// ✅ JPQL 查询手动添加软删除条件
@Query("SELECT c FROM Conversation c WHERE c.deleted = false AND c.tenantId = :tenantId")
List<Conversation> findActiveByTenantId(@Param("tenantId") Long tenantId);
```

**记忆口诀**：JPQL 查询手动加条件，@SQLRestriction 只管自动生成的 SQL。

---

## Spring Boot / Web

### 规则 WEB-000：ApiResponse.ok() 返回的 message 格式

**问题**：测试中断言期望 `message = "success"`，但实际返回 `"Success"`（首字母大写）。

**正确做法**：
```java
// ✅ 正确：断言首字母大写
assertThat(response.message()).isEqualTo("Success");

// ❌ 错误：期望全小写
assertThat(response.message()).isEqualTo("success");
```

**记忆口诀**：ApiResponse.ok() 的 message 是 "Success"，首字母大写。

**相关**：cartisan-boot 的 ApiResponse 实现

---

### 规则 WEB-001：Actuator 与全局异常处理器的交互

**场景**：访问未暴露的 Actuator 端点时，期望返回 404，但实际返回 500。

**原因**：cartisan-web 的 `@ControllerAdvice` 全局异常处理器捕获了 Spring 抛出的 `HandlerNotFoundException`，转换为 ApiResponse 格式的 500 响应。

**影响**：不影响核心功能（端点确实未暴露，通过 `/actuator` 可验证），但响应格式与纯 Actuator 环境不同。

**预期行为**：
```bash
# 访问未暴露的端点
curl http://localhost:8080/actuator/info
# 返回: {"code":500,"message":"Internal server error",...}
# 而非标准的 404
```

**后续优化（可选）**：在 cartisan-web 的 GlobalExceptionHandler 中排除 `/actuator/**` 路径：
```java
@ExceptionHandler(HandlerNotFoundException.class)
public ResponseEntity<ApiResponse<Void>> handleNotFound(HandlerNotFoundException e, HttpServletRequest request) {
    // 排除 Actuator 路径，让 Spring 返回标准 404
    if (request.getRequestURI().startsWith("/actuator/")) {
        throw e;  // 重新抛出，让 Spring 处理
    }
    return ApiResponse.error(404, "Not Found");
}
```

---

### 规则 WEB-002：@Validated 必须放在类上触发 @RequestParam 校验

```java
// ✅ 添加 @Validated
@Validated  // 必须有
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    @GetMapping("/search")
    public void search(@RequestParam @Min(1) Integer page) {
        // Spring 会先校验，校验失败抛出 ConstraintViolationException
    }
}
```

---

### 规则 WEB-003：@Component 默认 bean 名称可能与自动配置冲突

**问题**：`@Component` 默认 bean 名称可能与 Spring Boot 自动配置冲突。

```java
// ✅ 显式指定 bean 名称，避免与 Spring Boot 自动配置冲突
@Component("aieducenterRequestContextFilter")
public class RequestContextFilter extends OncePerRequestFilter {
    // ...
}
```

**命名建议**：使用项目前缀，如 `aieducenter{ClassName}` 或 `{context}{ClassName}`。

---

## 断言工具 (Assertions)

### 规则 ASRT-001：异常类型语义决定 HTTP 状态码

| 断言方法 | 异常类型 | 语义 | HTTP |
|---------|---------|------|------|
| `require()` | `DomainException` | 调用者责任 = 业务规则违反 | 4xx |
| `ensure()` | `IllegalStateException` | 实现者责任 = 代码 bug | 500 |
| `requirePresent()` | `DomainException` | 资源不存在 | 404 |

```java
// ✅ 业务规则违反
public void deductCoins(int amount) {
    require(this.balance >= amount,
        new InsufficientBalanceException("余额不足"));
}

// ✅ 代码 bug
public void addItem(OrderItem item) {
    this.items.add(item);
    ensure(this.items.contains(item), "item should be present after add");
}
```

---

## 计费相关

### 规则 BILL-001：金额计算用 BigDecimal，禁止 double

```java
// ✅ 正确
public record Money(BigDecimal amount, String currency) {
    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount), "CNY");
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }
}

// ❌ 错误：精度丢失
public double add(double a, double b) {
    return a + b;  // 0.1 + 0.2 可能 ≠ 0.3
}
```

---

### 规则 BILL-002：虚拟币预扣需校验余额和配额

**计费流程**：
```java
// 1. 预扣
CoinDeduction deduction = coinService.preDeduct(tenantId, userId, estimatedCoins);

// 2. 调用 AI
try {
    ChatResponse response = aiGateway.chat(request);

    // 3. 实扣（多退少补）
    coinService.actualDeduct(deduction.getId(), response.actualTokens());
} catch (Exception e) {
    // 4. 失败回滚
    coinService.rollbackDeduction(deduction.getId());
    throw e;
}
```

---

## 多租户相关

### 规则 TENANT-001：租户上下文用 ScopedValue 传递

```java
// 使用 cartisan-security 的 TenantContext
Long tenantId = TenantContext.currentTenantId();

// 在 Filter 中设置
TenantContext.set(tenantId);
try {
    chain.doFilter(request, response);
} finally {
    TenantContext.clear();
}
```

---

### 规则 TENANT-002：所有数据查询必须带租户过滤

```java
// ✅ Repository 层自动过滤
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    @Query("SELECT c FROM Conversation c WHERE c.tenantId = :tenantId AND c.deleted = false")
    List<Conversation> findByTenantId(@Param("tenantId") Long tenantId);
}

// ✅ 也可以用 @SQLRestriction
@Entity(name = "conversation")
@SQLRestriction("tenant_id = CURRENT_TENANT_ID() AND deleted = false")
public class Conversation { ... }
```

---

## AI 调用相关

### 规则 AI-001：流式输出需正确处理 SSE 断开

```java
// ✅ 正确处理客户端断开
public Flux<String> chatStream(ChatRequest request) {
    return aiProvider.chatStream(request)
        .doOnCancel(() -> {
            // 记录取消，可能需要部分计费
            log.info("Client disconnected during chat");
        })
        .doOnError(e -> {
            // 记录错误，回滚预扣
            log.error("Chat error", e);
        })
        .doOnComplete(() -> {
            // 流结束，触发实扣
            coinService.actualDeduct(deductionId, actualTokens);
        });
}
```

---

## 前端 / Monorepo

### 规则 FRONT-000：前后端联调用 Next.js rewrites 代理，避免 CORS

**问题**：前端直接访问后端（如 `http://localhost:8080`）会被浏览器 CORS 策略阻止。

**正确做法**：在 Next.js 中配置 rewrites 代理 `/api/*` 请求到后端。
```ts
// next.config.ts
export async function rewrites() {
  const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080'
  return [
    {
      source: '/api/:path*',
      destination: `${backendUrl}/api/:path*`,
    },
  ]
}
```

**前端调用**：使用相对路径
```ts
// ✅ 正确：通过代理访问
const response = await fetch('/api/health')

// ❌ 错误：直接跨域访问（CORS 阻止）
const response = await fetch('http://localhost:8080/api/health')
```

**生产环境**：通过环境变量配置后端地址
```bash
BACKEND_URL=https://api.example.com pnpm build
```

**记忆口诀**：前端调后端？rewrites 代理跑。

---

### 规则 FRONT-001：Next.js 配置 transpilePackages 转译 workspace 包

**问题**：从 workspace 包导入源码（.ts）时，Next.js 默认不会转译，导致运行时错误。

**正确做法**：
```ts
// next.config.ts
const nextConfig: NextConfig = {
  transpilePackages: ['@aieducenter/ui', '@aieducenter/api-client', '@aieducenter/shared'],
}
```

**记忆口诀**：workspace 包导入源码？transpilePackages 必配。

---

### 规则 FRONT-002：TypeScript jsx 配置用 preserve

**问题**：Next.js 15 + React 19 应使用 `jsx: "preserve"`，而非 `react`。

**正确做法**：
```json
{
  "compilerOptions": {
    "jsx": "preserve"  // 让 Next.js 处理 JSX
  }
}
```

---

### 规则 FRONT-003：isolatedModules 必须启用

**问题**：Next.js 要求 `isolatedModules: true`，否则可能构建失败。

**正确做法**：
```json
{
  "compilerOptions": {
    "isolatedModules": true  // tsconfig.base.json
  }
}
```

---

### 规则 FRONT-004：workspace:* 协议用于开发期本地引用

**正确做法**：
```json
{
  "dependencies": {
    "@aieducenter/ui": "workspace:*"  // 开发期自动链接
  }
}
```

**注意**：发布时 pnpm 自动替换为实际版本号。

---

### 规则 FRONT-005：共享包空壳占位，延迟引入依赖

**原则**：F01-04 仅创建包结构，shadcn/ui 等 React peerDependencies 留到 F01-06 添加。

```json
// F01-04 阶段
{
  "name": "@aieducenter/ui",
  "exports": { ".": "./src/index.ts" }
  // ❌ 不添加 React peerDependencies

// F01-06 阶段
{
  "peerDependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0"
  }
}
```

---

## 踩坑记录

### PIT-001 (2026-03-15)：MessageFormat 参数不足时不会抛异常

**场景**：`MessageFormat.format("Error {0} at {1}", "onlyOne")` 不会抛异常，而是保留未替换的占位符。

**实际行为**：返回 `"Error onlyOne at {1}"`。

---

### PIT-002 (2026-03-15)：@Retention(RUNTIME) 是注解可被反射读取的前提

**正确做法**：
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)  // 必须有
public @interface Aggregate {
}
```

---

### PIT-003 (2026-03-15)：枚举完整性测试应使用精确匹配

```java
// ✅ 精确匹配，防止新增值
@Test
void tenantRole_shouldHaveExactlyFourValues() {
    assertThat(TenantRole.values())
        .hasSize(4)
        .containsExactlyInAnyOrder(
            TenantRole.OWNER,
            TenantRole.ADMIN,
            TenantRole.MEMBER,
            TenantRole.GUEST
        );
}
```

---

### PIT-004 (2026-03-15)：BigDecimal 构造不要用 double

```java
// ❌ 错误：0.1 无法精确表示
new BigDecimal(0.1)  // 实际是 0.1000000000000000055511151231257827021181583404541015625

// ✅ 正确：先转字符串
new BigDecimal("0.1")  // 或 BigDecimal.valueOf(0.1)
```

---

### 规则 FRONT-006：packages/ui 的 tsconfig.json 需配置 jsx: react-jsx

**问题**：React 组件（.tsx）编译报错 `Cannot use JSX unless the '--jsx' flag is provided`。

**正确做法**：
```json
{
  "compilerOptions": {
    "jsx": "react-jsx"  // 或 "preserve"，但不能省略
  }
}
```

**记忆口诀**：UI 包用 JSX？jsx 配置必须有。

---

### 规则 FRONT-007：shadcn add 组件导入路径需手动修复

**问题**：shadcn add 生成的组件使用 `import { cn } from "@/lib"`，但 `@/lib` 是 packages/ui 的路径别名，在组件文件中解析为 `@/components/.../lib`。

**正确做法**：运行 shadcn add 后，批量替换导入路径：
```bash
# 在 packages/ui/src/components 目录下
sed -i '' 's|from "@/lib"|from "../lib/utils"|g' *.tsx
```

**记忆口诀**：shadcn 组件导入？执行后先修路径。

---

### 规则 FRONT-008：packages/ui 必须导出 CSS 文件

**问题**：web/admin 无法 `import '@aieducenter/ui/src/index.css'`，报错 `Package path ./src/index.css is not exported`。

**正确做法**：
```json
{
  "name": "@aieducenter/ui",
  "exports": {
    ".": "./src/index.ts",
    "./src/index.css": "./src/index.css"  // 必须显式导出
  }
}
```

**记忆口诀**：共享包导 CSS？exports 字段要配。

---

### 规则 FRONT-009：应用层 tsconfig.json 需配置 baseUrl 和 paths

**问题**：`import { ThemeToggle } from '@/components/theme-toggle'` 报错 `Cannot find module`。

**正确做法**：
```json
{
  "extends": "../tsconfig.base.json",
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  }
}
```

**记忆口诀**：@ 别名找不到？baseUrl + paths 配置好。

---

### 规则 FRONT-010：html 标签需 suppressHydrationWarning 属性

**问题**：使用 next-themes 时控制台警告 `Text content did not match`。

**正确做法**：
```tsx
<html lang="zh-CN" suppressHydrationWarning>
  {/* next-themes 需要这个属性避免 hydration 不匹配警告 */}
</html>
```

**记忆口诀**：next-themes 主题切换？suppressHydrationWarning 要加。

---

### 规则 FRONT-011：Next.js 应用需有独立的 Tailwind 配置

**问题**：从 workspace 包导入 CSS 时，Tailwind `@tailwind` 指令未被处理，导致样式不生效。

**原因**：Tailwind 的 `content` 配置只包含 `packages/ui/src`，不包含 `web/src` 的页面文件。

**正确做法**：
1. 在 `web/src/app/globals.css` 中添加 `@tailwind` 指令和 CSS 变量
2. 在 `web/tailwind.config.ts` 中配置 `content` 包含两个目录：
```ts
content: [
  './src/**/*.{ts,tsx}',
  '../../packages/ui/src/**/*.{ts,tsx}',
],
```
3. 创建 `web/postcss.config.mjs`（注意是 `.mjs` 格式）：
```js
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
```
4. 在 `web/src/app/layout.tsx` 中导入本地的 `./globals.css`

**记忆口诀**：Tailwind 不生效？查 content 配置和 postcss.config.mjs。

---

### 规则 FRONT-012：darkMode 配置用字符串而非数组

**问题**：`darkMode: ['class']` 导致 TypeScript 类型错误。

**正确做法**：
```ts
darkMode: 'class',  // 字符串，不是数组
```

**记忆口诀**：darkMode 配置？字符串形式。

---

### 规则 FRONT-013：Tailwind CSS 需固定版本 v3

**问题**：`tailwindcss-animate` 会安装 Tailwind CSS v4 作为依赖，导致 PostCSS 配置不兼容。

**正确做法**：
```json
{
  "devDependencies": {
    "tailwindcss": "3.4.19",  // 固定版本，不使用 ^
    "tailwindcss-animate": "^1.0.7"
  }
}
```

**原因**：Tailwind CSS v4 的 PostCSS 插件已移至 `@tailwindcss/postcss`，语法完全不同。shadcn/ui 组件期望 v3 的 `hsl(var(--...))` 语法。

**记忆口诀**：shadcn/ui 组件？Tailwind CSS 锁 v3。


---

### 规则 FRONT-014：Next.js 15 + ESLint 10 需配置 .eslintrc.json

**问题**：`next lint` 在没有 ESLint 配置时会进入交互式选择模式，导致脚本执行失败。

**正确做法**：
```json
// web/.eslintrc.json 和 admin/.eslintrc.json
{
  "extends": ["next/core-web-vitals"]
}
```

**安装依赖**：
```bash
# web 和 admin 分别执行
pnpm add -D eslint eslint-config-next
```

**注意**：ESLint 10 配置格式有变化，部分 Next.js 15 选项不再支持（如 useEslintrc、extensions 等）。如果 lint 失败但不影响构建，可暂时跳过。

**记忆口诀**：Next.js 跑 lint？先装 ESLint 再配 eslintrc。

---

## CI/CD / 部署

### 规则 CI-001：ESLint 10 配置变化导致 next lint 失败

**问题**：ESLint 10 移除了部分配置选项，导致 `next lint` 报错：
```
- Unknown options: useEslintrc, extensions, resolvePluginsRelativeTo, rulePaths, ignorePath, reportUnusedDisableDirectives
- 'extensions' has been removed.
- 'resolvePluginsRelativeTo' has been removed.
```

**临时方案**：在验证脚本中让 lint 失败只警告、不阻止流程：
```bash
# scripts/validate-frontend.sh
echo "  → Lint 检查（失败仅警告）..."
pnpm -r lint || echo "  ⚠️  Lint 检查失败，但继续执行"
```

**长期方案**：等待 Next.js 15 更新以支持 ESLint 10，或降级到 ESLint 9。

**相关 Feature**：F01-09

---

### 规则 CI-002：Docker 构建需先发布 cartisan-boot 到本地 Maven

**问题**：Dockerfile 构建时需要 cartisan-boot 依赖，但容器内无法访问宿主 workspace。

**正确做法**：
```dockerfile
# 方案 A：在构建前先发布到本地 Maven
RUN cd cartisan-boot && ./gradlew publishToMavenLocal

# 方案 B：使用 volume 映射本地 Maven 仓库（未实施）
```

**注意**：部署脚本 `build-backend.sh` 已包含发布步骤。

**相关 Feature**：F01-09

---

## 前端测试

### 规则 FTEST-001：前端单元测试使用 vitest + happy-dom

**环境配置**：
```ts
// vitest.config.ts
export default defineConfig({
  test: {
    environment: 'happy-dom',
    globals: true,
  },
})
```

**理由**：
- vitest 与 Vite 生态无缝集成，配置简单
- happy-dom 比 JSDOM 更快，API 更接近浏览器
- ESM 原生支持

**相关 Feature**：F01-07

---

### 规则 FTEST-002：测试中间件需要导出供测试访问

**问题**：openapi-fetch 的中间件无法通过 api 实例直接访问。

**正确做法**：
```ts
// 导出中间件供测试使用
export const __middlewares__ = [authMiddleware, refreshMiddleware]

// 测试中
const authMiddleware = __middlewares__[0] as AuthMiddleware
```

**相关 Feature**：F01-07

---

### 规则 FTEST-003：动态 import 的 mock 需要创建外部函数

**问题**：vi.mock 中直接 mock 函数会导致每次调用返回新实例。

**正确做法**：
```ts
// 创建外部 mock 函数
const mockRefreshAccessTokenOnce = vi.fn()

// 模块使用外部函数
vi.mock('../auth/refresh', () => ({
  refreshAccessTokenOnce: () => mockRefreshAccessTokenOnce(),
  REFRESH_ENDPOINT: '/api/v1/auth/refresh'
}))

// 测试中配置 mock
mockRefreshAccessTokenOnce.mockResolvedValueOnce(false)
```

**相关 Feature**：F01-07

---

## 前端状态管理

### 规则 STATE-001：Zustand store 分离 set 和 clear 方法

**问题**：`setAccessToken(token: string | null)` 允许 null，但意图不清晰。

**正确做法**：
```ts
export interface AuthState {
  accessToken: string | null
  setAccessToken: (token: string) => void    // 只接受非空
  clearAccessToken: () => void              // 明确清除语义
}
```

**理由**：
- 意图明确：设置和清除是两个不同的语义
- 类型安全：setAccessToken 只接受非空字符串
- 便于扩展：clearAccessToken 可扩展清除更多状态

**相关 Feature**：F01-07

---

## API 客户端

### 规则 API-001：常量导出避免硬编码

**问题**：端点路径在多处硬编码，修改时容易遗漏。

**正确做法**：
```ts
// refresh.ts
export const REFRESH_ENDPOINT = '/api/v1/auth/refresh'

// client.ts
import { REFRESH_ENDPOINT } from '../auth/refresh'
```

**相关 Feature**：F01-07

---

### 规则 API-002：catch 块应记录错误上下文

**问题**：静默返回 false 导致生产环境无法调试。

**正确做法**：
```ts
try {
  // ...
} catch (error) {
  console.error('[refreshAccessTokenOnce] Token refresh failed:', error)
  return false
}
```

**相关 Feature**：F01-07

---

### 规则 DDD-004：值对象校验使用 compact constructor

**推荐做法**：使用 Java Record 的 compact constructor 在构造时完成校验。

```java
// ✅ 正确：compact constructor 自动拦截构造过程
public record Username(String value) implements ValueObject<String> {
    private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{2,19}$");

    public Username {
        Assertions.require(value != null && PATTERN.matcher(value).matches(),
            UserError.USERNAME_INVALID);
    }

    public boolean sameValueAs(Username other) {
        return other != null && this.value.equals(other.value);
    }
}
```

**理由**：
- Record 天然不可变，符合值对象语义
- compact constructor 语法简洁，校验逻辑集中
- 无需编写显式构造函数
- 实现 `ValueObject<T>` 接口提供 `sameValueAs()` 方法

**相关 Feature**：F02-01

---

### 规则 DDD-005：密码加密使用 BCryptPasswordEncoder

**推荐做法**：
```java
// ✅ 正确：使用 Spring Security 的 BCryptPasswordEncoder
private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(10);

public User(String username, String plainPassword, String nickname, String avatar) {
    this.username = new Username(username);
    this.password = PASSWORD_ENCODER.encode(plainPassword);  // 自动加盐
    // ...
}

public boolean matchesPassword(String plainPassword) {
    return PASSWORD_ENCODER.matches(plainPassword, this.password);
}
```

**理由**：
- BCrypt 是行业标准，抗彩虹表攻击
- 自动生成盐值，无需手动管理
- strength=10 平衡安全性和性能（约 100ms）

**注意**：密码字段不在数据库设 UNIQUE 约束，允许不同用户使用相同密码。

**相关 Feature**：F02-01

---

### 规则 DDD-006：聚合根 ID 生成使用 @PrePersist

**推荐做法**：
```java
@Entity
public class User extends SoftDeletable implements AggregateRoot<User> {
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @PrePersist
    void prePersist() {
        if (this.id == null) {
            this.id = TsidGenerator.newInstance().generate();
        }
    }
}
```

**理由**：
- JPA 保存前自动触发，确保 ID 在持久化前生成
- 避免在构造函数中生成 ID（创建时无需 ID）
- 支持领域模型的"创建后才有 ID"语义

**相关 Feature**：F02-01

---

### 规则 DATA-004：Repository 查询手动添加软删除条件

**问题**：`@SQLRestriction` 只对自动生成的 SQL 生效，`@Query` 编写的 JPQL 不受影响。

**正确做法**：
```java
// ✅ JPQL 查询手动添加 deleted = false
@Query("SELECT u FROM User u WHERE u.username = :username AND u.deleted = false")
Optional<User> findByUsername(@Param("username") String username);
```

**记忆口诀**：JPQL 查询手动加条件，@SQLRestriction 只管自动 SQL。

**相关 Feature**：F02-01

---

## 踩坑记录 - F02-01

### PIT-005 (2026-03-18)：集成测试依赖 Docker，CI 需配置

**问题**：`SpringDataJpaUserRepositoryTest` 继承 `IntegrationTestBase`，依赖 Testcontainers + Docker。
无 Docker 环境时测试失败。

**解决方案**：
- 本地开发：确保 Docker 运行
- CI 环境：配置 Docker runner

**相关 Feature**：F02-01
