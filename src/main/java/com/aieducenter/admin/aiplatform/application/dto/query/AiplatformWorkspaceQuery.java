package com.aieducenter.admin.aiplatform.application.dto.query;

/**
 * AI 平台沙箱列表查询参数（BFF 透传 aiplatform）——北向期望态/实态两维过滤，字段名与 aiplatform
 * {@code GET /api/backoffice/workspaces} 查询参数逐字镜像（spec #62 定稿、issue #66）。
 *
 * <p>漂移发现口径：两维独立单选、可组合、可缺省（缺省＝全量）——{@code desired} 期望态
 * （1=运行 2=休眠 3=封存，DB 意图侧）、{@code actual} 容器实态（1=运行中 2=已停止 3=无容器
 * 4=未知，docker 现场探查）。「期望运行而实态无容器」即漂移行——{@code desired=1 & actual=3}
 * 即捞漂移清单；期望休眠/封存而 actual=3 是正常收敛态。</p>
 *
 * <p>枚举筛选项以 aiplatform {@code DesiredState} / {@code ContainerState}（BaseEnum）的
 * <strong>Integer code</strong> 透传。绑定裁决分两段（同订单/项目口径）：非整数取值
 * （{@code desired=abc}）在 <strong>admin 绑定层</strong>即 400（框架默认信封，不到 provider）；
 * <strong>合法整数但未知 code</strong>（如 7）透传 provider，由其绑定层以 400 WSP_014
 * （数字业务码 1014）裁决、错误信封忠实透传。分页参数 {@code page}/{@code size} 不在此
 * （controller 独立绑定，缺省 1/20 镜像 provider）。</p>
 *
 * @since 0.1.0
 */
public record AiplatformWorkspaceQuery(

        /** 期望态单选（1=运行 2=休眠 3=封存）；null = 全量 */
        Integer desired,

        /** 容器实态单选（1=运行中 2=已停止 3=无容器 4=未知）；null = 全量 */
        Integer actual
) {
}
