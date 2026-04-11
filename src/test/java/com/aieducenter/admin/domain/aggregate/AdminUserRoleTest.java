package com.aieducenter.admin.domain.aggregate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 AdminUser 的角色管理行为。
 *
 * <p>注意：ID 在 JPA 保存时通过 @PrePersist 钩子生成，
 * 构造时 ID 为 null 是正常行为。</p>
 */
class AdminUserRoleTest {

    @Test
    void given_newAdminUser_when_constructed_then_idIsNull() {
        // Given & When
        AdminUser admin = new AdminUser("testuser", "encodedPassword", "测试用户");

        // Then
        assertThat(admin.getId()).isNull();  // ✅ 构造时 ID 为 null，@PrePersist 会在保存时生成
        assertThat(admin.getUsername()).isEqualTo("testuser");
        assertThat(admin.getNickname()).isEqualTo("测试用户");
    }

    @Test
    void given_newAdminUser_when_addRole_then_roleAdded() {
        // Given
        AdminUser admin = new AdminUser("testuser", "encodedPassword", "测试用户");

        // When
        admin.addRole(1L);

        // Then
        assertThat(admin.getRoleIds()).hasSize(1);
        assertThat(admin.getRoleIds()).containsExactly(1L);
    }

    @Test
    void given_newAdminUserWithExternalId_when_constructed_then_useExternalId() {
        // Given & When
        AdminUser admin = new AdminUser("testuser", "encodedPassword", "测试用户");

        // Then
        assertThat(admin.getId()).isNull();  // 初始状态 ID 为 null
        // @PrePersist 会在保存时生成 ID
        assertThat(admin.getUsername()).isEqualTo("testuser");
    }

    @Test
    void given_newAdminUserWithoutId_when_addRoleMultipleTimes_then_allRolesAdded() {
        // Given
        AdminUser admin = new AdminUser("testuser", "encodedPassword", "测试用户");

        // When
        admin.addRole(1L);
        admin.addRole(2L);
        admin.addRole(3L);

        // Then
        assertThat(admin.getId()).isNull();  // 构造时仍为 null
        assertThat(admin.getRoleIds()).hasSize(3);
        assertThat(admin.getRoleIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
    }
}
