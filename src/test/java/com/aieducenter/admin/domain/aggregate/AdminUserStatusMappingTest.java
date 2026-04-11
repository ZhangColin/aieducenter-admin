package com.aieducenter.admin.domain.aggregate;

import com.aieducenter.admin.domain.enums.AdminUserStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify enum code mapping.
 *
 * This test documents the expected behavior:
 * - ACTIVE has code = 1
 * - DISABLED has code = 0
 */
class AdminUserStatusMappingTest {

    @Test
    void verifyEnumCodes() {
        System.out.println("=== Enum Code Mapping ===");
        System.out.println("ACTIVE: code=" + AdminUserStatus.ACTIVE.getCode() + ", name=" + AdminUserStatus.ACTIVE.getName());
        System.out.println("DISABLED: code=" + AdminUserStatus.DISABLED.getCode() + ", name=" + AdminUserStatus.DISABLED.getName());
        System.out.println("========================");

        assertThat(AdminUserStatus.ACTIVE.getCode()).isEqualTo(1);
        assertThat(AdminUserStatus.DISABLED.getCode()).isEqualTo(0);
    }
}
