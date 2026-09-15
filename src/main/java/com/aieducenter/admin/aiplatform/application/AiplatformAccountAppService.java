package com.aieducenter.admin.aiplatform.application;

import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformAccountProfileResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformAccountProfileWireResponse;
import com.aieducenter.admin.aiplatform.infrastructure.AiplatformClient;
import org.springframework.stereotype.Service;

/**
 * aiplatform 账号域 BFF 应用服务——聚合 aiplatform 账号极简档案读口（issue #63 T1 tracer bullet）。
 *
 * <p>admin 作为 BFF：调接口 + DTO 转换，不持业务逻辑、不持账号数据、不记业务审计（审计归
 * aiplatform）。下游错误已由 {@link AiplatformClient} 统一翻译为 {@link AiplatformUpstreamException}
 * （provider 信封原样透传），本层不再 try/catch——区别于 payment/account 的
 * {@code translateXxxError} 映射式翻译（spec #62 定稿：aiplatform 不做映射）。</p>
 *
 * <p>对接 aiplatform {@code GET /api/backoffice/accounts/{externalId}}（#154 已冻结）。</p>
 *
 * @since 0.1.0
 */
@Service
public class AiplatformAccountAppService {

    private final AiplatformClient aiplatformClient;

    public AiplatformAccountAppService(AiplatformClient aiplatformClient) {
        this.aiplatformClient = aiplatformClient;
    }

    /**
     * 查询账号极简档案（透传 aiplatform）——订单/项目详情里按 externalId 认交易对手正身。
     */
    public AiplatformAccountProfileResponse getAccountProfile(String externalId) {
        AiplatformAccountProfileWireResponse wire = aiplatformClient.getAccountProfile(externalId);
        return new AiplatformAccountProfileResponse(
                wire.id(), wire.externalId(), wire.displayName(), wire.createdAt());
    }
}
