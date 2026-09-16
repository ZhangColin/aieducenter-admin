package com.aieducenter.admin.aiplatform.infrastructure;

import com.aieducenter.admin.aiplatform.application.AiplatformAccountAppService;
import com.aieducenter.admin.aiplatform.application.AiplatformCostAppService;
import com.aieducenter.admin.aiplatform.application.AiplatformMaterialAppService;
import com.aieducenter.admin.aiplatform.application.AiplatformOrderAppService;
import com.aieducenter.admin.aiplatform.application.AiplatformPriceEntryAppService;
import com.aieducenter.admin.aiplatform.application.AiplatformProjectAppService;
import com.aieducenter.admin.aiplatform.application.AiplatformWorkspaceAppService;
import com.cartisan.openapi.client.BinaryResponse;
import com.cartisan.openapi.client.OpenApiClient;
import com.cartisan.openapi.client.OpenApiClientException;
import com.cartisan.openapi.config.CartisanOpenapiProperties;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * aiplatform wire 契约测试共享脚手架——子类化 {@link OpenApiClient} 仅替换 HTTP 传输（先例
 * {@code PaymentWireTestSupport}）：用对齐 Spring Boot 默认的 {@link ObjectMapper} 反序列化给定的
 * aiplatform 信封 JSON，其余全真实（真实 {@link AiplatformClient} + 真实 Jackson 反序列化 +
 * 真实 AppService 映射）。唯一被替换的是网络传输——对 wire 反序列化契约无关（方法边界 mock
 * 会绕过反序列化路径，是已知反例）。{@code ObjectMapper} 对齐 Spring Boot 默认
 * （{@code FAIL_ON_UNKNOWN_PROPERTIES=false} + {@code JavaTimeModule}）。
 *
 * <p>供 {@code AiplatformAccountClientContractTest} / {@code AiplatformOrderClientContractTest} /
 * {@code AiplatformProjectClientContractTest} / {@code AiplatformWorkspaceClientContractTest} /
 * {@code AiplatformCostClientContractTest} / {@code AiplatformPriceEntryClientContractTest} /
 * {@code AiplatformMaterialClientContractTest} 复用；后续域 {@code *ContractTest} 沿用本脚手架
 * 扩展（各域 AppService 重载）。</p>
 *
 * @since 0.1.0
 */
final class AiplatformWireTestSupport {

    private AiplatformWireTestSupport() {
    }

