package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import com.aieducenter.admin.application.AdminUserManagementAppService;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.domain.repository.AdminUserRepository;
import com.cartisan.core.exception.ApplicationException;

/**
 * AdminUser 并发集成测试。
 *
 * <p>使用真实数据库测试并发场景下的数据一致性。</p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminUserConcurrencyIntegrationTest {

    @Autowired
    private AdminUserManagementAppService adminUserManagementAppService;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @AfterEach
    void tearDown() {
        // Clean up test data
        adminUserRepository.deleteAll();
    }

    @Test
    void should_allow_only_one_creation_when_concurrent_requests_with_same_username() throws Exception {
        // Given
        int threadCount = 10;
        String username = "concurrent_test_user";
        String password = "Test1234";
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 10 threads try to create user with same username
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    CreateAdminUserCommand command = new CreateAdminUserCommand(
                        username, password, "测试用户", null, null
                    );
                    adminUserManagementAppService.create(command);
                    successCount.incrementAndGet();
                } catch (ApplicationException e) {
                    // Expected: duplicate username error
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    // Unexpected error
                    e.printStackTrace();
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executorService.shutdown();

        // Then: Only one thread should succeed, 9 should fail with duplicate username
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(9);

        // Verify only one user exists in database
        assertThat(adminUserRepository.count()).isEqualTo(1);
        assertThat(adminUserRepository.existsByUsername(username)).isTrue();
    }
}
