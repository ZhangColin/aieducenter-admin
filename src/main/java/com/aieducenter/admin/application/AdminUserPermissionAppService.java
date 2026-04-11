package com.aieducenter.admin.application;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.aggregate.AdminUser;
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
     * @param adminId 管理员 ID
     * @return 权限编码列表
     */
    public List<String> getPermissions(Long adminId) {
        // 超级管理员返回空列表（由 SaToken 拦截器直接放行）
        if (adminUserRepository.hasRole(adminId, AdminRole.SUPER_ADMIN_CODE)) {
            return List.of();
        }

        AdminUser adminUser = requirePresent(
                adminUserRepository.findById(adminId)
        );

        // 通过领域模型聚合权限编码
        Set<Long> roleIds = adminUser.getRoleIds();
        if (roleIds.isEmpty()) {
            return List.of();
        }

        return adminRoleRepository.findAllById(roleIds).stream()
                .flatMap(role -> role.getPermissionCodes().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 获取管理员的角色编码列表。
     *
     * @param adminId 管理员 ID
     * @return 角色编码列表
     */
    public List<String> getRoleCodes(Long adminId) {
        // 超级管理员返回空列表（由 SaToken 拦截器直接放行）
        if (adminUserRepository.hasRole(adminId, AdminRole.SUPER_ADMIN_CODE)) {
            return List.of();
        }

        AdminUser adminUser = requirePresent(
                adminUserRepository.findById(adminId)
        );

        // 通过领域模型聚合角色编码
        Set<Long> roleIds = adminUser.getRoleIds();
        if (roleIds.isEmpty()) {
            return List.of();
        }

        return adminRoleRepository.findAllById(roleIds).stream()
                .map(AdminRole::getCode)
                .collect(Collectors.toList());
    }

    /**
     * 获取管理员的菜单列表（树形）。
     *
     * @param adminId 管理员 ID
     * @return 菜单 DTO 列表
     */
    public List<MenuResponse> getMenus(Long adminId) {
        // 超级管理员返回所有菜单
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
        for (AdminRole role : adminRoleRepository.findAllById(roleIds)) {
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
}
