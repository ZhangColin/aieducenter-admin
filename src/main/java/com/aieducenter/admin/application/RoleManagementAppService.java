package com.aieducenter.admin.application;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import cn.hutool.core.collection.CollUtil;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.admin.domain.aggregate.AdminMenu;
import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.repository.AdminRoleRepository;
import com.aieducenter.admin.domain.repository.AdminMenuRepository;
import com.aieducenter.admin.domain.error.AdminMessage;
import com.aieducenter.admin.application.dto.command.AssignMenusCommand;
import com.aieducenter.admin.application.dto.command.AssignPermissionsCommand;
import com.aieducenter.admin.application.dto.command.CreateRoleCommand;
import com.aieducenter.admin.application.dto.command.UpdateRoleCommand;
import com.aieducenter.admin.application.dto.query.AdminRoleQuery;
import com.aieducenter.admin.application.dto.response.RoleResponse;
import com.aieducenter.admin.application.mapper.AdminRoleMapper;
import com.aieducenter.admin.constants.AdminScopes;
import static com.cartisan.core.util.Assertions.requirePresent;

import com.cartisan.core.exception.DomainException;
import com.cartisan.data.jpa.specification.ConditionSpecifications;
import com.cartisan.security.permission.Permission;
import com.cartisan.security.permission.PermissionScanner;
import com.cartisan.web.response.PageResponse;

/**
 * 角色管理应用服务。
 */
@Service
public class RoleManagementAppService {

    private final AdminRoleRepository roleRepository;
    private final AdminMenuRepository menuRepository;
    private final AdminRoleMapper adminRoleMapper;
    private final PermissionScanner permissionScanner;

    public RoleManagementAppService(AdminRoleRepository roleRepository,
                                     AdminMenuRepository menuRepository,
                                     AdminRoleMapper adminRoleMapper,
                                     PermissionScanner permissionScanner) {
        this.roleRepository = roleRepository;
        this.menuRepository = menuRepository;
        this.adminRoleMapper = adminRoleMapper;
        this.permissionScanner = permissionScanner;
    }

    /**
     * 查询角色列表（分页）。
     */
    @Transactional(readOnly = true)
    public PageResponse<RoleResponse> findAll(AdminRoleQuery query, Pageable pageable) {
        Specification<AdminRole> spec = ConditionSpecifications.fromAnnotation(query);
        Page<AdminRole> page = roleRepository.findAll(spec, pageable);

        return new PageResponse<>(
                adminRoleMapper.convertList(page.getContent()),
                page.getTotalElements(),
                pageable.getPageNumber() + 1,
                pageable.getPageSize()
        );
    }

    /**
     * 创建角色。
     */
    @Transactional
    public Long create(CreateRoleCommand command) {
        // 检查 code 唯一性
        if (roleRepository.findByCode(command.code()).isPresent()) {
            throw new DomainException(AdminMessage.ROLE_CODE_ALREADY_EXISTS);
        }

        AdminRole role = new AdminRole(command.name(), command.code(), command.description(), command.sortOrder());
        AdminRole saved = roleRepository.save(role);
        return saved.getId();
    }

    /**
     * 修改角色。
     */
    @Transactional
    public void update(Long id, UpdateRoleCommand command) {
        AdminRole role = requirePresent(
                roleRepository.findById(id),
                AdminMessage.ROLE_NOT_FOUND
        );

        // 如果修改 code，检查唯一性
        if (!Objects.equals(role.getCode(), command.code())) {
            roleRepository.findByCode(command.code()).ifPresent(existing -> {
                throw new DomainException(AdminMessage.ROLE_CODE_ALREADY_EXISTS);
            });
        }

        role.setName(command.name());
        role.setCode(command.code());
        role.setDescription(command.description());
        role.setSortOrder(command.sortOrder());
        roleRepository.save(role);
    }

    /**
     * 删除角色。
     */
    @Transactional
    public void delete(Long id) {
        AdminRole role = requirePresent(
                roleRepository.findById(id),
                AdminMessage.ROLE_NOT_FOUND
        );

        // 超级管理员角色不能删除
        if (role.isSuperAdmin()) {
            throw new DomainException(AdminMessage.SUPER_ADMIN_CANNOT_DELETE);
        }

        // 检查是否有管理员使用该角色
        if (roleRepository.isUsedByAnyAdmin(id)) {
            throw new DomainException(AdminMessage.ROLE_IN_USE);
        }

        roleRepository.delete(role);
    }

    /**
     * 为角色分配菜单。
     */
    @Transactional
    public void assignMenus(Long roleId, AssignMenusCommand command) {
        // 验证角色存在
        AdminRole role = requirePresent(
                roleRepository.findById(roleId),
                AdminMessage.ROLE_NOT_FOUND
        );

        // 验证所有菜单 ID 存在（批量查询避免 N+1）
        Set<Long> existingMenuIds = menuRepository.findAllById(command.menuIds())
                .stream()
                .map(AdminMenu::getId)
                .collect(Collectors.toSet());

        if (!CollUtil.containsAll(existingMenuIds, command.menuIds())) {
            throw new DomainException(AdminMessage.MENU_NOT_FOUND);
        }

        // 清除现有菜单并添加新菜单
        role.clearMenus();
        for (Long menuId : command.menuIds()) {
            role.addMenu(menuId);
        }
        roleRepository.save(role);
    }

    /**
     * 为角色分配权限。
     */
    @Transactional
    public void assignPermissions(Long roleId, AssignPermissionsCommand command) {
        // 验证角色存在
        AdminRole role = requirePresent(
                roleRepository.findById(roleId),
                AdminMessage.ROLE_NOT_FOUND
        );

        // 验证权限 codes 有效性（通过 PermissionScanner 扫描代码中定义的权限）
        Set<String> validPermissionCodes = permissionScanner.scanByScope(AdminScopes.ADMIN).stream()
                .map(Permission::code)
                .collect(Collectors.toSet());

        for (String permissionCode : command.permissionCodes()) {
            if (!validPermissionCodes.contains(permissionCode)) {
                throw new DomainException(AdminMessage.PERMISSION_NOT_FOUND);
            }
        }

        // 清除现有权限并添加新权限
        role.clearPermissions();
        for (String permissionCode : command.permissionCodes()) {
            role.addPermission(permissionCode, null);
        }
        roleRepository.save(role);
    }

    /**
     * 根据 ID 获取角色详情。
     */
    public RoleResponse findById(Long id) {
        AdminRole role = requirePresent(
                roleRepository.findById(id),
                AdminMessage.ROLE_NOT_FOUND
        );
        return adminRoleMapper.convert(role);
    }
}
