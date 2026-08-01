package com.aieducenter.admin.application;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.aieducenter.admin.domain.enums.AdminRoleStatus;
import com.aieducenter.admin.domain.repository.AdminRoleRepository;
import com.aieducenter.admin.domain.repository.AdminUserRepository;
import com.aieducenter.admin.application.dto.response.MenuResponse;

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
 * <p>汇总聚合语义：用户菜单/权限/角色码 = 其全部<b>启用</b>角色的并集——禁用角色
 * （{@code AdminRoleStatus.DISABLED}）在任何汇总聚合中视为不存在（CONTEXT.md「RBAC」条目
 * 决策①，issue #21；{@code getRoleCodes}/{@code getPermissions}/{@code getMenus} 三方法
 * 同路径一次修齐）。超管不受影响：{@code SUPER_ADMIN} 角色自身不可禁用（REQ-10 聚合守卫）。</p>
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
     * 获取管理员的菜单列表（树形）。
     *
     * @param adminId 管理员 ID
     * @return 菜单 DTO 列表
     */
    @Transactional(readOnly = true)
    public List<MenuResponse> getMenus(Long adminId) {
        // 超管可见全部菜单（展示规则，与授权 bypass 无关——超管不靠角色-菜单绑定决定可见菜单）
        if (adminUserRepository.hasRole(adminId, AdminRole.SUPER_ADMIN_CODE)) {
            return menuManagementAppService.findTree(null);
        }

        AdminUser adminUser = requirePresent(
                adminUserRepository.findById(adminId)
        );

        // 通过领域模型聚合菜单 ID
        Set<Long> roleIds = adminUser.getRoleIds();
        if (roleIds.isEmpty()) {
            return List.of();
        }

        Set<Long> menuIds = new HashSet<>();
        for (AdminRole role : enabledRoles(roleIds)) {
            menuIds.addAll(role.getMenuIds());
        }

        if (menuIds.isEmpty()) {
            return List.of();
        }

        return menuManagementAppService.findTree(menuIds);
    }

    /**
     * 检查是否为超级管理员。
     */
    public boolean isSuperAdmin(Long adminId) {
        return adminUserRepository.hasRole(adminId, AdminRole.SUPER_ADMIN_CODE);
    }

    /**
     * 批量取用户的<b>启用</b>角色——禁用角色在汇总聚合中视为不存在（issue #21）。
     * 三个聚合方法（角色码/权限码/菜单）经此单一入口取数，保证语义一致。
     */
    private List<AdminRole> enabledRoles(Set<Long> roleIds) {
        return adminRoleRepository.findByIdInAndStatusAndDeletedFalse(roleIds, AdminRoleStatus.ENABLED);
    }
}
