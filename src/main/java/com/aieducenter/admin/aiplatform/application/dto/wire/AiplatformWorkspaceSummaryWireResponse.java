package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.time.LocalDateTime;

/**
 * aiplatform 沙箱清单条目的 wire 镜像——与 aiplatform {@code BackofficeWorkspaceSummaryResponse}
 * （#173 观测面）字段同构。用于 Jackson 反序列化 {@link com.cartisan.openapi.client.OpenApiClient}
 * 响应，不与 aiplatform 内部 DTO 耦合。
 *
 * <p>类型口径（spec #62）：{@code workspaceId} 为 <strong>String</strong>（TSID 十进制串）；
 * 四枚举均为 <strong>Integer code</strong> + {@code *Name} 中文名两列（kind/status/desiredState/
 * containerState——provider 已配对，BFF 透传不做 code→中文映射）；期望态与 docker 实态两列如实
 * 分示（desired=1 而 containerState=3 即「实态已亡」漂移行）。卷用量/封存包大小为 <strong>Long</strong>
 * （字节，cartisan-web 全局 Long→ToStringSerializer 出 JSON string）；封存容缺/探查失败为 null。</p>
 *
 * @since 0.1.0
 */
public record AiplatformWorkspaceSummaryWireResponse(

        /** 工作区标识（TSID 十进制字符串） */
        String workspaceId,

        /** 容器名（与 docker ps 对账锚点） */
        String containerName,

        /** 环境类型（1=开发 2=测试 3=生产） */
        Integer kind,

        /** 环境类型名（provider 出口提供） */
        String kindName,

        /** 置备状态（1=置备中 2=就绪 3=失败）：在途置备/唤醒（PROVISIONING）与漂移的区分锚 */
        Integer status,

        /** 置备状态名（provider 出口提供） */
        String statusName,

        /** 期望态（1=运行 2=休眠 3=封存，意图侧） */
        Integer desiredState,

        /** 期望态名（provider 出口提供） */
        String desiredStateName,

        /** 容器实态（1=运行中 2=已停止 3=无容器 4=未知——docker 现场探查，不落库） */
        Integer containerState,

        /** 容器实态名（provider 出口提供） */
        String containerStateName,

        /** 最近触碰（闲置计时输入） */
        LocalDateTime lastTouchAt,

        /** 卷用量（字节；封存容缺/探查失败为 null） */
        Long volumeSizeBytes,

        /** 封存时刻（未封存为 null） */
        LocalDateTime sealedAt,

        /** 封存包大小（字节；未封存为 null） */
        Long archiveSizeBytes,

        /** 所属项目引用（无所属项目为 null——工作区先于项目存在、软引用容缺） */
        ProjectRef project
) {

    /**
     * 所属项目引用（清单行内嵌，跳转项目详情的锚）：认出项目即可（详情另取）——id＋名称＋归档位。
     */
    public record ProjectRef(

            /** 项目标识（TSID 十进制字符串） */
            String projectId,

            /** 项目名 */
            String name,

            /** 是否已归档 */
            Boolean archived
    ) {
    }
}
