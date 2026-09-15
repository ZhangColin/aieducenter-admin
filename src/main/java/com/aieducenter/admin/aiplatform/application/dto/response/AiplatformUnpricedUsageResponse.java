package com.aieducenter.admin.aiplatform.application.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * unpriced 全局警示响应（BFF 北向出口）——逐字镜像 aiplatform {@code BackofficeUnpricedUsageResponse}
 * （issue #67）：窗口回显 + 未配价档位清单（用量驱动）。
 *
 * <p>据此发现漏配价并及时补价（补价只影响此后事件，历史成本不漂移）；窗口内无未配价用量时
 * {@code items} 为空清单，不是错误。</p>
 *
 * @since 0.1.0
 */
public record AiplatformUnpricedUsageResponse(

        /** 窗口起点（含；原样回显） */
        Instant from,

        /** 窗口终点（不含；原样回显） */
        Instant to,

        /** 未配价档位清单（provider/model/档位码序；tokens 只计无价分量） */
        List<UnpricedTier> items
) {

    /** 未配价档位项（tokenKind Integer code + tokenKindName；1=输入 2=输出 3=缓存读 4=缓存写 5=推理）。 */
    public record UnpricedTier(
            String provider,
            String model,
            Integer tokenKind,
            String tokenKindName,
            long tokens
    ) {
    }
}
