package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.time.LocalDateTime;

/**
 * aiplatform 版本序列单条的 wire 镜像——与 aiplatform {@code VersionResponse}（#91/#93）字段同构：
 * 正本＝容器内 git log（无库表），每轮编码 run 收口自动成版。run 版本与回滚版本同形：
 * run 版本 {@code runId} 非空、{@code rollbackFrom} 为 null；回滚版本反之。
 *
 * @since 0.1.0
 */
public record AiplatformVersionWireResponse(

        /** 成版 commit hash（hex 40 位） */
        String commitHash,

        /** commit 主题（= 收口摘要） */
        String subject,

        /** 锚定 run（Run-Id trailer 联接对话史收尾卡；回滚版本为 null） */
        String runId,

        /** 回滚锚定的源版本（run 版本为 null） */
        String rollbackFrom,

        /** 成版时刻（git epoch 秒转 JVM 时区） */
        LocalDateTime committedAt
) {
}
