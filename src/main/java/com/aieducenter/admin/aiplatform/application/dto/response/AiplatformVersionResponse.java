package com.aieducenter.admin.aiplatform.application.dto.response;

import java.time.LocalDateTime;

/**
 * AI 平台版本序列单条——北向出口，逐字镜像 aiplatform {@code VersionResponse}（#91/#93），
 * 无增删字段、无换型（spec #62 忠实透传）。正本＝容器内 git log：每轮编码 run 收口自动成版
 * （commit 主题 = 收口摘要、Run-Id trailer 锚定收尾卡）；run 版本与回滚版本同形互斥。
 *
 * @since 0.1.0
 */
public record AiplatformVersionResponse(

        /** 成版 commit hash（hex 40 位） */
        String commitHash,

        /** commit 主题（= 收口摘要） */
        String subject,

        /** 锚定 run（Run-Id trailer 联接对话史收尾卡；回滚版本为 null） */
        String runId,

        /** 回滚锚定的源版本（run 版本为 null） */
        String rollbackFrom,

        /** 成版时刻 */
        LocalDateTime committedAt
) {
}
