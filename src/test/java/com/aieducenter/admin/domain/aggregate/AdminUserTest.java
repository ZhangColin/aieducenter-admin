package com.aieducenter.admin.domain.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.aieducenter.admin.domain.enums.AdminUserGender;
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

    // ========== 性别（对齐 Soybean 用户档案）==========

    @Test
    void given_newAdmin_when_created_then_gender_null() {
        // 性别为可选档案字段，创建时默认 null（未填）
        AdminUser adminUser = new AdminUser("testuser", encodePassword("Test1234"), "测试用户");

        assertThat(adminUser.getGender()).isNull();
    }

    @Test
    void given_admin_when_setGender_then_roundTrip() {
        AdminUser adminUser = new AdminUser("testuser", encodePassword("Test1234"), "测试用户");

        adminUser.setGender(AdminUserGender.MALE);

        assertThat(adminUser.getGender()).isEqualTo(AdminUserGender.MALE);
        assertThat(adminUser.getGender().getCode()).isEqualTo(1);
        assertThat(adminUser.getGender().getName()).isEqualTo("男");
    }

    @Test
    void given_maleAdmin_when_setGenderFemale_then_changed() {
        AdminUser adminUser = new AdminUser("testuser", encodePassword("Test1234"), "测试用户");
        adminUser.setGender(AdminUserGender.MALE);

        adminUser.setGender(AdminUserGender.FEMALE);

        assertThat(adminUser.getGender()).isEqualTo(AdminUserGender.FEMALE);
        assertThat(adminUser.getGender().getCode()).isEqualTo(2);
    }

    // ========== 破窗账号（运维韧性）守卫 ==========

    @Test
    void given_breakGlassId_when_isBreakGlass_then_true() throws Exception {
        AdminUser breakGlass = adminUserWithId("admin", AdminUser.BREAK_GLASS_ADMIN_ID);

        assertThat(breakGlass.isBreakGlass()).isTrue();
    }

    @Test
    void given_normalId_when_isBreakGlass_then_false() throws Exception {
        AdminUser normal = adminUserWithId("operator", AdminUser.BREAK_GLASS_ADMIN_ID + 1);

        assertThat(normal.isBreakGlass()).isFalse();
    }

    @Test
    void given_breakGlassId_when_markAsDeleted_then_throwDomainException() throws Exception {
        AdminUser breakGlass = adminUserWithId("admin", AdminUser.BREAK_GLASS_ADMIN_ID);

        assertThatThrownBy(breakGlass::markAsDeleted)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining(AdminMessage.BREAK_GLASS_CANNOT_DELETE.message());
        // 不可删 → 软删标记不应被置位
        assertThat(breakGlass.isDeleted()).isFalse();
    }

    @Test
    void given_normalId_when_markAsDeleted_then_markedDeleted() throws Exception {
        AdminUser normal = adminUserWithId("operator", AdminUser.BREAK_GLASS_ADMIN_ID + 1);

        normal.markAsDeleted();

        assertThat(normal.isDeleted()).isTrue();
    }

    @Test
    void given_breakGlassId_when_disable_then_throwDomainException() throws Exception {
        AdminUser breakGlass = adminUserWithId("admin", AdminUser.BREAK_GLASS_ADMIN_ID);

        assertThatThrownBy(breakGlass::disable)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining(AdminMessage.BREAK_GLASS_CANNOT_DISABLE.message());
        // 不可禁 → 状态保持 ACTIVE
        assertThat(breakGlass.getStatus()).isEqualTo(AdminUserStatus.ACTIVE);
    }

    @Test
    void given_normalId_when_disable_then_disabled() throws Exception {
        AdminUser normal = adminUserWithId("operator", AdminUser.BREAK_GLASS_ADMIN_ID + 1);

        normal.disable();

        assertThat(normal.getStatus()).isEqualTo(AdminUserStatus.DISABLED);
    }

    @Test
    void given_breakGlassId_when_changePassword_then_succeed() throws Exception {
        // 破窗号不可删/不可禁，但可改密（救援号需能轮换密码）
        AdminUser breakGlass = adminUserWithId("admin", AdminUser.BREAK_GLASS_ADMIN_ID);
        String newPassword = encodePassword("NewPass123");

        breakGlass.changePassword(newPassword);

        assertThat(breakGlass.getPassword()).isEqualTo(newPassword);
    }

    /** 构造一个指定 id 的管理员（反射设 id，模拟 JPA 加载后的状态）。 */
    private AdminUser adminUserWithId(String username, long id) throws Exception {
        AdminUser adminUser = new AdminUser(username, encodePassword("Test1234"), username);
        java.lang.reflect.Field idField = AdminUser.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(adminUser, id);
        return adminUser;
    }
}
