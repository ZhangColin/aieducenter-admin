package com.aieducenter.admin.aiplatform.application.dto.wire;

import java.time.LocalDateTime;
import java.util.List;

/**
 * aiplatform 订单详情的 wire 镜像——与 aiplatform {@code BackofficeOrderDetailResponse}
 * （#155 价目留痕已冻结）字段同构（21 字段 + 内嵌价目历史），逐字镜像、无增删。
 *
 * <p>报价依据全量事实：PRD 快照正文（下单冻结）、价目历史（append-only 全量，新 → 旧，带操作者）、
 * 全部状态时点。取消/归档操作者两肢为 admin 侧管理员标识（透传头落痕），存量行/无头 NULL。</p>
 *
 * @since 0.1.0
 */
public record AiplatformOrderDetailWireResponse(

        /** 订单标识（TSID 十进制字符串） */
        String id,

        /** 所属项目标识（TSID 十进制字符串） */
        String projectId,

        /** 项目名（软引用缺档为 null） */
        String projectName,

        /** 下单用户昵称（下单账号可空/缺档为 null） */
        String ownerDisplayName,

        /** 订单状态（1=待报价 2=已报价 3=已支付 4=已归档 5=已取消） */
        Integer status,

        /** 状态中文名（provider 出口提供） */
        String statusName,

        /** 当前总价（分；待报价 NULL） */
        Long amount,

        /** 币种（v1 恒 CNY；待报价 NULL） */
        String currency,

        /** 当前后台备注（最新价目行；待报价 NULL） */
        String note,

        /** 价目历史（新 → 旧，append-only 全量；带操作者，存量行操作者为空；待报价空表） */
        List<AiplatformPriceEntryWireResponse> priceEntries,

        /** 下单时 PRD 全文快照（交易标的，只插不改） */
        String prdSnapshot,

        /** 下单时间 */
        LocalDateTime createdAt,

        /** 首次报价时点（改价不刷新；待报价 NULL） */
        LocalDateTime quotedAt,

        /** 支付成功时点（未支付 NULL） */
        LocalDateTime paidAt,

        /** 归档时点（未归档 NULL） */
        LocalDateTime archivedAt,

        /** 重试归档操作者 id（#158；支付链自动归档/缺透传头为 null） */
        String archiveOperatorId,

        /** 重试归档操作者名（直读；口径同取消留痕两列） */
        String archiveOperatorName,

        /** 取消时点（未取消 NULL） */
        LocalDateTime cancelledAt,

        /** 取消原因（#157 运营取消必填留痕，运营内部口径；用户取消/未取消为 null） */
        String cancelReason,

        /** 取消操作者 id（admin 侧管理员 TSID；用户取消/缺透传头为 null） */
        String cancelOperatorId,

        /** 取消操作者名（直读；口径同价目行 operator） */
        String cancelOperatorName
) {
}
