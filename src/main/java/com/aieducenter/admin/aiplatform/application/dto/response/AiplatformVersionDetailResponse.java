package com.aieducenter.admin.aiplatform.application.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI 平台版本详情——北向出口，逐字镜像 aiplatform {@code VersionDetailResponse}（#91/#93），
 * 无增删字段、无换型（spec #62 忠实透传）。版本元数据 + 锚定的收尾卡载荷（Run-Id 联接对话史
 * closing 条目，#88 同载荷）；收尾卡缺位时 {@code closing} 为 null（版本元数据仍如实返回）。
 *
 * @since 0.1.0
 */
public record AiplatformVersionDetailResponse(

        /** 成版 commit hash（hex 40 位） */
        String commitHash,

        /** commit 主题（= 收口摘要） */
        String subject,

        /** 锚定 run（回滚版本为 null） */
        String runId,

        /** 回滚锚定的源版本（run 版本为 null） */
        String rollbackFrom,

        /** 成版时刻 */
        LocalDateTime committedAt,

        /** 收尾卡载荷原样（缺位为 null） */
        Map<String, Object> closing
) {
}
