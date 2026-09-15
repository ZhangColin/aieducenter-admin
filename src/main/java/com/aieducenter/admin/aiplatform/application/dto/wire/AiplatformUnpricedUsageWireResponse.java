package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.time.Instant;
import java.util.List;

/**
 * aiplatform unpriced 全局警示的 wire 镜像——与 aiplatform {@code BackofficeUnpricedUsageResponse}
 * （#161 成本运营，用量驱动）字段同构：窗口回显 + 未配价档位清单。
 *
 * <p>窗口内有 token 用量且事件时点无生效单价的 (provider, model, 档位) 按档位汇总——
 * {@code tokens} 只累计无价分量（同档位部分有价部分无价时只计无价部分）。静态配价缺口不做：
 * 已配价档位与无用量档位不出现；窗口内无未配价用量时 {@code items} 为空清单，不是错误。</p>
 *
 * @param from  窗口起点（含；原样回显）
 * @param to    窗口终点（不含；原样回显）
 * @param items 未配价档位清单（provider/model/档位码序）
 */
public record AiplatformUnpricedUsageWireResponse(
        Instant from,
        Instant to,
        List<UnpricedTier> items
) {

    /**
     * 未配价档位项（tokenKind 为 Integer code + tokenKindName 随附，1=输入 2=输出 3=缓存读
     * 4=缓存写 5=推理；tokens 只计无价分量）。
     */
    public record UnpricedTier(
            String provider,
            String model,
            Integer tokenKind,
            String tokenKindName,
            long tokens
    ) {
    }
}
