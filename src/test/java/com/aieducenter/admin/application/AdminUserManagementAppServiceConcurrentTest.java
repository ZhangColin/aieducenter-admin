package com.aieducenter.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aieducenter.admin.application.dto.command.AssignRolesCommand;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.aieducenter.admin.domain.error.AdminMessage;
import com.aieducenter.admin.domain.repository.AdminRoleRepository;
import com.aieducenter.admin.domain.repository.AdminUserRepository;
import com.aieducenter.admin.application.mapper.AdminUserMapper;
import com.aieducenter.admin.domain.service.PasswordEncoderService;
import com.cartisan.core.exception.DomainException;

@ExtendWith(MockitoExtension.class)
class AdminUserManagementAppServiceConcurrentTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private AdminRoleRepository adminRoleRepository;

    @Mock
    private AdminUserAuthAppService adminUserAuthAppService;

    @Mock
    private AdminUserMapper adminUserMapper;

    @Mock
    private PasswordEncoderService passwordEncoderService;

    private AdminUserManagementAppService adminUserManagementAppService;

    @BeforeEach
    void setUp() {
        adminUserManagementAppService = new AdminUserManagementAppService(
            adminUserRepository,
            adminRoleRepository,
            adminUserAuthAppService,
            adminUserMapper,
            passwordEncoderService
        );
    }

    @Test
    void given_concurrent_create_with_same_username_when_username_unique_then_only_one_succeeds() throws InterruptedException {
        // Given
        String username = "concurrentuser";
        CreateAdminUserCommand command = new CreateAdminUserCommand(
            username, "Test1234", "测试用户", "test@example.com", null
        );

        AtomicInteger callCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Mock: first call returns false (username available), subsequent calls return true (simulate race condition)
        when(adminUserRepository.existsByUsername(username)).thenAnswer(invocation -> {
            int currentCall = callCount.incrementAndGet();
            return currentCall > 1; // Only first call sees username as available
        });

        when(passwordEncoderService.encodePassword("Test1234")).thenReturn("$2a$10$encodedPassword");

        when(adminUserRepository.save(any(AdminUser.class)))
            .thenAnswer(invocation -> {
                AdminUser user = invocation.getArgument(0);
                java.lang.reflect.Field idField = AdminUser.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(user, 1L);
                return user;
            });

        // When: 10 threads try to create user with same username concurrently
        for (int i = 0; i < 10; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    adminUserManagementAppService.create(command);
                    successCount.incrementAndGet();
                } catch (DomainException e) {
                    if (e.getMessage().contains(AdminMessage.USERNAME_ALREADY_EXISTS.message())) {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore other exceptions from reflection
                }
            });
        }

        startLatch.countDown(); // Start all threads at once
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        // Then: only one creation should succeed, nine should fail with duplicate username error
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(9);

        // Verify save was called only once (unique constraint working)
        verify(adminUserRepository).save(any(AdminUser.class));
    }

    @Test
    void given_concurrent_delete_same_user_when_multiple_deletes_then_only_one_succeeds() throws InterruptedException {
        // Given
        Long userId = 1L;
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        when(adminUserRepository.count()).thenReturn(2L); // Allow deletion

        AtomicInteger findCallCount = new AtomicInteger(0);
        when(adminUserRepository.findById(userId)).thenAnswer(invocation -> {
            int currentCall = findCallCount.incrementAndGet();
            if (currentCall == 1) {
                return Optional.of(adminUser); // First call finds the user
            } else {
                return Optional.empty(); // Subsequent calls don't find it (already deleted)
            }
        });

        // When: 10 threads try to delete same user concurrently
        for (int i = 0; i < 10; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    adminUserManagementAppService.delete(userId);
                    successCount.incrementAndGet();
                } catch (DomainException e) {
                    if (e.getMessage().contains(AdminMessage.ADMIN_NOT_FOUND.message())) {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore other exceptions
                }
            });
        }

        startLatch.countDown(); // Start all threads at once
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        // Then: only one deletion should succeed, nine should fail with user not found error
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(9);

        // Verify delete was called only once
        verify(adminUserRepository).delete(any(AdminUser.class));
    }

    @Test
    void given_concurrent_assign_roles_when_multiple_assignments_then_all_succeed() throws Exception {
        // Given
        Long userId = 1L;
        AssignRolesCommand command = new AssignRolesCommand(List.of(1L, 2L));
        AdminUser adminUser = new AdminUser("testuser", "Test1234", "测试用户");
        AdminRole role1 = new AdminRole("管理员", "ADMIN", "管理员", 1);
        AdminRole role2 = new AdminRole("操作员", "OPERATOR", "操作员", 2);

        // Set IDs using reflection
        java.lang.reflect.Field idField = AdminRole.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(role1, 1L);
        idField.set(role2, 2L);

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        AtomicInteger successCount = new AtomicInteger(0);

        when(adminUserRepository.findById(userId)).thenReturn(Optional.of(adminUser));
        when(adminRoleRepository.findAllById(command.roleIds())).thenReturn(List.of(role1, role2));
        when(adminUserRepository.save(any(AdminUser.class))).thenReturn(adminUser);

        // When: 10 threads try to assign roles concurrently (should all succeed)
        for (int i = 0; i < 10; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    adminUserManagementAppService.assignRoles(userId, command);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Ignore exceptions
                }
            });
        }

        startLatch.countDown(); // Start all threads at once
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        // Then: all assignments should succeed (idempotent operation)
        assertThat(successCount.get()).isEqualTo(10);
    }
}
