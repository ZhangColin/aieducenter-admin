package com.aieducenter.admin.aiplatform.application.dto.wire;

/**
 * aiplatform token 用量五档的 wire 镜像——与 aiplatform 领域模型 {@code TokenUsage}
 * （#161/#164 成本域出口直接序列化领域记录）字段同构：input/output/cacheRead/cacheWrite/reasoning。
 *
 * <p>五档为 <strong>primitive long</strong>（互斥分解、加和恒等于提供方 total 口径）——provider
 * 序列化为 <strong>JSON 数字</strong>（非 cartisan-web 全局 Long→ToStringSerializer 的字符串口径，
 * 该注册只覆盖包装类型 Long）；聚合空结果时五档全零（{@code TokenUsage.ZERO} 基线），非 null。</p>
 *
 * @since 0.1.0
 */
public record AiplatformTokenUsageWireResponse(

        /** 输入 token（OpenAI 口径 = prompt − cached，即未命中缓存部分） */
        long input,

        /** 输出 token（OpenAI 口径 = completion − reasoning，推理已拆出单列） */
        long output,

        /** 缓存读 token（命中部分——按缓存读档位单独计价） */
        long cacheRead,

        /** 缓存写 token（Anthropic cache_creation 写入量） */
        long cacheWrite,

        /** 推理 token（OpenAI reasoning，从 completion 拆出单列） */
        long reasoning
) {
}
