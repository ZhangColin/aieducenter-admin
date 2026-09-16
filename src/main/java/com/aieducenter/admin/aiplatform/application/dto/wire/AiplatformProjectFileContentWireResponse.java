package com.aieducenter.admin.aiplatform.application.dto.wire;

/**
 * aiplatform 文本文件内容响应的 wire 镜像——与 aiplatform {@code ProjectFileContentResponse}
 * （#163 文件区「点看」）字段同构：工作区相对路径（请求 path 原样回显）+ 文本正文原样。
 * 仅可浏览路径且不超在线查看上限（1 MiB）、正文无 NUL——各拒绝口径以错误码表达
 * （PRJ_020/021/022/023），不在此载体表达。
 *
 * @param path    工作区相对路径（请求 path 原样回显）
 * @param content 文本正文（工作区文件原样）
 */
public record AiplatformProjectFileContentWireResponse(
        String path,
        String content
) {
}
