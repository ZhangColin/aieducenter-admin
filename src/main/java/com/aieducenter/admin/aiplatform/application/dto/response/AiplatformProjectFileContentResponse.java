package com.aieducenter.admin.aiplatform.application.dto.response;

/**
 * AI 平台项目文本文件内容响应——北向出口，逐字镜像 aiplatform {@code ProjectFileContentResponse}
 * （#163「点看」），无增删字段、无换型（spec #62 忠实透传）。只读策略拒绝口径（非交付物/机密
 * PRJ_020、不存在 PRJ_021、超 1 MiB PRJ_022、非文本 PRJ_023）归 provider 裁决透传，
 * 不在此载体表达。
 *
 * @param path    工作区相对路径（请求 path 原样回显）
 * @param content 文本正文（工作区文件原样）
 */
public record AiplatformProjectFileContentResponse(
        String path,
        String content
) {
}