    /**
     * 用给定 aiplatform 成功信封 JSON 构造一个 HTTP 传输被替换的 {@link AiplatformAccountAppService}。
     * 其 {@link AiplatformClient} 的 {@code get} 忽略 url、把 envelopeBody 按 {@code AiplatformClient}
     * 真实传入的 {@link TypeReference} 反序列化——真实反序列化路径完整保留；wire URL 记入 wireUrlSink
     * （断言出站路径）。
     */
    static AiplatformAccountAppService accountAppServiceWithStubTransport(String envelopeBody, String[] wireUrlSink) {
        ObjectMapper mapper = bootDefaultMapper();
        OpenApiClient stubTransport = new OpenApiClient(new CartisanOpenapiProperties(), null, mapper) {
            @Override
            public <T> T get(String url, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                try {
                    return mapper.readValue(envelopeBody, typeReference);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return new AiplatformAccountAppService(new AiplatformClient(stubTransport, "http://stub-aiplatform"));
    }

    /**
     * 用给定的下游错误（HTTP 状态 + 错误信封 body）构造一个传输始终抛 {@link OpenApiClientException}
     * 的 {@link AiplatformAccountAppService}——框架 {@code OpenApiClient.validateResponse} 对 ≥400 响应
     * 抛 {@code OpenApiClientException(statusCode, body)}，本 stub 在传输层复刻该行为，
     * 让 {@link AiplatformClient} 的错误信封解析（透传翻译）打到真实路径。
     */
    static AiplatformAccountAppService accountAppServiceWithErrorTransport(
            int statusCode, String errorBody, String[] wireUrlSink) {
        OpenApiClient stubTransport = new OpenApiClient(new CartisanOpenapiProperties(), null, bootDefaultMapper()) {
            @Override
            public <T> T get(String url, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                throw new OpenApiClientException(statusCode, errorBody);
            }
        };
        return new AiplatformAccountAppService(new AiplatformClient(stubTransport, "http://stub-aiplatform"));
    }

    /**
     * 订单域版 {@link #accountAppServiceWithStubTransport}——{@code get}（清单/详情）走真实
     * {@link TypeReference} 反序列化，{@code download}（源码包）返回给定 {@link BinaryResponse}：
     * 二进制无反序列化路径，stub 只需保全字节与响应头。
     */
    static AiplatformOrderAppService orderAppServiceWithStubTransport(
            String envelopeBody, BinaryResponse downloadStub, String[] wireUrlSink) {
        ObjectMapper mapper = bootDefaultMapper();
        OpenApiClient stubTransport = new OpenApiClient(new CartisanOpenapiProperties(), null, mapper) {
            @Override
            public <T> T get(String url, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                try {
                    return mapper.readValue(envelopeBody, typeReference);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public BinaryResponse download(String url) {
                wireUrlSink[0] = url;
                return downloadStub;
            }
        };
        return new AiplatformOrderAppService(new AiplatformClient(stubTransport, "http://stub-aiplatform"));
    }

    /**
     * 订单域版 {@link #accountAppServiceWithErrorTransport}——{@code get} 与 {@code download}
     * 都始终抛 {@link OpenApiClientException}（框架对 ≥400 的行为复刻：download 的错误信封
     * 在 body 里、以 UTF-8 解码后入异常）。
     */
    static AiplatformOrderAppService orderAppServiceWithErrorTransport(
            int statusCode, String errorBody, String[] wireUrlSink) {
        OpenApiClient stubTransport = new OpenApiClient(new CartisanOpenapiProperties(), null, bootDefaultMapper()) {
            @Override
            public <T> T get(String url, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                throw new OpenApiClientException(statusCode, errorBody);
            }

            @Override
            public BinaryResponse download(String url) {
                wireUrlSink[0] = url;
                throw new OpenApiClientException(statusCode, errorBody);
            }
        };
        return new AiplatformOrderAppService(new AiplatformClient(stubTransport, "http://stub-aiplatform"));
    }

    /**
     * 项目域版 {@link #accountAppServiceWithStubTransport}——六读口（清单/详情/对话史/PRD/版本
     * 列表/版本详情）全走 {@code get}，按真实 {@link TypeReference} 反序列化。
     */
    static AiplatformProjectAppService projectAppServiceWithStubTransport(String envelopeBody, String[] wireUrlSink) {
        ObjectMapper mapper = bootDefaultMapper();
        OpenApiClient stubTransport = new OpenApiClient(new CartisanOpenapiProperties(), null, mapper) {
            @Override
            public <T> T get(String url, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                try {
                    return mapper.readValue(envelopeBody, typeReference);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return new AiplatformProjectAppService(new AiplatformClient(stubTransport, "http://stub-aiplatform"));
    }

    /**
     * 项目域版 {@link #accountAppServiceWithErrorTransport}——{@code get} 始终抛
     * {@link OpenApiClientException}（框架对 ≥400 的行为复刻）。
     */
    static AiplatformProjectAppService projectAppServiceWithErrorTransport(
            int statusCode, String errorBody, String[] wireUrlSink) {
        OpenApiClient stubTransport = new OpenApiClient(new CartisanOpenapiProperties(), null, bootDefaultMapper()) {
            @Override
            public <T> T get(String url, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                throw new OpenApiClientException(statusCode, errorBody);
            }
        };
        return new AiplatformProjectAppService(new AiplatformClient(stubTransport, "http://stub-aiplatform"));
    }

    /**
     * 沙箱域版 {@link #accountAppServiceWithStubTransport}——读口（清单/详情）走 {@code get}、
     * 四干预动作（唤醒/休眠/重建/封存）走 {@code post}（无请求体），均按真实 {@link TypeReference}
     * 反序列化；post 的 wire URL 记入 wireUrlSink 后复用同一 sink（读写同域断言各自调用）。
     */
    static AiplatformWorkspaceAppService workspaceAppServiceWithStubTransport(
            String envelopeBody, String[] wireUrlSink) {
        ObjectMapper mapper = bootDefaultMapper();
        OpenApiClient stubTransport = new OpenApiClient(new CartisanOpenapiProperties(), null, mapper) {
            @Override
            public <T> T get(String url, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                try {
                    return mapper.readValue(envelopeBody, typeReference);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public <T> T post(String url, Object body, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                try {
                    return mapper.readValue(envelopeBody, typeReference);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return new AiplatformWorkspaceAppService(new AiplatformClient(stubTransport, "http://stub-aiplatform"));
    }

    /**
     * 沙箱域版 {@link #accountAppServiceWithErrorTransport}——{@code get} 与 {@code post} 都始终抛
     * {@link OpenApiClientException}（框架对 ≥400 的行为复刻：四动作的错误信封同读口）。
     */
    static AiplatformWorkspaceAppService workspaceAppServiceWithErrorTransport(
            int statusCode, String errorBody, String[] wireUrlSink) {
        OpenApiClient stubTransport = new OpenApiClient(new CartisanOpenapiProperties(), null, bootDefaultMapper()) {
            @Override
            public <T> T get(String url, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                throw new OpenApiClientException(statusCode, errorBody);
            }

            @Override
            public <T> T post(String url, Object body, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                throw new OpenApiClientException(statusCode, errorBody);
            }
        };
        return new AiplatformWorkspaceAppService(new AiplatformClient(stubTransport, "http://stub-aiplatform"));
    }

    /**
     * 成本域版 {@link #accountAppServiceWithStubTransport}——四读口（总览/unpriced/项目成本清单/
     * 单项目下钻）全走 {@code get}，按真实 {@link TypeReference} 反序列化。
     */
    static AiplatformCostAppService costAppServiceWithStubTransport(String envelopeBody, String[] wireUrlSink) {
        ObjectMapper mapper = bootDefaultMapper();
        OpenApiClient stubTransport = new OpenApiClient(new CartisanOpenapiProperties(), null, mapper) {
            @Override
            public <T> T get(String url, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                try {
                    return mapper.readValue(envelopeBody, typeReference);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return new AiplatformCostAppService(new AiplatformClient(stubTransport, "http://stub-aiplatform"));
    }

    /**
     * 成本域版 {@link #accountAppServiceWithErrorTransport}——{@code get} 始终抛
     * {@link OpenApiClientException}（框架对 ≥400 的行为复刻）。
     */
    static AiplatformCostAppService costAppServiceWithErrorTransport(
            int statusCode, String errorBody, String[] wireUrlSink) {
        OpenApiClient stubTransport = new OpenApiClient(new CartisanOpenapiProperties(), null, bootDefaultMapper()) {
            @Override
            public <T> T get(String url, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                throw new OpenApiClientException(statusCode, errorBody);
            }
        };
        return new AiplatformCostAppService(new AiplatformClient(stubTransport, "http://stub-aiplatform"));
    }

    /**
     * 单价表域版 {@link #accountAppServiceWithStubTransport}——清单走 {@code get}、改价/停用走
     * {@code post}（改价带命令体、停用 null body），均按真实 {@link TypeReference} 反序列化；
     * post 额外把命令体按<strong>生产 mapper 口径</strong>序列化进 wireBodySink（出站 JSON 形状
     * 断言——OpenApiClient 序列化在其 sendWithBody 内部，传输替换于 post 方法边界会绕过它，
     * 故在 stub 里用对齐生产的 mapper 补打这一 seam；null body 原样记 null）。
     */
    static AiplatformPriceEntryAppService priceEntryAppServiceWithStubTransport(
            String envelopeBody, String[] wireUrlSink, String[] wireBodySink) {
        ObjectMapper mapper = bootDefaultMapper();
        // cartisan-web 生产 ObjectMapper 启用 WRITE_BIGDECIMAL_AS_PLAIN（JacksonConfiguration：
        // BigDecimal 禁科学计数）——出站命令体序列化断言须对齐生产口径，而非 Spring Boot 默认
        ObjectMapper commandBodyMapper = bootDefaultMapper()
                .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
        OpenApiClient stubTransport = new OpenApiClient(new CartisanOpenapiProperties(), null, mapper) {
            @Override
            public <T> T get(String url, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                try {
                    return mapper.readValue(envelopeBody, typeReference);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public <T> T post(String url, Object body, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                try {
                    wireBodySink[0] = body == null ? null : commandBodyMapper.writeValueAsString(body);
                    return mapper.readValue(envelopeBody, typeReference);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return new AiplatformPriceEntryAppService(new AiplatformClient(stubTransport, "http://stub-aiplatform"));
    }

    /**
     * 单价表域版 {@link #accountAppServiceWithErrorTransport}——{@code get} 与 {@code post} 都始终抛
     * {@link OpenApiClientException}（框架对 ≥400 的行为复刻：改价/停用的错误信封同读口）。
     */
    static AiplatformPriceEntryAppService priceEntryAppServiceWithErrorTransport(
            int statusCode, String errorBody, String[] wireUrlSink) {
        OpenApiClient stubTransport = new OpenApiClient(new CartisanOpenapiProperties(), null, bootDefaultMapper()) {
            @Override
            public <T> T get(String url, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                throw new OpenApiClientException(statusCode, errorBody);
            }

            @Override
            public <T> T post(String url, Object body, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                throw new OpenApiClientException(statusCode, errorBody);
            }
        };
        return new AiplatformPriceEntryAppService(new AiplatformClient(stubTransport, "http://stub-aiplatform"));
    }

    /**
     * 知识素材域版 {@link #accountAppServiceWithStubTransport}——清单/详情走 {@code get}、
     * 停用/启用走 {@code post}（null body）、删除走 {@code delete}（cartisan-boot#33，无 body
     * 参数——DELETE 动词唯一出口），均按真实 {@link TypeReference} 反序列化；post 额外把命令体
     * 序列化进 wireBodySink（null body 原样记 null——钉死两治理动作无请求体契约）。
     */
    static AiplatformMaterialAppService materialAppServiceWithStubTransport(
            String envelopeBody, String[] wireUrlSink, String[] wireBodySink) {
        ObjectMapper mapper = bootDefaultMapper();
        OpenApiClient stubTransport = new OpenApiClient(new CartisanOpenapiProperties(), null, mapper) {
            @Override
            public <T> T get(String url, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                try {
                    return mapper.readValue(envelopeBody, typeReference);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public <T> T post(String url, Object body, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                try {
                    wireBodySink[0] = body == null ? null : mapper.writeValueAsString(body);
                    return mapper.readValue(envelopeBody, typeReference);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public <T> T delete(String url, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                try {
                    return mapper.readValue(envelopeBody, typeReference);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return new AiplatformMaterialAppService(new AiplatformClient(stubTransport, "http://stub-aiplatform"));
    }

    /**
     * 知识素材域版 {@link #accountAppServiceWithErrorTransport}——{@code get}/{@code post}/
     * {@code delete} 都始终抛 {@link OpenApiClientException}（框架对 ≥400 的行为复刻：
     * 三治理动作与读口的错误信封同形）。
     */
    static AiplatformMaterialAppService materialAppServiceWithErrorTransport(
            int statusCode, String errorBody, String[] wireUrlSink) {
        OpenApiClient stubTransport = new OpenApiClient(new CartisanOpenapiProperties(), null, bootDefaultMapper()) {
            @Override
            public <T> T get(String url, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                throw new OpenApiClientException(statusCode, errorBody);
            }

            @Override
            public <T> T post(String url, Object body, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                throw new OpenApiClientException(statusCode, errorBody);
            }

            @Override
            public <T> T delete(String url, TypeReference<T> typeReference) {
                wireUrlSink[0] = url;
                throw new OpenApiClientException(statusCode, errorBody);
            }
        };
        return new AiplatformMaterialAppService(new AiplatformClient(stubTransport, "http://stub-aiplatform"));
    }

    private static ObjectMapper bootDefaultMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);   // Spring Boot 默认
    }
}
