package com.aieducenter.admin.application;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import cn.hutool.core.collection.CollUtil;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.admin.application.dto.command.AssignRolesCommand;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.application.dto.command.ResetPasswordCommand;
import com.aieducenter.admin.application.dto.command.UpdateAdminUserCommand;
import com.aieducenter.admin.application.dto.query.AdminUserQuery;
import com.aieducenter.admin.application.dto.response.AdminUserResponse;
import com.aieducenter.admin.application.mapper.AdminUserMapper;
import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.error.AdminMessage;
import com.aieducenter.admin.domain.enums.AdminUserStatus;
import com.aieducenter.admin.domain.repository.AdminRoleRepository;
import com.aieducenter.admin.domain.repository.AdminUserRepository;
import com.aieducenter.admin.domain.service.PasswordEncoderService;

import static com.cartisan.core.util.Assertions.require;
import static com.cartisan.core.util.Assertions.requirePresent;

import com.cartisan.core.exception.ApplicationException;
import com.cartisan.data.jpa.specification.ConditionSpecifications;

import com.cartisan.web.response.PageResponse;

/**
 * 管理员管理应用服务。
 *
 * @since 0.1.0
 */
@Service
public class AdminUserManagementAppService {

    private static final String PASSWORD_PATTERN = "^(?=.*[a-zA-Z])(?=.*\\d).{8,20}$";

    private final AdminUserRepository adminUserRepository;
    private final AdminRoleRepository adminRoleRepository;
    private final AdminUserAuthAppService adminUserAuthAppService;
    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoderService passwordEncoderService;

    public AdminUserManagementAppService(
            AdminUserRepository adminUserRepository,
            AdminRoleRepository adminRoleRepository,
            AdminUserAuthAppService adminUserAuthAppService,
            AdminUserMapper adminUserMapper,
            PasswordEncoderService passwordEncoderService) {
        this.adminUserRepository = adminUserRepository;
        this.adminRoleRepository = adminRoleRepository;
        this.adminUserAuthAppService = adminUserAuthAppService;
        this.adminUserMapper = adminUserMapper;
        this.passwordEncoderService = passwordEncoderService;
    }

    /**
     * 查询管理员列表（分页）。
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> findAll(AdminUserQuery query, Pageable pageable) {
        Specification<AdminUser> spec = ConditionSpecifications.fromAnnotation(query);
        Page<AdminUser> page = adminUserRepository.findAll(spec, pageable);

        return new PageResponse<>(
                adminUserMapper.convertList(page.getContent()),
                page.getTotalElements(),
                pageable.getPageNumber() + 1,
                pageable.getPageSize()
        );
    }

    /**
     * 查询管理员详情。
     */
    @Transactional(readOnly = true)
    public AdminUserResponse findById(Long id) {
        AdminUser adminUser = requirePresent(
                adminUserRepository.findById(id),
                AdminMessage.ADMIN_NOT_FOUND
        );

        return adminUserMapper.convert(adminUser);
    }

    /**
     * 创建管理员。
     */
    @Transactional
    public Long create(CreateAdminUserCommand command) {
        require(
                !adminUserRepository.existsByUsername(command.username()),
                AdminMessage.USERNAME_ALREADY_EXISTS
        );

        // Validate password strength in application service
        if (command.password() == null || !command.password().matches(PASSWORD_PATTERN)) {
            throw new ApplicationException(AdminMessage.PASSWORD_WEAK);
        }

        // Encode password in application service
        String encodedPassword = passwordEncoderService.encodePassword(command.password());

        AdminUser adminUser = new AdminUser(command.username(), encodedPassword, command.nickname());
        if (command.email() != null) {
            adminUser.setEmail(command.email());
        }
        if (command.phone() != null) {
            adminUser.setPhone(command.phone());
        }

        AdminUser saved = adminUserRepository.save(adminUser);
        return saved.getId();
    }

    /**
     * 更新管理员。
     */
    @Transactional
    public void update(Long id, UpdateAdminUserCommand command) {
        AdminUser adminUser = requirePresent(
                adminUserRepository.findById(id),
                AdminMessage.ADMIN_NOT_FOUND
        );

        if (command.nickname() != null) {
            adminUser.updateNickname(command.nickname());
        }
        if (command.email() != null) {
            adminUser.setEmail(command.email());
        }
        if (command.phone() != null) {
            adminUser.setPhone(command.phone());
        }
        if (command.avatar() != null) {
            adminUser.setAvatar(command.avatar());
        }

        adminUserRepository.save(adminUser);
    }

    /**
     * 删除管理员。
     */
    @Transactional
    public void delete(Long id) {
        // 检查是否是最后一个管理员
        if (adminUserRepository.count() <= 1) {
            throw new ApplicationException(AdminMessage.LAST_ADMIN_CANNOT_DELETE);
        }

        AdminUser adminUser = requirePresent(
                adminUserRepository.findById(id),
                AdminMessage.ADMIN_NOT_FOUND
        );

        adminUserRepository.delete(adminUser);
    }

    /**
     * 修改管理员状态。
     *
     * @param id 管理员 ID
     * @param status 状态枚举
     */
    @Transactional
    public void updateStatus(Long id, AdminUserStatus status) {
        AdminUser adminUser = requirePresent(
                adminUserRepository.findById(id),
                AdminMessage.ADMIN_NOT_FOUND
        );

        if (status == AdminUserStatus.ACTIVE) {
            adminUser.enable();
        } else {
            adminUser.disable();
        }

        adminUserRepository.save(adminUser);
    }

    /**
     * 为管理员分配角色。
     */
    @Transactional
    public void assignRoles(Long id, AssignRolesCommand command) {
        AdminUser adminUser = requirePresent(
                adminUserRepository.findById(id),
                AdminMessage.ADMIN_NOT_FOUND
        );

        // 验证所有角色 ID 存在（批量查询避免 N+1）
        Set<Long> existingRoleIds = adminRoleRepository.findAllById(command.roleIds())
                .stream()
                .map(AdminRole::getId)
                .collect(Collectors.toSet());

        if (!CollUtil.containsAll(existingRoleIds, command.roleIds())) {
            throw new ApplicationException(AdminMessage.ROLE_NOT_FOUND);
        }

        // 清除现有角色关联
        adminUser.clearRoles();

        // 添加新角色关联
        for (Long roleId : command.roleIds()) {
            adminUser.addRole(roleId);
        }

        adminUserRepository.save(adminUser);
    }

    /**
     * 重置管理员密码。
     */
    @Transactional
    public void resetPassword(Long id, ResetPasswordCommand command) {
        adminUserAuthAppService.resetPassword(id, command);
    }
}
