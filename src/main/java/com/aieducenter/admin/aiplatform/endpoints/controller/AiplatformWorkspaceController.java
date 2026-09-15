package com.aieducenter.admin.aiplatform.endpoints.controller;

import com.aieducenter.admin.aiplatform.application.AiplatformWorkspaceAppService;
import com.aieducenter.admin.aiplatform.application.dto.query.AiplatformWorkspaceQuery;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformWorkspaceDetailResponse;
import com.aieducenter.admin.aiplatform.application.dto.response.AiplatformWorkspaceSummaryResponse;
import com.aieducenter.admin.constants.AdminScopes;
import com.cartisan.security.annotation.RequireAuth;
import com.cartisan.security.annotation.RequirePermission;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * AI 平台沙箱控制器——BFF 观测面（清单/详情）＋四干预动作（唤醒/休眠/重建/封存），issue #66。
 *
 * <p>北向 {@code /api/admin/aiplatform/workspaces/**} 逐字镜像 aiplatform
 * {@code /api/backoffice/workspaces/**}（#173 观测 + #174 动作）——查询参数名、缺省值、分页基准
 * （page 1-based，缺省 1/20）与 provider controller 同形；四写操作均无请求体、回执＝详情 DTO
 * （动作后的观测详情）。错误经 {@code AiplatformUpstreamErrorAdvice} 原样透传（如 404 WSP_001→1001、
 * 400 WSP_014→1014、400 WSP_009→1009、409 WSP_015→1015、409 WSP_017→1017）。</p>
 *
 * <p>权限码（spec #62）：读 {@code workspace:read} 一码；四动作各自独立写码
 * （{@code workspace:wake|hibernate|rebuild|seal}）——最小授权（如只给算力回收员配 hibernate
 * 而不给重建）。操作者身份由框架 OpenApiClient 自动透传 {@code X-User-Id/X-User-Name} 出站头
 * （identity 同款），provider 落痕动作行。</p>
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/admin/aiplatform/workspaces")
@Validated
@Tag(name = "Admin Aiplatform Workspace", description = "AI 平台 · 沙箱")
public class AiplatformWorkspaceController {

    private final AiplatformWorkspaceAppService workspaceAppService;

    public AiplatformWorkspaceController(AiplatformWorkspaceAppService workspaceAppService) {
        this.workspaceAppService = workspaceAppService;
    }

    // 不挂 @ErrorCodes：框架 ErrorCodesValidator 要求码在应用自身 CodeMessageRegistry 注册，admin 不镜像
    // provider 错误码目录（忠实透传、零加戏——spec #62）；错误码以 description 文档化（WSP_014 等）。
    @GetMapping
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:workspace:read",
            name = "AI 平台 / 沙箱查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "沙箱清单（期望态/实态过滤，分页 1-based）——透传 aiplatform",
            description = "存储观测工作清单：新沙箱在前（TSID 倒序）。两过滤维度均可缺省"
                    + "（缺省＝全量）、可组合：① desired 期望态<strong>单选</strong>，Integer code"
                    + "（1=运行 2=休眠 3=封存，DB 意图侧）；② actual 容器实态<strong>单选</strong>，"
                    + "Integer code（1=运行中 2=已停止 3=无容器 4=未知——docker 现场探查，实态不落库，"
                    + "过滤在探查后内存完成，total 如实＝筛后计数；「期望运行而实态无容器」即漂移行，"
                    + "desired=1&actual=3 即捞漂移清单）。每行＝项目引用（无所属项目为 null）＋期望态/"
                    + "实态两列＋last-touch＋卷大小（旁路容器 du 全卷、含可重建缓存，字节——封存容缺"
                    + " null、探查失败亦 null）＋封存信息（时刻/包大小）。page 1 基（缺省 1）、size 缺省"
                    + " 20（provider 上界 100），clamp 由 provider 执行、回显 provider 回报值。实态与"
                    + "卷大小逐行现场探查（docker 子进程），页越大越慢——观测页不必贪大。绑定裁决两段："
                    + "非整数 code/非法分页值在本服务绑定层 400（框架信封）；数值但未知的 code（如 7）"
                    + "由 provider 裁决 400 WSP_014（数字业务码 1014）透传。")
    public ApiResponse<PageResponse<AiplatformWorkspaceSummaryResponse>> workspaces(
            @RequestParam(required = false) Integer desired,
            @RequestParam(required = false) Integer actual,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(workspaceAppService.list(
                new AiplatformWorkspaceQuery(desired, actual), page, size));
    }

    @GetMapping("/{id}")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:workspace:read",
            name = "AI 平台 / 沙箱查看",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "沙箱详情（全量字段＋所属项目引用＋资源观测）——透传 aiplatform",
            description = "清单行超集：另带置备失败原因（FAILED 态排障）、封存包寻址键"
                    + "（archivePath）、审计时间列（createdAt/updatedAt）与中间件资源清单"
                    + "（resources：kind 1=PostgreSQL 2=Redis + 容器名 + 连接串原文，容器内回环形态"
                    + "——全量如实呈现，机机签名面无脱敏）。期望态/实态/卷大小同清单口径（现场探查、"
                    + "封存容缺）。四枚举 kind/status/desiredState/containerState 均配 Integer code +"
                    + " *Name。所属项目引用软引用容缺（null 呈现）——工作区先于项目存在。"
                    + "工作区不存在（含畸形 id）404 WSP_001（数字业务码 1001）。")
    public ApiResponse<AiplatformWorkspaceDetailResponse> detail(@PathVariable String id) {
        return ApiResponse.ok(workspaceAppService.getDetail(id));
    }

    @PostMapping("/{id}/wake")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:workspace:wake",
            name = "AI 平台 / 沙箱唤醒",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "唤醒（等就绪）——透传 aiplatform",
            description = "收敛到 READY＋（已生成项目）应用在服，同步等结果：健康即回；容器缺失/"
                    + "被杀（漂移型）走幂等重建（卷保留、数据不动）；封存态走深度唤醒（解包回卷＋"
                    + "依赖重装，分钟级）。run 在途不受限（唤醒不动数据面）。响应＝动作后的观测详情"
                    + "（新事实，与详情同形全量字段＋资源观测列表）。X-User-Id/X-User-Name 透传头自动"
                    + "落痕动作行（缺头落空）。工作区不存在 404 WSP_001（1001）；非 DEV 400 WSP_007"
                    + "（1007）；封存包不可读（深度唤醒保持封存态）404 WSP_016（1016）；置备等待超时"
                    + " 500 WSP_011（1011）。")
    public ApiResponse<AiplatformWorkspaceDetailResponse> wake(@PathVariable String id) {
        return ApiResponse.ok(workspaceAppService.wake(id));
    }

    @PostMapping("/{id}/hibernate")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:workspace:hibernate",
            name = "AI 平台 / 沙箱休眠",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "强制休眠（立即删容器保卷）——透传 aiplatform",
            description = "管理员的即时止损口：删容器保卷、期望态置休眠——闲置计时的手动直达"
                    + "（不等闲置阈值）。已休眠＝幂等成功（补删残留容器）；封存态拒 400 WSP_009"
                    + "（1009，卷已删，先唤醒）；置备在途拒 WSP_009；run 在途拒 409 WSP_015（1015，"
                    + "管理员操作资源面，不打断用户正在进行的生成——要处置先取消 run 或等收口）；"
                    + "收敛任务在途（触碰自愈/扫描封存）409 WSP_017（1017）。响应＝动作后的观测详情。"
                    + "操作者透传头落痕同唤醒。")
    public ApiResponse<AiplatformWorkspaceDetailResponse> hibernate(@PathVariable String id) {
        return ApiResponse.ok(workspaceAppService.hibernate(id));
    }

    @PostMapping("/{id}/rebuild")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:workspace:rebuild",
            name = "AI 平台 / 沙箱重建",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "强制重建（rm＋幂等重建）——透传 aiplatform",
            description = "「预览死了」型事故的标准化处置（替代手工 docker 拉）：在跑但坏了的容器"
                    + "也杀（卷保留、数据不动），随后走唤醒内核同一重建路径收敛回 READY（已生成项目"
                    + "拉起应用）。封存态拒 400 WSP_009（1009，数据只在包里，空卷重建＝掩埋数据丢失"
                    + "——先唤醒）；置备在途拒 WSP_009；run 在途拒 409 WSP_015（1015）；收敛任务在途"
                    + " 409 WSP_017（1017）；重建重试上限落 FAILED 时 500 WSP_010（1010，可再触发）。"
                    + "响应＝动作后的观测详情。操作者透传头落痕同唤醒。")
    public ApiResponse<AiplatformWorkspaceDetailResponse> rebuild(@PathVariable String id) {
        return ApiResponse.ok(workspaceAppService.rebuild(id));
    }

    @PostMapping("/{id}/seal")
    @RequireAuth
    @RequirePermission(
            value = "admin:aiplatform:workspace:seal",
            name = "AI 平台 / 沙箱封存",
            scope = AdminScopes.ADMIN
    )
    @Operation(summary = "封存（产物同自动封存）——透传 aiplatform",
            description = "动作序同闲置满期的自动封存：删容器 → 整卷打包（仅排可重建缓存，数据库"
                    + "与机密随包）落平台存储 → 期望态置封存＋包元数据 → 删卷（失败由扫描下轮收敛）。"
                    + "RUNNING 起点可用（即时深回收，不用先等休眠满期）；重复封存拒 400 WSP_009"
                    + "（1009，走「唤醒→休眠→封存」周期）；置备在途拒 WSP_009；run 在途拒 409"
                    + " WSP_015（1015，卷正被 run 读写时打包＝半程数据）；收敛任务在途 409 WSP_017"
                    + "（1017）。响应＝动作后的观测详情（含封存包元数据）。操作者透传头落痕同唤醒。")
    public ApiResponse<AiplatformWorkspaceDetailResponse> seal(@PathVariable String id) {
        return ApiResponse.ok(workspaceAppService.seal(id));
    }
}
