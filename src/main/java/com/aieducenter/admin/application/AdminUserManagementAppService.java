package com.aieducenter.admin.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     *
     * <p>每行内联已分配角色摘要（裁剪投影 {@code {id, name, code}}）。角色按本页全部用户的角色 ID
     * <b>批量查一次</b>（{@link AdminRoleRepository#findByIdIn(Collection)}），再在内存按用户分组——
     * 无 N+1。关联指向的角色若已物理删除（ADR-0005）则不回显，与 {@link #findById} 同语义。</p>
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> findAll(AdminUserQuery query, Pageable pageable) {
        Specification<AdminUser> spec = ConditionSpecifications.fromAnnotation(query);
        Page<AdminUser> page = adminUserRepository.findAll(spec, pageable);
        List<AdminUser> users = page.getContent();

        // 批量取本页全部用户所挂角色（一次查询，无 N+1）
        Set<Long> roleIds = users.stream()
                .flatMap(u -> u.getRoleIds().stream())
                .collect(Collectors.toSet());
        Map<Long, AdminRole> roleById = roleIds.isEmpty()
                ? Map.of()
                : adminRoleRepository.findByIdIn(roleIds).stream()
                        .collect(Collectors.toMap(AdminRole::getId, role -> role));

        List<AdminUserResponse> responses = users.stream()
                .map(u -> adminUserMapper.convertWithRoles(u, rolesFor(u, roleById)))
                .collect(Collectors.toList());

        return new PageResponse<>(
                responses,
                page.getTotalElements(),
                pageable.getPageNumber() + 1,
                pageable.getPageSize()
        );
    }

    /**
     * 从批量加载的角色字典中取出该用户的存活角色（关联指向的角色已物理删除则跳过）。
     */
    private static List<AdminRole> rolesFor(AdminUser user, Map<Long, AdminRole> roleById) {
        return user.getRoleIds().stream()
                .map(roleById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 查询管理员详情。
     *
     * <p>携带当前已分配角色摘要（{@code roles}）供「分配角色」回显：关联指向的角色若已物理删除
     * （ADR-0005）则不回显，批量查询一次完成，无 N+1。</p>
     */
    @Transactional(readOnly = true)
    public AdminUserResponse findById(Long id) {
        AdminUser adminUser = requirePresent(
                adminUserRepository.findById(id),
                AdminMessage.ADMIN_NOT_FOUND
        );

        Set<Long> roleIds = adminUser.getRoleIds();
        List<AdminRole> roles = roleIds.isEmpty()
                ? List.of()
                : adminRoleRepository.findByIdIn(roleIds);

        return adminUserMapper.convertWithRoles(adminUser, roles);
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
        if (command.gender() != null) {
            adminUser.setGender(command.gender());
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
        if (command.gender() != null) {
            adminUser.setGender(command.gender());
        }

        adminUserRepository.save(adminUser);
    }

    /**
     * 删除管理员（物理删除，ADR-0005）。
     *
     * <p>破窗账号（保留 ID = 1）的不可删守卫由聚合 {@link AdminUser#requireDeletable()} 承担，
     * 删除前显式调用——命中即抛领域错误，删除不会发生。删除用户时其 {@code sys_admin_user_roles}
     * 关联行由 {@code cascade = ALL + orphanRemoval} 同事务级联清除（生产库另有 FK ON DELETE CASCADE 兜底）。</p>
     */
    @Transactional
    public void delete(Long id) {
        AdminUser adminUser = requirePresent(
                adminUserRepository.findById(id),
                AdminMessage.ADMIN_NOT_FOUND
        );

        adminUser.requireDeletable();
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

        // 空集 = 清空（去掉 @NotEmpty 后，null 归一为空集，clear-then-add 支持清空）
        List<Long> roleIds = command.roleIds() == null ? List.of() : command.roleIds();

        // 验证所有角色 ID 存在（批量查询避免 N+1）
        Set<Long> existingRoleIds = adminRoleRepository.findAllById(roleIds)
                .stream()
                .map(AdminRole::getId)
                .collect(Collectors.toSet());

        if (!CollUtil.containsAll(existingRoleIds, roleIds)) {
            throw new ApplicationException(AdminMessage.ROLE_NOT_FOUND);
        }

        // 破窗号必须保留 SUPER_ADMIN 角色——守住"总能以全权救援"的韧性目标
        // （不可删/不可禁守卫的逻辑补全：否则能登入却无救援能力）。授权（谁是超管）仍走角色。
        if (adminUser.isBreakGlass()) {
            Long superAdminRoleId = adminRoleRepository.findByCode(AdminRole.SUPER_ADMIN_CODE)
                    .map(AdminRole::getId)
                    .orElse(null);
            require(roleIds.contains(superAdminRoleId),
                    AdminMessage.BREAK_GLASS_SUPER_ADMIN_REQUIRED);
        }

        // 清除现有角色关联
        adminUser.clearRoles();

        // 添加新角色关联
        for (Long roleId : roleIds) {
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
