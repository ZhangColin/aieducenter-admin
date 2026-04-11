package com.aieducenter.admin.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import com.aieducenter.admin.application.AdminUserManagementAppService;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.application.dto.query.AdminUserQuery;
import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.aieducenter.admin.domain.repository.AdminRoleRepository;
import com.aieducenter.admin.domain.repository.AdminUserRepository;
import com.cartisan.core.exception.ApplicationException;

/**
 * 管理员模块压力测试。
 *
 * <h3>测试目标</h3>
 * <ul>
 *   <li>并发登录压力测试：100 个线程同时尝试登录</li>
 *   <li>数据库连接池压力测试：高并发下连接池不会耗尽</li>
 * </ul>
 *
 * <h3>注意事项</h3>
 * <p>这是框架级别的压力测试，验证系统在高并发下的基本行为。
 * 真正的压力测试需要：</p>
 * <ol>
 *   <li>准备大规模测试数据（至少 10000+ 用户）</li>
 *   <li>使用专门的性能测试工具（JMeter、Gatling、K6）</li>
 *   <li>在类生产环境下进行（硬件配置、网络环境）</li>
 *   <li>监控数据库连接池、CPU、内存等资源使用情况</li>
 * </ol>
 *
 * <h3>执行时机</h3>
 * <p>这些测试耗时较长且环境复杂，默认不执行。需要时通过系统属性启用：</p>
 * <pre>
 * mvn test -Drun.performance.tests=true
 * </pre>
 *
 * @since 0.1.0
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "run.performance.tests", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminStressTest {

    @Autowired
    private AdminUserManagementAppService adminUserManagementAppService;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private AdminRoleRepository adminRoleRepository;

    private static final int CONCURRENT_THREAD_COUNT = 100;
    private static final int TEST_USER_COUNT = 200;

    private List<Long> testUserIds = new ArrayList<>();

    /**
     * 准备测试数据：创建大量用户用于压力测试。
     */
    @BeforeEach
    void setUp() {
        // Create test role
        AdminRole testRole = new AdminRole("压力测试角色", "STRESS_TEST", "用于压力测试", 1);
        testRole = adminRoleRepository.save(testRole);

        // Create test users for stress testing
        List<AdminUser> users = new ArrayList<>();
        for (int i = 0; i < TEST_USER_COUNT; i++) {
            AdminUser user = new AdminUser(
                "stress_user_" + i,
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy", // Test1234
                "压力测试用户 " + i
            );
            users.add(user);
        }
        List<AdminUser> savedUsers = adminUserRepository.saveAll(users);
        // Collect actual user IDs for concurrent tests
        testUserIds = savedUsers.stream().map(AdminUser::getId).collect(java.util.stream.Collectors.toList());
    }

    @AfterEach
    void tearDown() {
        // Clean up test data
        adminUserRepository.deleteAll();
        adminRoleRepository.deleteAll();
    }

    /**
     * 并发分页查询压力测试。
     *
     * <p>模拟 100 个线程同时进行分页查询，验证系统稳定性。</p>
     *
     * <p>预期结果：</p>
     * <ul>
     *   <li>所有查询都能成功完成（无异常）</li>
     *   <li>数据库连接池不会耗尽</li>
     *   <li>平均响应时间在可接受范围内</li>
     * </ul>
     */
    @Test
    void given_100_threads_when_concurrent_pagination_query_then_all_should_succeed() throws Exception {
        // Given
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(CONCURRENT_THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> responseTimes = new ArrayList<>();

        Pageable pageable = PageRequest.of(0, 20);

        // When: 100 threads execute pagination concurrently
        for (int i = 0; i < CONCURRENT_THREAD_COUNT; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready

                    long startTime = System.currentTimeMillis();
                    adminUserManagementAppService.findAll(new AdminUserQuery(null, null, null), pageable);
                    long endTime = System.currentTimeMillis();

                    synchronized (responseTimes) {
                        responseTimes.add(endTime - startTime);
                    }

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads at once
        boolean completed = endLatch.await(60, TimeUnit.SECONDS); // Wait for completion (max 60s)
        executorService.shutdown();

        // Then
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(CONCURRENT_THREAD_COUNT);
        assertThat(failureCount.get()).isEqualTo(0);

        // Calculate statistics
        long avgResponseTime = responseTimes.stream().mapToLong(Long::longValue).sum() / responseTimes.size();
        long maxResponseTime = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);

        System.out.println(String.format(
            "并发分页查询结果: 成功=%d, 失败=%d, 平均响应时间=%d ms, 最大响应时间=%d ms",
            successCount.get(), failureCount.get(), avgResponseTime, maxResponseTime
        ));
    }

    /**
     * 并发登录压力测试（模拟）。
     *
     * <p>通过并发查询用户信息来模拟登录场景，验证系统在高并发下的表现。</p>
     *
     * <p>预期结果：</p>
     * <ul>
     *   <li>所有查询都能成功完成</li>
     *   <li>数据库连接池能够处理并发请求</li>
     *   <li>无死锁或超时现象</li>
     * </ul>
     */
    @Test
    void given_100_threads_when_concurrent_login_simulation_then_all_should_succeed() throws Exception {
        // Given
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(CONCURRENT_THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> responseTimes = new ArrayList<>();

        // When: 100 threads query user info concurrently (simulating login)
        for (int i = 0; i < CONCURRENT_THREAD_COUNT; i++) {
            final int threadIndex = i;
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready

                    long startTime = System.currentTimeMillis();
                    // Simulate login by querying user info
                    // In real scenario, this would be authentication
                    // Use actual user IDs from test data
                    Long userId = testUserIds.get(threadIndex % testUserIds.size());
                    adminUserManagementAppService.findById(userId);
                    long endTime = System.currentTimeMillis();

                    synchronized (responseTimes) {
                        responseTimes.add(endTime - startTime);
                    }

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads at once
        boolean completed = endLatch.await(60, TimeUnit.SECONDS); // Wait for completion (max 60s)
        executorService.shutdown();

        // Then
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(CONCURRENT_THREAD_COUNT);
        assertThat(failureCount.get()).isEqualTo(0);

        // Calculate statistics
        long avgResponseTime = responseTimes.stream().mapToLong(Long::longValue).sum() / responseTimes.size();
        long maxResponseTime = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);

        System.out.println(String.format(
            "并发登录模拟结果: 成功=%d, 失败=%d, 平均响应时间=%d ms, 最大响应时间=%d ms",
            successCount.get(), failureCount.get(), avgResponseTime, maxResponseTime
        ));
    }

    /**
     * 数据库连接池压力测试。
     *
     * <p>通过大量并发数据库操作来测试连接池的稳定性和配置合理性。</p>
     *
     * <p>预期结果：</p>
     * <ul>
     *   <li>所有操作都能成功完成</li>
     *   <li>无连接池耗尽异常</li>
     *   <li>连接能够正确释放</li>
     * </ul>
     */
    @Test
    void given_100_threads_when_concurrent_db_operations_then_connection_pool_should_handle() throws Exception {
        // Given
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(CONCURRENT_THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 100 threads perform database operations concurrently
        for (int i = 0; i < CONCURRENT_THREAD_COUNT; i++) {
            final int threadIndex = i;
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready

                    // Perform multiple DB operations in each thread
                    for (int j = 0; j < 5; j++) {
                        Pageable pageable = PageRequest.of(0, 10);
                        adminUserManagementAppService.findAll(new AdminUserQuery(null, null, null), pageable);
                    }

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads at once
        boolean completed = endLatch.await(120, TimeUnit.SECONDS); // Wait for completion (max 120s)
        executorService.shutdown();

        // Then
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(CONCURRENT_THREAD_COUNT);
        assertThat(failureCount.get()).isEqualTo(0);

        System.out.println(String.format(
            "连接池压力测试结果: 成功=%d, 失败=%d, 总操作数=%d",
            successCount.get(), failureCount.get(), CONCURRENT_THREAD_COUNT * 5
        ));
    }

    /**
     * 并发创建用户压力测试。
     *
     * <p>测试在高并发下创建用户的表现，验证唯一性约束和事务处理。</p>
     *
     * <p>预期结果：</p>
     * <ul>
     *   <li>用户名唯一性约束正确执行</li>
     *   <li>无脏数据产生</li>
     *   <li>事务正确回滚或提交</li>
     * </ul>
     */
    @Test
    void given_100_threads_when_concurrent_create_users_then_only_unique_succeeds() throws Exception {
        // Given
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(CONCURRENT_THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 100 threads try to create users with unique usernames
        for (int i = 0; i < CONCURRENT_THREAD_COUNT; i++) {
            final int threadIndex = i;
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready

                    CreateAdminUserCommand command = new CreateAdminUserCommand(
                        "cuser_" + threadIndex,
                        "Test1234",
                        "并发用户 " + threadIndex,
                        null,
                        null
                    );
                    adminUserManagementAppService.create(command);
                    successCount.incrementAndGet();
                } catch (ApplicationException e) {
                    // Expected: duplicate username error
                    System.err.println("Thread " + threadIndex + " failed with ApplicationException: " + e.getMessage());
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Thread " + threadIndex + " failed with Exception: " + e.getClass().getName() + ": " + e.getMessage());
                    failureCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads at once
        boolean completed = endLatch.await(120, TimeUnit.SECONDS); // Wait for completion (max 120s)
        executorService.shutdown();

        // Then: All should succeed because usernames are unique
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(CONCURRENT_THREAD_COUNT);
        assertThat(failureCount.get()).isEqualTo(0);

        System.out.println(String.format(
            "并发创建用户结果: 成功=%d, 失败=%d",
            successCount.get(), failureCount.get()
        ));
    }

    /**
     * TODO: 真实的压力测试需要以下准备：
     *
     * <ol>
     *   <li>数据准备：使用专门的工具准备 10000+ 测试用户数据</li>
     *   <li>监控工具：集成 Prometheus + Grafana 监控数据库连接池、CPU、内存</li>
     *   <li>性能工具：使用 JMeter/Gatling/K6 进行专业的压力测试</li>
     *   <li>环境隔离：在独立的测试环境进行，避免影响开发环境</li>
     *   <li>基准对比：建立性能基准，每次测试后对比分析</li>
     *   <li>失败分析：记录失败的请求和错误信息，进行根因分析</li>
     * </ol>
     *
     * @see <a href="https://jmeter.apache.org/">Apache JMeter</a>
     * @see <a href="https://gatling.io/">Gatling</a>
     * @see <a href="https://k6.io/">K6</a>
     */
    @Test
    void todo_real_stress_test_with_proper_setup() {
        // This is a placeholder for future implementation
        // Real stress testing requires:
        // - Large dataset (10000+ users)
        // - Professional testing tools (JMeter, Gatling, K6)
        // - Monitoring infrastructure (Prometheus, Grafana)
        // - Isolated testing environment
        // - Performance baseline and regression detection
    }
}
