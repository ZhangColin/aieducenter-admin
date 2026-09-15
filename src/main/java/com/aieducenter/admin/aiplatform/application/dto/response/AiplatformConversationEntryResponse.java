package com.aieducenter.admin.aiplatform.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 平台对话史条目——北向出口，逐字镜像 aiplatform {@code ConversationEntryResponse}
 * （#89 水合载荷），无增删字段、无换型（spec #62 忠实透传）。
 *
 * <p>{@code kind} 为 Integer code（1=用户发言 2=智能体回复 3=问答卡 4=问答作答 5=收尾卡
 * 6=平台轻引导）+ {@code kindName} 中文名随行（aiplatform#186 已落，provider 出口提供、
 * BFF 透传不臆造映射）。{@code question}/{@code closing} 为事件载荷 JSON 原样透传。</p>
 *
 * @since 0.1.0
 */
public record AiplatformConversationEntryResponse(

        /** 条目标识（Long；JSON string 出口） */
        Long id,

        /** 条目类型（1=用户发言 2=智能体回复 3=问答卡 4=问答作答 5=收尾卡 6=平台轻引导） */
        Integer kind,

        /** 条目类型中文名（provider 出口提供，aiplatform#186） */
        String kindName,

        /** 锚定编码 run（无 run 语境为 null） */
        String runId,

        /** 正文（用户发言/智能体回复/作答内容；问答卡/收尾卡为 null） */
        String text,

        /** 问答卡载荷原样（answered=false 即挂起待答；非问答卡为 null） */
        Map<String, Object> question,

        /** 收尾卡载荷原样（版本详情锚定的权威事实，#88 同载荷；非收尾卡为 null） */
        Map<String, Object> closing,

        /** 用户发言随带的圈注附件 JSON 数组（无附件为 null/空） */
        List<Map<String, Object>> attachments,

        /** 问答卡是否已作答（非问答卡恒 false） */
        boolean answered,

        /** 写入时点（id 升序 = 对话序） */
        LocalDateTime at
) {
}
