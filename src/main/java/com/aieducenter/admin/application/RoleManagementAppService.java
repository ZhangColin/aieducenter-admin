package com.aieducenter.admin.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;

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
import com.aieducenter.admin.application.dto.response.RoleOptionResponse;
import com.aieducenter.admin.application.mapper.AdminRoleMapper;
import com.aieducenter.admin.domain.enums.AdminRoleStatus;
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
        role.setHome(command.home());
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
        role.setHome(command.home());
        roleRepository.save(role);
    }

    /**
     * 删除角色。
     *
     * <p>SUPER_ADMIN 不可删守卫在聚合 {@link AdminRole#markAsDeleted()}（单一执行点，仿
     * {@code AdminUser} 破窗号 guard）。但 SUPER_ADMIN 角色恒被破窗号使用，{@code ROLE_IN_USE}
     * 会先命中而遮蔽 {@code SUPER_ADMIN_CANNOT_DELETE}，故此处仅对非超管角色查 in-use；
     * 超管角色放行至 {@code repository.delete} → 聚合守卫抛正确错误码。</p>
     */
    @Transactional
    public void delete(Long id) {
        AdminRole role = requirePresent(
                roleRepository.findById(id),
                AdminMessage.ROLE_NOT_FOUND
        );

        if (!role.isSuperAdmin() && roleRepository.isUsedByAnyAdmin(id)) {
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

        // 空集 = 清空（去掉 @NotEmpty 后，null 归一为空集，clear-then-add 支持清空）
        List<Long> menuIds = command.menuIds() == null ? List.of() : command.menuIds();

        // 验证所有菜单 ID 存在（批量查询避免 N+1）
        Set<Long> existingMenuIds = menuRepository.findAllById(menuIds)
                .stream()
                .map(AdminMenu::getId)
                .collect(Collectors.toSet());

        if (!CollUtil.containsAll(existingMenuIds, menuIds)) {
            throw new DomainException(AdminMessage.MENU_NOT_FOUND);
        }

        // 清除现有菜单并添加新菜单
        role.clearMenus();
        for (Long menuId : menuIds) {
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

        // 空集 = 清空（去掉 @NotEmpty 后，null 归一为空集）
        List<String> permissionCodes = command.permissionCodes() == null ? List.of() : command.permissionCodes();

        // 验证权限 codes 有效性（通过 PermissionScanner 扫描代码中定义的权限）
        // 同时建立 code → name 映射：sys_admin_role_permissions.permission_name 为 NOT NULL，
        // 落库时必须回填权限名（见 REQ-7 Bug ②），不能再传 null。用手写 put 而非
        // Collectors.toMap，以容忍个别权限缺名（toMap 遇 null value 会抛 NPE）。
        Map<String, String> permissionNameByCode = MapUtil.newHashMap();
        permissionScanner.scanByScope(AdminScopes.ADMIN)
                .forEach(p -> permissionNameByCode.put(p.code(), p.name()));

        for (String permissionCode : permissionCodes) {
            if (!permissionNameByCode.containsKey(permissionCode)) {
                throw new DomainException(AdminMessage.PERMISSION_NOT_FOUND);
            }
        }

        // 清除现有权限并添加新权限
        role.clearPermissions();
        for (String permissionCode : permissionCodes) {
            role.addPermission(permissionCode, permissionNameByCode.get(permissionCode));
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

    /**
     * 修改角色状态（启用/禁用）。
     *
     * <p>SUPER_ADMIN 角色不可禁——守卫在聚合 {@link AdminRole#disable()}。</p>
     *
     * @param id     角色 ID
     * @param status 目标状态
     */
    @Transactional
    public void updateStatus(Long id, AdminRoleStatus status) {
        AdminRole role = requirePresent(
                roleRepository.findById(id),
                AdminMessage.ROLE_NOT_FOUND
        );

        if (status == AdminRoleStatus.ENABLED) {
            role.enable();
        } else {
            role.disable();
        }

        roleRepository.save(role);
    }

    /**
     * 轻量角色字典（{@code GET /roles/all}）——仅启用、不分页、精简 {id,name,code}，供下拉选择。
     */
    @Transactional(readOnly = true)
    public List<RoleOptionResponse> listEnabledOptions() {
        return roleRepository.findByStatusAndDeletedFalse(AdminRoleStatus.ENABLED).stream()
                .map(r -> new RoleOptionResponse(r.getId(), r.getName(), r.getCode()))
                .toList();
    }
}
