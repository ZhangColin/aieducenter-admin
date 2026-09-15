package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * aiplatform 版本详情的 wire 镜像——与 aiplatform {@code VersionDetailResponse}（#91/#93）
 * 字段同构：版本元数据 + 锚定的收尾卡载荷（Run-Id 联接对话史 closing 条目，#88 同载荷）。
 * 收尾卡落库失败等缺口下 {@code closing} 可 null（版本元数据仍如实返回）；回滚版本无
 * run/收尾卡（runId 与 closing 均空、rollbackFrom 锚定源版本）。
 *
 * @since 0.1.0
 */
public record AiplatformVersionDetailWireResponse(

        /** 成版 commit hash（hex 40 位） */
        String commitHash,

        /** commit 主题（= 收口摘要） */
        String subject,

        /** 锚定 run（回滚版本为 null） */
        String runId,

        /** 回滚锚定的源版本（run 版本为 null） */
        String rollbackFrom,

        /** 成版时刻（git epoch 秒转 JVM 时区） */
        LocalDateTime committedAt,

        /** 收尾卡载荷原样（Map 逐字透传、BFF 不解读；缺位为 null） */
        Map<String, Object> closing
) {
}
