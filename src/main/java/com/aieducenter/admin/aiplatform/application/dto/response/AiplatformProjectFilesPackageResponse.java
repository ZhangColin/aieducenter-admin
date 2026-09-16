package com.aieducenter.admin.aiplatform.application.dto.response;

/**
 * AI 平台项目文件包——北向出口载体（tar.gz 二进制 + provider 响应头原值）。
 *
 * <p>二进制端点无 {@code ApiResponse} 信封（spec #62）：provider 以 {@code application/gzip} +
 * {@code Content-Disposition: attachment; filename="…"} 出流——文件名两态由 provider 决定
 * （已封存项目＝封存包整卷口径 {@code {id}-archive.tar.gz}；未封存＝即时源码包交付口径
 * {@code {id}-source.tar.gz}，沙箱休眠中会先唤醒重建再打包），{@code contentType}/
 * {@code contentDisposition} 为 provider 响应头 <strong>raw 值</strong>原样透传（不打解析、
 * 不重构文件名——BFF 不判封存态）；{@code content} 为全量缓冲的原始字节（内存中转、不落盘
 * ——provider 侧本身即 byte[] 缓冲出口，经 cartisan-openapi {@code download} 保全）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformProjectFilesPackageResponse(

        /** tar.gz 原始字节（gzip 字节流，不做任何字符解码） */
        byte[] content,

        /** provider 的 Content-Type raw 值（application/gzip） */
        String contentType,

        /** provider 的 Content-Disposition raw 值（attachment; filename="…-archive.tar.gz" 或 …-source.tar.gz） */
        String contentDisposition
) {
}
