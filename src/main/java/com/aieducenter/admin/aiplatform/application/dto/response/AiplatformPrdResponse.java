package com.aieducenter.admin.aiplatform.application.dto.response;

import java.time.Instant;

/**
 * AI 平台 PRD 读响应——北向出口，逐字镜像 aiplatform {@code PrdResponse}（#41），无增删字段、
 * 无换型（spec #62 忠实透传）。当前版 PRD（v1 无版本链只最新版），事实源是项目 dev 工作区的
 * {@code docs/PRD.md}。
 *
 * @param projectId 项目标识（TSID 十进制字符串）
 * @param content   markdown 正文（工作区文件原样）
 * @param updatedAt 最近写出时间（工作区文件 mtime，秒精度——与正文同一事实源；Instant）
 */
public record AiplatformPrdResponse(
        String projectId,
        String content,
        Instant updatedAt
) {
}
