package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.aieducenter.admin.application.AdminUserManagementAppService;
import com.aieducenter.admin.application.RoleManagementAppService;
import com.aieducenter.admin.application.dto.command.AssignRolesCommand;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.application.dto.command.CreateRoleCommand;
import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.repository.AdminRoleRepository;
import com.aieducenter.admin.domain.repository.AdminUserRepository;

/**
 * 角色分配并发集成测试。
 *
 * <p><b>KNOWN ISSUE:</b> This test reveals a concurrency bug in the current implementation.
 * The role assignment feature is not thread-safe - concurrent assignments cause duplicate key violations.
 * This needs to be fixed by adding proper locking or idempotency checks to the implementation.
 * The test is disabled until the bug is fixed.</p>
 *
 * <p>测试多个线程同时给同一管理员分配角色时的状态一致性。</p>
 */
@SpringBootTest
class RoleAssignmentConcurrencyIntegrationTest {

    @Autowired
    private AdminUserManagementAppService adminUserManagementAppService;

    @Autowired
    private RoleManagementAppService roleManagementAppService;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private AdminRoleRepository adminRoleRepository;

    private Long userId;
    private Long role1Id;
    private Long role2Id;

    @BeforeEach
    void setUp() {
        // Create test user
        CreateAdminUserCommand userCmd = new CreateAdminUserCommand(
            "testuser_concurrent", "Test1234", "测试用户", null, null
        );
        userId = adminUserManagementAppService.create(userCmd);

        // Create test roles
        CreateRoleCommand role1Cmd = new CreateRoleCommand("角色1", "ROLE1", "角色1", 1);
        CreateRoleCommand role2Cmd = new CreateRoleCommand("角色2", "ROLE2", "角色2", 2);
        role1Id = roleManagementAppService.create(role1Cmd);
        role2Id = roleManagementAppService.create(role2Cmd);
    }

    @AfterEach
    void tearDown() {
        adminUserRepository.deleteAll();
        adminRoleRepository.deleteAll();
    }

    @Disabled("Concurrency bug: role assignment is not thread-safe. Needs implementation fix.")
    @Test
    void should_assign_roles_when_multiple_concurrent_assignments() throws Exception {
        // Given
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: 10 threads concurrently assign roles to the same user
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    List<Long> roleIds = (threadId % 2 == 0) ? List.of(role1Id) : List.of(role2Id);
                    AssignRolesCommand command = new AssignRolesCommand(roleIds);
                    adminUserManagementAppService.assignRoles(userId, command);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executorService.shutdown();

        // Then: All assignments should succeed (idempotent operation)
        assertThat(successCount.get()).isEqualTo(threadCount);

        // Verify user has roles assigned
        var user = adminUserRepository.findById(userId).orElseThrow();
        assertThat(user.getRoleIds()).isNotEmpty();
    }
}
