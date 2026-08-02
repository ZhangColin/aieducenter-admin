package com.aieducenter.admin.application;

import java.util.*;
import java.util.stream.Collectors;

import cn.hutool.core.collection.CollUtil;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.aieducenter.admin.domain.enums.AdminRoleStatus;
import com.aieducenter.admin.domain.repository.AdminRoleRepository;
import com.aieducenter.admin.domain.repository.AdminUserRepository;
import com.aieducenter.admin.application.dto.response.MenuResponse;
import com.aieducenter.admin.application.dto.response.MyMenusResponse;

import static com.cartisan.core.util.Assertions.requirePresent;

/**
 * 管理员权限应用服务。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>供 SaToken 接口调用，返回管理员的权限、角色、菜单</li>
 *   <li>通过领域模型聚合数据，避免跨表 SQL 查询</li>
 * </ul>
 *
 * <p>汇总聚合语义：用户权限/角色码/导航 = 其全部<b>启用</b>角色的并集——禁用角色
 * （{@code AdminRoleStatus.DISABLED}）在任何汇总聚合中视为不存在（CONTEXT.md「RBAC」条目
 * 决策①，issue #21；各聚合方法同路径一次修齐）。超管不受影响：{@code SUPER_ADMIN} 角色
 * 自身不可禁用（REQ-10 聚合守卫）。</p>
 *
 * @since 0.1.0
 */
@Service
public class AdminUserPermissionAppService {

    private final AdminUserRepository adminUserRepository;
    private final AdminRoleRepository adminRoleRepository;
    private final MenuManagementAppService menuManagementAppService;

    public AdminUserPermissionAppService(
            AdminUserRepository adminUserRepository,
            AdminRoleRepository adminRoleRepository,
            MenuManagementAppService menuManagementAppService) {
        this.adminUserRepository = adminUserRepository;
        this.adminRoleRepository = adminRoleRepository;
        this.menuManagementAppService = menuManagementAppService;
    }

    /**
     * 获取管理员的权限编码列表。
     *
     * <p>超管与非超管走同一聚合路径——超管的授权放行由框架 {@code AuthorizationBypassResolver} 在拦截器层处理
     * （见 {@code SaTokenConfig#superAdminAuthorizationBypassResolver}），本方法不再为绕历史上的 Bug ②
     * 而对超管特判返回空。</p>
     *
     * @param adminId 管理员 ID
     * @return 权限编码列表
     */
    @Transactional(readOnly = true)
    public List<String> getPermissions(Long adminId) {
        AdminUser adminUser = requirePresent(
                adminUserRepository.findById(adminId)
        );

        // 通过领域模型聚合权限编码
        Set<Long> roleIds = adminUser.getRoleIds();
        if (roleIds.isEmpty()) {
            return List.of();
        }

        return enabledRoles(roleIds).stream()
                .flatMap(role -> role.getPermissionCodes().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 获取管理员的角色编码列表。
     *
     * <p>超管与非超管走同一聚合路径（不再特判返回空，见 {@link #getPermissions}）。</p>
     *
     * @param adminId 管理员 ID
     * @return 角色编码列表
     */
    @Transactional(readOnly = true)
    public List<String> getRoleCodes(Long adminId) {
        AdminUser adminUser = requirePresent(
                adminUserRepository.findById(adminId)
        );

        // 通过领域模型聚合角色编码
        Set<Long> roleIds = adminUser.getRoleIds();
        if (roleIds.isEmpty()) {
            return List.of();
        }

        return enabledRoles(roleIds).stream()
                .map(AdminRole::getCode)
                .collect(Collectors.toList());
    }

    /**
     * 检查是否为超级管理员。
     */
    public boolean isSuperAdmin(Long adminId) {
        return adminUserRepository.hasRole(adminId, AdminRole.SUPER_ADMIN_CODE);
    }

    /**
     * 获取「我的导航」（{@code GET /menus/my}，REQ-13-T2）：{@code {home, menus}}。
     *
     * <ul>
     *   <li>{@code menus}：全部<b>启用</b>角色并集裁剪的可见菜单树，且只含启用菜单
     *     （禁用叶子不下发、禁用 directory 整棵子树不下发——status 过滤下沉在组装层，
     *     见 {@link MenuTreeAssembler#assembleVisible}）。超管不受角色裁剪、看到全量启用菜单
     *     （status 过滤对超管同样生效）。</li>
     *   <li>{@code home}：全部启用角色按 {@code (sortOrder 升, id 升)} 取第一个非空白
     *     （非 null 且 trim 后非空）的 home；全空 → null。超管同规则。</li>
     * </ul>
     *
     * @param adminId 管理员 ID
     * @return 我的导航（home + 菜单树）
     */
    @Transactional(readOnly = true)
    public MyMenusResponse getMyMenus(Long adminId) {
        AdminUser adminUser = requirePresent(
                adminUserRepository.findById(adminId)
        );

        Set<Long> roleIds = adminUser.getRoleIds();
        List<AdminRole> roles = roleIds.isEmpty() ? List.of() : enabledRoles(roleIds);

        String home = firstNonBlankHome(roles);

        List<MenuResponse> menus;
        if (isSuperAdmin(adminId)) {
            // 超管：不角色裁剪的全量启用菜单（与管理面 findTree(null) 语义分叉，见 assembleVisible）
            menus = menuManagementAppService.findVisibleTree(null);
        } else {
            Set<Long> menuIds = unionMenuIds(roles);
            menus = menuIds.isEmpty() ? List.of() : menuManagementAppService.findVisibleTree(menuIds);
        }

        return new MyMenusResponse(home, menus);
    }

    /** 角色列表的菜单 id 并集（{@link #getMyMenus} 使用）。 */
    private static Set<Long> unionMenuIds(List<AdminRole> roles) {
        Set<Long> menuIds = CollUtil.newHashSet();
        for (AdminRole role : roles) {
            menuIds.addAll(role.getMenuIds());
        }
        return menuIds;
    }

    /**
     * 取第一个非空白 home：启用角色按 {@code (sortOrder 升, id 升)} 排序，第一个
     * 非 null 且 trim 后非空的 home 原样返回（trim 仅作空白判定）；全空 → null（REQ-13-T2 已定②）。
     */
    private static String firstNonBlankHome(List<AdminRole> enabledRoles) {
        return enabledRoles.stream()
                .sorted(Comparator.comparing(AdminRole::getSortOrder).thenComparing(AdminRole::getId))
                .map(AdminRole::getHome)
                .filter(home -> home != null && !home.trim().isEmpty())
                .findFirst()
                .orElse(null);
    }

    /**
     * 批量取用户的<b>启用</b>角色——禁用角色在汇总聚合中视为不存在（issue #21）。
     * 三个聚合方法（角色码/权限码/菜单）经此单一入口取数，保证语义一致。
     */
    private List<AdminRole> enabledRoles(Set<Long> roleIds) {
        return adminRoleRepository.findByIdInAndStatus(roleIds, AdminRoleStatus.ENABLED);
    }
}
