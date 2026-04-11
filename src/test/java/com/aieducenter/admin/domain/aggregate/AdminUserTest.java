package com.aieducenter.admin.domain.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.aieducenter.admin.domain.enums.AdminUserStatus;
import com.aieducenter.admin.domain.error.AdminMessage;
import com.cartisan.core.exception.DomainException;

/**
 * AdminUser 聚合根测试。
 */
class AdminUserTest {
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    // Helper method to generate encoded password for tests
    private String encodePassword(String plainPassword) {
        return encoder.encode(plainPassword);
    }

    @Test
    void given_valid_input_when_create_admin_then_success() {
        // Given - pre-encoded password (simulating what application service does)
        String encodedPassword = encodePassword("Test1234");

        // When
        AdminUser adminUser = new AdminUser("testuser", encodedPassword, "测试用户");

        // Then
        assertThat(adminUser.getUsername()).isEqualTo("testuser");
        assertThat(adminUser.getNickname()).isEqualTo("测试用户");
        assertThat(adminUser.getStatus()).isEqualTo(AdminUserStatus.ACTIVE);
        assertThat(adminUser.getPassword()).isEqualTo(encodedPassword);
    }

    @Test
    void given_invalid_username_when_create_admin_then_throw_exception() {
        // When & Then
        assertThatThrownBy(() -> new AdminUser("invalid user", encodePassword("Test1234"), "测试用户"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining(AdminMessage.USERNAME_INVALID.message());
    }

    @Test
    void given_encoded_password_when_changePassword_then_success() {
        // Given
        String oldPassword = encodePassword("OldPass123");
        String newPassword = encodePassword("NewPass456");
        AdminUser adminUser = new AdminUser("testuser", oldPassword, "测试用户");

        // When
        adminUser.changePassword(newPassword);

        // Then
        assertThat(adminUser.getPassword()).isEqualTo(newPassword);
    }

    @Test
    void given_null_password_when_changePassword_then_throw_exception() {
        // Given
        AdminUser adminUser = new AdminUser("testuser", encodePassword("Test1234"), "测试用户");

        // When & Then
        assertThatThrownBy(() -> adminUser.changePassword(null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining(AdminMessage.PASSWORD_WEAK.message());
    }

    @Test
    void given_active_admin_when_disable_then_status_disabled() {
        // Given
        AdminUser adminUser = new AdminUser("testuser", encodePassword("Test1234"), "测试用户");

        // When
        adminUser.disable();

        // Then
        assertThat(adminUser.getStatus()).isEqualTo(AdminUserStatus.DISABLED);
    }

    @Test
    void given_disabled_admin_when_enable_then_status_active() {
        // Given
        AdminUser adminUser = new AdminUser("testuser", encodePassword("Test1234"), "测试用户");
        adminUser.disable();

        // When
        adminUser.enable();

        // Then
        assertThat(adminUser.getStatus()).isEqualTo(AdminUserStatus.ACTIVE);
    }
}
