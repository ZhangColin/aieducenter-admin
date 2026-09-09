## 背景

在 aieducenter-admin（[issue #53](https://github.com/ZhangColin/aieducenter-admin/issues/53)，账号管理：管理详情 + 状态操作 封号/解封/解锁）对接 identity 后台管理签名 API 时，发现 `cartisan-openapi` 的 `OpenApiClient` 无法消费 **HTTP 204 No Content（空 body）** 的下游响应。

identity（[issue #66](https://github.com/ZhangColin/aieducenter-identity/issues/66)）的三个管理写端点 `POST /api/account/{userId}/disable` / `activate` / `unlock` 返回 `ResponseEntity.noContent().build()`（raw 204、空 body），由 identity 测试 `status().isNoContent()` 钉死契约。

## 问题：`OpenApiClient` 对空 body 无条件反序列化 → 抛异常

`OpenApiClient` 的 `sendWithBody`（`post`/`put`）与 `get` 在 `validateResponse` 之后**无条件**执行：

```java
HttpResponse<String> response = httpClient.send(...);
validateResponse(response);                      // 仅拦 >= 400
return objectMapper.readValue(response.body(), responseType);   // ← 空 body 在此抛
```

204 的响应 body 经 `BodyHandlers.ofString()` 为空串 `""`，而 Jackson `objectMapper.readValue("", TypeReference)` 抛 `MismatchedInputException: No content to map due to end-of-input`（已实测确认，Spring Boot 默认 `FAIL_ON_UNKNOWN_PROPERTIES=false` 也救不了——这是 parser 级、发生在任何 deserializer 之前）。

该异常在 `sendWithBody` 的 `catch (Exception e)` 里被包成 `RuntimeException("OpenApiClient POST failed: ...")` 重抛。消费方（admin `AccountClient`）只拿到 `OpenApiClient.post(url, body, typeref)` 这一签名，**内部反序列化无法绕过**——没有纯消费侧的解决办法。

### 为什么 app-registry 的 `VOID_TYPEREF` 没踩到

aieducenter-admin 的 `AppRegistryClient` 用 `TypeReference<ApiResponse<Void>>` 消费"void"端点看似没事，是因为 app-registry 的 disable/enable 端点实际返回 `ApiResponse<AppResponse>`（**200 + 信封**，非 204）。identity 才是 admin 对接的第一个**真正返回 raw 204** 的下游。

## 是否阻塞下游

**阻塞。** aieducenter-admin #53 的三个写端点（disable/activate/unlock，identity 全返 204）无法用现有 `OpenApiClient` 实现——一调就抛。admin 不能改本仓库以外的代码，只能在此提 issue 等框架修。

## 建议方案（择一或组合）

1. **（推荐）`OpenApiClient` 容忍空 body**：`response.body()` 为 `null`/blank 时跳过反序列化、返回 `null`。向后兼容——现有带 body 的调用（200 + 信封）行为完全不变；只有原本会抛的空 body 场景从「抛异常」变为「返回 null」。消费方对 204 用 `TypeReference<ApiResponse<Void>>`（或 `Void`）、忽略返回值即可。

   ```java
   private <T> T readBody(HttpResponse<String> response, TypeReference<T> responseType) {
       String body = response.body();
       if (body == null || body.isBlank()) {
           return null;
       }
       return objectMapper.readValue(body, responseType);
   }
   ```
   （`sendWithBody` / `get` 各把 `objectMapper.readValue(response.body(), responseType)` 换成 `readBody(response, responseType)`，仍在原 try 块内、Jackson 异常的包装语义不变。）

2. **加 `postForVoid` / `getForVoid` 显式 void 变体**：语义更明确（签名上就声明无返回体），但 API 面扩大；可与方案 1 共存。

### 建议补的框架测试

镜像 `OpenApiClientPutTest`（用 `com.sun.net.httpserver.HttpServer` 起本地服务）加一例：本地服务回 `204` + 空 body，断言 `client.post(...)` 返回 `null` 且不抛。

## 关联

- 触发上下文：aieducenter-admin [issue #53](https://github.com/ZhangColin/aieducenter-admin/issues/53)（账号管理状态操作）
- 下游契约：identity [issue #66](https://github.com/ZhangColin/aieducenter-identity/issues/66)（后台管理签名 API，#67 详情 / #68 封号 / #69 解封·解锁·踢人，端点均返 204）
