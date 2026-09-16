package com.aieducenter.admin.aiplatform.application;

import java.util.List;

import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformMaterialQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformMaterialDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformMaterialSummaryResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformMaterialDetailWireResponse;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformMaterialListWireRequest;
import com.aieducenter.admin.aiplatform.application.dto.wire.AiplatformMaterialSummaryWireResponse;
import com.aieducenter.admin.aiplatform.infrastructure.AiplatformClient;
import com.cartisan.web.response.PageResponse;
import org.springframework.stereotype.Service;

/**
 * aiplatform 知识素材域 BFF 应用服务——治理四件套：清单（三维检索）＋详情（元数据＋PRD 全文）
 * ＋停用⇄启用（可逆开关）＋删除（治理移除、不可逆），issue #69。管理单元＝素材＝
 * 项目 × 素材类型（登记表一行），非块；内容面零写（不编辑、不手动新增——沉淀唯一触发点不动），
 * 治理手段＝停用/删除。
 *
 * <p>admin 作为 BFF：调接口 + DTO 转换，不持业务逻辑、不持素材数据（知识库归 aiplatform
 * knowledge 上下文）。停用/启用/删除的操作者必留痕语义由 provider 域面守卫承担——缺
 * {@code X-User-Id/X-User-Name} 出站头时 provider 以 400 KNW_006（数字业务码 2006）拦截
 * （知识治理无种子脚本无头通道，与单价表缺头落空有意不同）；KNW_005/006/007 错误信封已由
 * {@link AiplatformClient} 统一翻译为 {@link AiplatformUpstreamException} 原样透传
 * （spec #62 定稿），本层不再 try/catch。操作者身份经框架 {@code RequestContext}→
 * {@code X-User-Id/X-User-Name} 自动透传（AppService 不经手），北向请求体/参数不含操作者字段，
 * 前端无法伪造留痕。</p>
 *
 * <p>分页（spec #62 平台分页统一决议目标态）：北向请求 page <strong>1-based</strong>，出站直传
 * 零换算；回显取 provider 回报的 page/size 原值（含 provider clamp 后的值），BFF 不重复夹取。</p>
 *
 * @since 0.1.0
 */
@Service
public class AiplatformMaterialAppService {

    private final AiplatformClient aiplatformClient;

    public AiplatformMaterialAppService(AiplatformClient aiplatformClient) {
        this.aiplatformClient = aiplatformClient;
    }

    /**
     * 素材清单（透传 aiplatform）——三维过滤可组合、均可缺省（缺省＝全量）：status 单选
     * （1=启用 2=停用）、sunkFrom/To 沉淀时间闭区间（首沉淀时间）、projectId 来源项目精确
     * （查无＝空清单 200）。排序服务端定死＝沉淀时间倒序（新沉淀在前，id 倒序稳定）。
     */
    public PageResponse<AiplatformMaterialSummaryResponse> list(AiplatformMaterialQuery query,
                                                                 int page, int size) {
        PageResponse<AiplatformMaterialSummaryWireResponse> wirePage = aiplatformClient.listMaterials(
                new AiplatformMaterialListWireRequest(
                        query.status(), query.sunkFrom(), query.sunkTo(), query.projectId()),
                page, size);
        List<AiplatformMaterialSummaryResponse> items = wirePage.items().stream()
                .map(AiplatformMaterialAppService::toSummary)
                .toList();
        // 回显 provider 的 page/size 原值（1-based，含 clamp 后值）——不是北向入参回声
        return new PageResponse<>(items, wirePage.total(), wirePage.page(), wirePage.size());
    }

    /**
     * 素材详情（透传 aiplatform）——元数据＋素材全文（块按 seq 以空行拼接）。来源项目引用容缺
     * 直读登记面（不校验项目存在，缺档不炸）。
     */
    public AiplatformMaterialDetailResponse getDetail(String id) {
        return toDetail(aiplatformClient.getMaterial(id));
    }

    /**
     * 停用素材（透传 aiplatform）——可逆开关：素材全部块退出生成命中（重沉淀不复活），误伤可经
     * enable 恢复；重复停用幂等（操作者留最近一次）。回执＝summary（provider 重读登记行的
     * 最新状态与操作者）。
     */
    public AiplatformMaterialSummaryResponse disable(String id) {
        return toSummary(aiplatformClient.disableMaterial(id));
    }

    /**
     * 启用素材（透传 aiplatform）——停用的可逆侧：素材全部块恢复参与生成命中；重复启用幂等。
     * 回执＝summary（同停用口径）。
     */
    public AiplatformMaterialSummaryResponse enable(String id) {
        return toSummary(aiplatformClient.enableMaterial(id));
    }

    /**
     * 删除素材（透传 aiplatform）——治理移除、不可逆：登记行与全部块同事务移除、不动来源项目
     * （管理删除与项目删除级联正交）；无行可留、不留痕。回执＝<strong>删除前终态</strong>
     * summary（provider 契约如此，逐字镜像——确认移除了什么）。
     */
    public AiplatformMaterialSummaryResponse delete(String id) {
        return toSummary(aiplatformClient.deleteMaterial(id));
    }

    private static AiplatformMaterialSummaryResponse toSummary(AiplatformMaterialSummaryWireResponse wire) {
        return new AiplatformMaterialSummaryResponse(
                wire.id(), wire.kind(), wire.projectId(), wire.projectName(), wire.title(),
                wire.status(), wire.statusName(), wire.sunkAt(), wire.operatorId(), wire.operatorName());
    }

    private static AiplatformMaterialDetailResponse toDetail(AiplatformMaterialDetailWireResponse wire) {
        return new AiplatformMaterialDetailResponse(
                wire.id(), wire.kind(), wire.projectId(), wire.projectName(), wire.title(),
                wire.status(), wire.statusName(), wire.sunkAt(), wire.operatorId(), wire.operatorName(),
                wire.content());
    }
}
