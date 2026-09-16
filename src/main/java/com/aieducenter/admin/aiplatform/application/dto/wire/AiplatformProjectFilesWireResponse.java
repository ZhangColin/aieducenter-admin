package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.util.List;

/**
 * aiplatform 文件树响应的 wire 镜像——与 aiplatform {@code ProjectFilesResponse}（#163 文件区）
 * 字段同构：交付文件视图＝项目 dev 工作区剔除非交付物（data/、.platform/、node_modules/ 与
 * .env——与源码包同口径）后的只读文件清单。只列文件（目录由前端按路径段合成），按路径稳定
 * 排序，直读工作区实时状态。
 *
 * @param projectId 项目标识（TSID 十进制字符串）
 * @param files     文件条目（工作区相对路径 + 字节大小，按路径稳定排序）
 */
public record AiplatformProjectFilesWireResponse(
        String projectId,
        List<FileEntry> files
) {

    /** 单个文件条目（与 provider {@code ProjectFilesResponse.FileEntry} 同构）。 */
    public record FileEntry(
            String path,
            long size
    ) {
    }
}
