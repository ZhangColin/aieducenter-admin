package com.aieducenter.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aieducenter.admin.application.dto.command.AdminUserLoginCommand;
import com.aieducenter.admin.application.dto.command.ResetPasswordCommand;
import com.aieducenter.admin.application.dto.command.UpdatePasswordCommand;
import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.aieducenter.admin.domain.error.AdminMessage;
import com.aieducenter.admin.domain.repository.AdminUserRepository;
import com.cartisan.core.exception.ApplicationException;
import com.cartisan.security.authentication.AuthenticationService;
import com.aieducenter.admin.application.mapper.AdminUserMapper;
import com.cartisan.security.authentication.TokenInfo;
import com.aieducenter.admin.domain.service.PasswordEncoderService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminUserAuthAppServiceTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private AdminUserPermissionAppService adminPermissionAppService;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private AdminUserMapper adminUserMapper;

    @Mock
    private PasswordEncoderService passwordEncoderService;

    private static final BCryptPasswordEncoder testEncoder = new BCryptPasswordEncoder(10);

    private AdminUserAuthAppService adminAuthAppService;

    @BeforeEach
    void setUp() {
        adminAuthAppService = new AdminUserAuthAppService(
            adminUserRepository,
            adminPermissionAppService,
            authenticationService,
            adminUserMapper,
            passwordEncoderService
        );
    }

    // ========== login tests ==========

    @Test
    void given_valid_credentials_when_login_then_return_token_info() {
        // Given
        String plainPassword = "Test1234";
        String encodedPassword = testEncoder.encode(plainPassword);
        AdminUser adminUser = new AdminUser("admin", encodedPassword, "管理员");
        var tokenInfo = new TokenInfo("test-token", 1L, Instant.now().plusSeconds(86400));

        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(passwordEncoderService.verifyPassword(plainPassword, encodedPassword)).thenReturn(true);
        when(authenticationService.login(any(), eq(86400L))).thenReturn(tokenInfo);

        AdminUserLoginCommand command = new AdminUserLoginCommand("admin", plainPassword, false);

        // When
        TokenInfo result = adminAuthAppService.login(command);

        // Then
        assertThat(result.token()).isEqualTo("test-token");
        verify(passwordEncoderService).verifyPassword(plainPassword, encodedPassword);
    }

    @Test
    void given_valid_credentials_with_remember_me_when_login_then_return_token_info_with_extended_timeout() {
        // Given
        String plainPassword = "Test1234";
        String encodedPassword = testEncoder.encode(plainPassword);
        AdminUser adminUser = new AdminUser("admin", encodedPassword, "管理员");
        var tokenInfo = new TokenInfo("test-token", 1L, Instant.now().plusSeconds(604800));

        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(passwordEncoderService.verifyPassword(plainPassword, encodedPassword)).thenReturn(true);
        when(authenticationService.login(any(), eq(604800L))).thenReturn(tokenInfo);

        AdminUserLoginCommand command = new AdminUserLoginCommand("admin", plainPassword, true);

        // When
        TokenInfo result = adminAuthAppService.login(command);

        // Then
        assertThat(result.token()).isEqualTo("test-token");
    }

    @Test
    void given_nonexistent_username_when_login_then_throw_login_failed_exception() {
        // Given
        when(adminUserRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        AdminUserLoginCommand command = new AdminUserLoginCommand("nonexistent", "Test1234", false);

        // When & Then
        assertThatThrownBy(() -> adminAuthAppService.login(command))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining(AdminMessage.LOGIN_FAILED.message());
    }

    @Test
    void given_wrong_password_when_login_then_throw_login_failed_exception() {
        // Given
        String encodedPassword = testEncoder.encode("Test1234");
        AdminUser adminUser = new AdminUser("admin", encodedPassword, "管理员");
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(passwordEncoderService.verifyPassword("WrongPass123", encodedPassword)).thenReturn(false);

        AdminUserLoginCommand command = new AdminUserLoginCommand("admin", "WrongPass123", false);

        // When & Then
        assertThatThrownBy(() -> adminAuthAppService.login(command))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining(AdminMessage.LOGIN_FAILED.message());
    }

    @Test
    void given_disabled_user_when_login_then_throw_admin_disabled_exception() {
        // Given
        String plainPassword = "Test1234";
        String encodedPassword = testEncoder.encode(plainPassword);
        AdminUser adminUser = new AdminUser("admin", encodedPassword, "管理员");
        adminUser.disable();
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        AdminUserLoginCommand command = new AdminUserLoginCommand("admin", plainPassword, false);

        // When & Then
        assertThatThrownBy(() -> adminAuthAppService.login(command))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining(AdminMessage.ADMIN_DISABLED.message());
    }

    @Test
    void given_logout_when_called_then_delegate_to_authentication_service() {
        // When
        adminAuthAppService.logout();

        // Then
        verify(authenticationService).logout();
    }

    // ========== updatePassword tests ==========

    @Test
    void given_valid_input_when_updatePassword_then_success() {
        // Given
        String oldEncodedPassword = testEncoder.encode("Test1234");
        String newEncodedPassword = testEncoder.encode("NewPass56");
        AdminUser adminUser = new AdminUser("admin", oldEncodedPassword, "管理员");

        when(adminUserRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(passwordEncoderService.verifyPassword("Test1234", oldEncodedPassword)).thenReturn(true);
        when(passwordEncoderService.encodePassword("NewPass56")).thenReturn(newEncodedPassword);
        UpdatePasswordCommand command = new UpdatePasswordCommand("Test1234", "NewPass56");

        // When
        adminAuthAppService.updatePassword(1L, command);

        // Then
        assertThat(adminUser.getPassword()).isEqualTo(newEncodedPassword);
        verify(adminUserRepository).save(adminUser);
    }

    @Test
    void given_nonexistent_user_when_updatePassword_then_throw_admin_not_found_exception() {
        // Given
        when(adminUserRepository.findById(999L)).thenReturn(Optional.empty());
        UpdatePasswordCommand command = new UpdatePasswordCommand("Test1234", "NewPass56");

        // When & Then
        assertThatThrownBy(() -> adminAuthAppService.updatePassword(999L, command))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining(AdminMessage.ADMIN_NOT_FOUND.message());
    }

    @Test
    void given_wrong_old_password_when_updatePassword_then_throw_password_incorrect_exception() {
        // Given
        String oldEncodedPassword = testEncoder.encode("Test1234");
        AdminUser adminUser = new AdminUser("admin", oldEncodedPassword, "管理员");
        when(adminUserRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(passwordEncoderService.verifyPassword("WrongPass123", oldEncodedPassword)).thenReturn(false);
        UpdatePasswordCommand command = new UpdatePasswordCommand("WrongPass123", "NewPass56");

        // When & Then
        assertThatThrownBy(() -> adminAuthAppService.updatePassword(1L, command))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining(AdminMessage.PASSWORD_INCORRECT.message());
    }

    // ========== resetPassword tests ==========

    @Test
    void given_valid_input_when_resetPassword_then_success() {
        // Given
        String oldEncodedPassword = testEncoder.encode("Test1234");
        String newEncodedPassword = testEncoder.encode("NewReset56");
        AdminUser adminUser = new AdminUser("admin", oldEncodedPassword, "管理员");

        when(adminUserRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(passwordEncoderService.encodePassword("NewReset56")).thenReturn(newEncodedPassword);
        ResetPasswordCommand command = new ResetPasswordCommand("NewReset56");

        // When
        adminAuthAppService.resetPassword(1L, command);

        // Then
        assertThat(adminUser.getPassword()).isEqualTo(newEncodedPassword);
        verify(adminUserRepository).save(adminUser);
    }

    @Test
    void given_nonexistent_user_when_resetPassword_then_throw_admin_not_found_exception() {
        // Given
        when(adminUserRepository.findById(999L)).thenReturn(Optional.empty());
        ResetPasswordCommand command = new ResetPasswordCommand("NewReset56");

        // When & Then
        assertThatThrownBy(() -> adminAuthAppService.resetPassword(999L, command))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining(AdminMessage.ADMIN_NOT_FOUND.message());
    }

    // ========== getCurrentAdmin tests ==========

    @Test
    void given_valid_user_when_getCurrentAdmin_then_return_current_user_response() {
        // Given
        AdminUser adminUser = new AdminUser("admin", "Test1234", "管理员");
        when(adminUserRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(adminPermissionAppService.getRoleCodes(1L)).thenReturn(java.util.List.of("ADMIN"));
        when(adminPermissionAppService.getPermissions(1L)).thenReturn(java.util.List.of("admin:user:read"));
        when(adminPermissionAppService.getMenus(1L)).thenReturn(java.util.List.of());

        // When
        var response = adminAuthAppService.getCurrentAdmin(1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.roleCodes()).containsExactly("ADMIN");
        assertThat(response.permissions()).containsExactly("admin:user:read");
        verify(adminPermissionAppService).getRoleCodes(1L);
        verify(adminPermissionAppService).getPermissions(1L);
        verify(adminPermissionAppService).getMenus(1L);
    }

    @Test
    void given_nonexistent_user_when_getCurrentAdmin_then_throw_admin_not_found_exception() {
        // Given
        when(adminUserRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> adminAuthAppService.getCurrentAdmin(999L))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining(AdminMessage.ADMIN_NOT_FOUND.message());
    }
}
