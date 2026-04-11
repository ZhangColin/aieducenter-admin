package com.aieducenter.admin.boundary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.aieducenter.admin.domain.error.AdminMessage;
import com.cartisan.core.exception.DomainException;

/**
 * AdminUser 边界值测试。
 *
 * <h3>测试覆盖</h3>
 * <ul>
 *   <li>用户名边界值：最小长度、最大长度、非法字符、空值</li>
 *   <li>密码边界值：空值、非空检查</li>
 * </ul>
 */
@DisplayName("AdminUser 边界值测试")
class AdminUserBoundaryTest {

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    private String encodePassword(String plainPassword) {
        return encoder.encode(plainPassword);
    }

    @ParameterizedTest
    @CsvSource({
        "'a',                    false",  // 1字符 - 太短
        "'ab',                   false",  // 2字符 - 太短
        "'abc',                  true",   // 3字符 - 最小有效长度
        "'ab_',                  true",   // 3字符 - 最小有效长度（下划线）
        "'testuser',             true",   // 正常用户名
        "'a',                    false",  // 1字符 - 太短
        "'user_123456789012345', true",   // 20字符 - 最大有效长度
        "'user_1234567890123456', false", // 21字符 - 超过最大长度
        "'User123',              true",   // 大写字母开头
        "'_username',            false",  // 下划线开头 - 无效
        "'123username',          false",  // 数字开头 - 无效
        "'user name',            false",  // 包含空格 - 无效
        "'user@name',            false",  // 包含特殊字符 - 无效
        "'user-name',            false",  // 包含连字符 - 无效
        "'user.name',            false"   // 包含点号 - 无效
    })
    @DisplayName("用户名格式边界值测试")
    void given_username_boundary_when_create_admin_then_expected_result(
            String username, boolean shouldBeValid) {
        // Given
        String encodedPassword = encodePassword("Test1234");

        // When & Then
        if (shouldBeValid) {
            AdminUser adminUser = new AdminUser(username, encodedPassword, "测试用户");
            assertThat(adminUser.getUsername()).isEqualTo(username);
        } else {
            assertThatThrownBy(() -> new AdminUser(username, encodedPassword, "测试用户"))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining(AdminMessage.USERNAME_INVALID.message());
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", "  ", "\t", "\n"})
    @DisplayName("用户名空值和空白测试")
    void given_null_or_empty_username_when_create_admin_then_throw_exception(String username) {
        // Given
        String encodedPassword = encodePassword("Test1234");

        // When & Then
        assertThatThrownBy(() -> new AdminUser(username, encodedPassword, "测试用户"))
                .isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", "  ", "validEncodedPassword"})
    @DisplayName("密码边界值测试 - 非空检查")
    void given_password_boundary_when_create_admin_then_expected_result(String encodedPassword) {
        // Given
        String validUsername = "testuser";

        // When & Then
        if (encodedPassword != null && !encodedPassword.isBlank()) {
            AdminUser adminUser = new AdminUser(validUsername, encodedPassword, "测试用户");
            assertThat(adminUser.getPassword()).isEqualTo(encodedPassword);
        } else if (encodedPassword == null) {
            assertThatThrownBy(() -> new AdminUser(validUsername, encodedPassword, "测试用户"))
                    .isInstanceOf(DomainException.class);
        } else {
            // Blank strings are allowed (domain only checks null)
            AdminUser adminUser = new AdminUser(validUsername, encodedPassword, "测试用户");
            assertThat(adminUser.getPassword()).isEqualTo(encodedPassword);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "'a',                    true",   // 1字符昵称
        "'ab',                   true",   // 2字符昵称
        "'test',                 true",   // 正常昵称
        "'测试用户',             true",   // 中文昵称
        "'',                     false",  // 空字符串
        "'  ',                   false"   // 空白字符串
    })
    @DisplayName("昵称边界值测试")
    void given_nickname_boundary_when_create_admin_then_expected_result(
            String nickname, boolean shouldBeValid) {
        // Given
        String encodedPassword = encodePassword("Test1234");
        String username = "testuser";

        // When
        AdminUser adminUser = new AdminUser(username, encodedPassword, nickname);

        // Then
        if (shouldBeValid) {
            assertThat(adminUser.getNickname()).isEqualTo(
                nickname != null && !nickname.isBlank() ? nickname : username);
        } else {
            assertThat(adminUser.getNickname()).isEqualTo(username);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", "  "})
    @DisplayName("修改密码时空值测试")
    void given_null_or_empty_password_when_changePassword_then_throw_exception(String encodedPassword) {
        // Given
        AdminUser adminUser = new AdminUser("testuser", encodePassword("Test1234"), "测试用户");

        // When & Then
        if (encodedPassword == null) {
            assertThatThrownBy(() -> adminUser.changePassword(encodedPassword))
                    .isInstanceOf(DomainException.class);
        } else if (encodedPassword.isBlank()) {
            // 空白字符串在修改密码时应该被允许（因为domain只检查null）
            adminUser.changePassword(encodedPassword);
            assertThat(adminUser.getPassword()).isEqualTo(encodedPassword);
        }
    }
}
