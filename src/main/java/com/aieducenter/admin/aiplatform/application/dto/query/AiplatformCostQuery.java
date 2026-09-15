package com.aieducenter.admin.aiplatform.application.dto.query;

import java.time.Instant;

/**
 * AI 平台成本域查询参数（BFF 透传 aiplatform）——成本四端点共用的查询时间窗，字段名与 aiplatform
 * {@code GET /api/backoffice/costs/**} 查询参数逐字镜像（spec #62 定稿、issue #67）。
 *
 * <p>时间窗 {@code [from, to)} <strong>半开区间</strong>、ISO-8601 Instant（UTC 带 Z，如
 * {@code 2026-09-01T00:00:00Z}）。provider 侧两参可缺省（缺省＝该侧不限），北向<strong>必填</strong>
 * ——逐字镜像、不设默认窗口（issue #67 拍板，同 #51 grill「如实透传优先于默认窗口」方向）：
 * 窗口由运营显式给出，BFF 不代设、provider 的不限能力不经 BFF 暴露（防无界扫描）。</p>
 *
 * <p>绑定裁决分两段（同订单/项目/沙箱口径）：缺参（必填）在 <strong>admin 绑定层</strong>即 400
 * （框架信封，不到 provider）；非 ISO-8601 Instant（如 {@code from=2026-09-01}——无时区态）为
 * 类型不匹配，按框架 {@code handleTypeMismatch} 既定口径 404（NOT_FOUND 信封）——同样不到
 * provider。provider 侧绑定失败 400 METER_011（数字业务码 3011）仅在 provider 契约演进时可能
 * 出现，错误信封忠实透传。分页参数 {@code page}/{@code size} 不在此（controller 独立绑定，
 * 缺省 1/20 镜像 provider——仅项目成本清单有分页）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformCostQuery(

        /** 窗口起点（含；ISO-8601 Instant，UTC 带 Z）——北向必填 */
        Instant from,

        /** 窗口终点（不含；ISO-8601 Instant，UTC 带 Z）——北向必填 */
        Instant to
) {
}
