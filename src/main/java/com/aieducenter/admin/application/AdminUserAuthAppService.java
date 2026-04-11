package com.aieducenter.admin.application;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.admin.application.dto.command.AdminUserLoginCommand;
import com.aieducenter.admin.application.dto.command.UpdatePasswordCommand;
import com.aieducenter.admin.application.dto.command.ResetPasswordCommand;
import com.aieducenter.admin.application.dto.response.AdminUserResponse;
import com.aieducenter.admin.application.dto.response.CurrentUserResponse;
import com.aieducenter.admin.application.mapper.AdminUserMapper;
import com.aieducenter.admin.application.dto.response.MenuResponse;
import com.aieducenter.admin.application.dto.response.RoleResponse;
import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.aieducenter.admin.domain.repository.AdminUserRepository;
import com.aieducenter.admin.domain.service.PasswordEncoderService;
import com.cartisan.core.exception.ApplicationException;
import com.aieducenter.admin.domain.error.AdminMessage;
import com.cartisan.security.authentication.AuthenticationService;
import com.cartisan.security.authentication.TokenInfo;

/**
 * 管理员认证应用服务。
 *
 * @since 0.1.0
 */
@Service
public class AdminUserAuthAppService {

    private static final long DEFAULT_TIMEOUT = 86400; // 24 小时
    private static final long REMEMBER_TIMEOUT = 604800; // 7 天

    private final AdminUserRepository adminUserRepository;
    private final AdminUserPermissionAppService adminPermissionAppService;
    private final AuthenticationService authenticationService;
    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoderService passwordEncoderService;

    public AdminUserAuthAppService(
            AdminUserRepository adminUserRepository,
            AdminUserPermissionAppService adminPermissionAppService,
            AuthenticationService authenticationService,
            AdminUserMapper adminUserMapper,
            PasswordEncoderService passwordEncoderService) {
        this.adminUserRepository = adminUserRepository;
        this.adminPermissionAppService = adminPermissionAppService;
        this.authenticationService = authenticationService;
        this.adminUserMapper = adminUserMapper;
        this.passwordEncoderService = passwordEncoderService;
    }

    /**
     * 管理员登录。
     */
    @Transactional
    public TokenInfo login(AdminUserLoginCommand command) {
        // 验证用户名和密码
        AdminUser adminUser = adminUserRepository.findByUsername(command.username())
                .orElseThrow(() -> new ApplicationException(AdminMessage.LOGIN_FAILED));

        if (!adminUser.isActive()) {
            throw new ApplicationException(AdminMessage.ADMIN_DISABLED);
        }

        if (!passwordEncoderService.verifyPassword(command.password(), adminUser.getPassword())) {
            throw new ApplicationException(AdminMessage.LOGIN_FAILED);
        }

        // 登录（使用框架的 AuthenticationService）
        long timeout = command.rememberMe() ? REMEMBER_TIMEOUT : DEFAULT_TIMEOUT;
        return authenticationService.login(adminUser.getId(), timeout);
    }

    /**
     * 管理员登出。
     */
    public void logout() {
        authenticationService.logout();
    }

    /**
     * 修改当前管理员密码。
     */
    @Transactional
    public void updatePassword(Long userId, UpdatePasswordCommand command) {
        AdminUser adminUser = adminUserRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(AdminMessage.ADMIN_NOT_FOUND));

        // Verify old password using domain service
        if (!passwordEncoderService.verifyPassword(command.oldPassword(), adminUser.getPassword())) {
            throw new ApplicationException(AdminMessage.PASSWORD_INCORRECT);
        }

        // Encode and update new password using domain service
        String newEncodedPassword = passwordEncoderService.encodePassword(command.newPassword());
        adminUser.changePassword(newEncodedPassword);
        adminUserRepository.save(adminUser);
    }

    /**
     * 重置管理员密码（管理员操作）。
     */
    @Transactional
    public void resetPassword(Long userId, ResetPasswordCommand command) {
        AdminUser adminUser = adminUserRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(AdminMessage.ADMIN_NOT_FOUND));

        String encodedPassword = passwordEncoderService.encodePassword(command.newPassword());
        adminUser.changePassword(encodedPassword);
        adminUserRepository.save(adminUser);
    }

    /**
     * 获取当前管理员信息。
     */
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentAdmin(Long userId) {
        AdminUser adminUser = adminUserRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(AdminMessage.ADMIN_NOT_FOUND));

        // 获取角色、菜单、权限
        List<String> roleCodes = adminPermissionAppService.getRoleCodes(userId);
        List<String> permissionCodes = adminPermissionAppService.getPermissions(userId);
        List<MenuResponse> menus = adminPermissionAppService.getMenus(userId);

        AdminUserResponse user = adminUserMapper.convert(adminUser);

        return new CurrentUserResponse(user, roleCodes, menus, permissionCodes);
    }
}
