package com.aieducenter.admin.aiplatform.application.dto.response;

import java.time.LocalDateTime;

/**
 * AI 平台沙箱清单行（BFF 透传 aiplatform）——北向 {@code GET /api/admin/aiplatform/workspaces}
 * 条目，与 aiplatform {@code BackofficeWorkspaceSummaryResponse} 字段逐字同构（issue #66）。
 *
 * <p>运营第一次能看见每台沙箱睡没睡、封没封、占多少存储——期望态与 docker 实态两列分示
 * （漂移一眼可见）。行内嵌项目引用（跳转项目详情的锚；无所属项目为 null）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformWorkspaceSummaryResponse(

        /** 工作区标识（TSID 十进制字符串） */
        String workspaceId,

        /** 容器名（与 docker ps 对账锚点） */
        String containerName,

        /** 环境类型（1=开发 2=测试 3=生产） */
        Integer kind,

        /** 环境类型名 */
        String kindName,

        /** 置备状态（1=置备中 2=就绪 3=失败）：在途置备/唤醒（PROVISIONING）与漂移的区分锚 */
        Integer status,

        /** 置备状态名 */
        String statusName,

        /** 期望态（1=运行 2=休眠 3=封存，意图侧） */
        Integer desiredState,

        /** 期望态名 */
        String desiredStateName,

        /** 容器实态（1=运行中 2=已停止 3=无容器 4=未知——docker 现场探查，不落库） */
        Integer containerState,

        /** 容器实态名 */
        String containerStateName,

        /** 最近触碰（闲置计时输入） */
        LocalDateTime lastTouchAt,

        /** 卷用量（字节；封存容缺/探查失败为 null） */
        Long volumeSizeBytes,

        /** 封存时刻（未封存为 null） */
        LocalDateTime sealedAt,

        /** 封存包大小（字节；未封存为 null） */
        Long archiveSizeBytes,

        /** 所属项目引用（无所属项目为 null） */
        ProjectRef project
) {

    /**
     * 所属项目引用：认出项目即可（详情另取）——id＋名称＋归档位。
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
