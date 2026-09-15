package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.time.LocalDateTime;
import java.util.List;

/**
 * aiplatform 沙箱详情的 wire 镜像——与 aiplatform {@code BackofficeWorkspaceDetailResponse}
 * （#173 观测面）字段同构：清单行的超集，另带置备失败原因（FAILED 态排障）、封存包寻址键、
 * 审计时间列与中间件资源清单（连接串原文，容器内回环形态）。四干预动作（#174）的回执也是本形状
 * ——「动作后的观测详情（新事实）」。
 *
 * <p>全量如实呈现，无脱敏字段（机机签名面）——{@code internalUrl} 连接串原文透传，BFF 不解读。</p>
 *
 * @since 0.1.0
 */
public record AiplatformWorkspaceDetailWireResponse(

        /** 工作区标识（TSID 十进制字符串） */
        String workspaceId,

        /** 容器名 */
        String containerName,

        /** 预览网络名 */
        String networkName,

        /** 环境类型（1=开发 2=测试 3=生产） */
        Integer kind,

        /** 环境类型名（provider 出口提供） */
        String kindName,

        /** 置备状态（1=置备中 2=就绪 3=失败） */
        Integer status,

        /** 置备状态名（provider 出口提供） */
        String statusName,

        /** 置备失败原因（FAILED 态非 null，其余 null） */
        String provisionError,

        /** 期望态（1=运行 2=休眠 3=封存，意图侧） */
        Integer desiredState,

        /** 期望态名（provider 出口提供） */
        String desiredStateName,

        /** 容器实态（1=运行中 2=已停止 3=无容器 4=未知——现场探查、封存容缺） */
        Integer containerState,

        /** 容器实态名（provider 出口提供） */
        String containerStateName,

        /** 最近触碰 */
        LocalDateTime lastTouchAt,

        /** 卷用量（字节；封存容缺/探查失败为 null） */
        Long volumeSizeBytes,

        /** 封存时刻（未封存为 null） */
        LocalDateTime sealedAt,

        /** 封存包寻址键（未封存为 null） */
        String archivePath,

        /** 封存包大小（字节；未封存为 null） */
        Long archiveSizeBytes,

        /** 创建时间（审计列） */
        LocalDateTime createdAt,

        /** 更新时间（审计列） */
        LocalDateTime updatedAt,

        /** 随供给中间件资源清单（连接串原文，容器内回环形态） */
        List<MiddlewareResourceObservation> resources,

        /** 所属项目引用（无所属项目为 null——软引用容缺，工作区先于项目存在） */
        AiplatformWorkspaceSummaryWireResponse.ProjectRef project
) {

    /**
     * 随供给中间件资源观测（#173）：kind Integer code（1=PostgreSQL 2=Redis——provider 出口
     * 无 *Name，忠实镜像不加戏）＋容器名＋容器内回环连接串原文。
     */
    public record MiddlewareResourceObservation(

            /** 中间件类型（1=PostgreSQL 2=Redis） */
            Integer kind,

            /** 中间件容器名 */
            String containerName,

            /** 容器内回环连接串原文 */
            String internalUrl
    ) {
    }
}
