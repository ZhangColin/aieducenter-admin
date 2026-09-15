package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * aiplatform 对话史条目的 wire 镜像——与 aiplatform {@code ConversationEntryResponse}
 * （#89 水合载荷）字段同构：对话面全量（用户发言/智能体回复/问答卡/问答作答/收尾卡/平台轻引导），
 * 过程明细（解说段/动作卡流水）不在其中。
 *
 * <p>{@code kind} 为 <strong>Integer code</strong>（1=用户发言 2=智能体回复 3=问答卡 4=问答作答
 * 5=收尾卡 6=平台轻引导）+ {@code kindName} 中文名随行（aiplatform#186 已落——2026-09-15 对照
 * provider 源码 f01d984 核实：backoffice 出口已配 *Name，BFF 透传 provider 值、不做本地映射）。
 * {@code question}/{@code closing} 为事件载荷 JSON 原样（{@code Map} 逐字透传、BFF 不解读）；
 * {@code closing} 是版本详情锚定的权威事实（#88 同载荷）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformConversationEntryWireResponse(

        /** 条目标识（Long；provider Long→JSON string 出口，反序列化回 Long） */
        Long id,

        /** 条目类型（1=用户发言 2=智能体回复 3=问答卡 4=问答作答 5=收尾卡 6=平台轻引导） */
        Integer kind,

        /** 条目类型中文名（provider 出口提供，aiplatform#186） */
        String kindName,

        /** 锚定编码 run（用户发言/收尾卡等无 run 语境为 null） */
        String runId,

        /** 正文（用户发言/智能体回复/作答内容；问答卡/收尾卡为 null） */
        String text,

        /** 问答卡载荷原样（question-raised 事件；answered=false 即挂起待答；非问答卡为 null） */
        Map<String, Object> question,

        /** 收尾卡载荷原样（run-finish 收口扩载，#88 同载荷；非收尾卡为 null） */
        Map<String, Object> closing,

        /** 用户发言随带的圈注附件 JSON 数组（#97 圈注 B 档；无附件为 null/空） */
        List<Map<String, Object>> attachments,

        /** 问答卡是否已作答（非问答卡恒 false） */
        boolean answered,

        /** 写入时点（id 升序 = 对话序） */
        LocalDateTime at
) {
}
