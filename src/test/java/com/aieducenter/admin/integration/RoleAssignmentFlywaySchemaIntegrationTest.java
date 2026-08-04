package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.admin.domain.aggregate.AdminMenu;
import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.aieducenter.admin.domain.entity.AdminRoleMenu;
import com.aieducenter.admin.domain.entity.AdminRolePermission;
import com.aieducenter.admin.domain.enums.MenuType;
import com.aieducenter.admin.domain.repository.AdminMenuRepository;
import com.aieducenter.admin.domain.repository.AdminRoleRepository;
import com.aieducenter.admin.domain.repository.AdminUserRepository;

/**
 * REQ-7 回归：关联实体落库（真 Flyway schema，非 create-drop）。
 *
 * <p>背景：三张关联表（sys_admin_role_menus / sys_admin_role_permissions /
 * sys_admin_user_roles）主键在 V1 DDL 为应用层 TSID（{@code BIGINT PRIMARY KEY}，无自增），
 * 但关联实体曾用 {@code @GeneratedValue(IDENTITY)}，导致生产（Flyway）下 INSERT 时 id 无来源
 * → NOT NULL 违例 → 框架兜底为 400（REQ-7 Bug ①）。测试库默认 {@code ddl-auto=create-drop}
 * 按 Hibernate 注解建表（IDENTITY → 自增列），把这个「实体注解 ↔ Flyway DDL」裂隙掩盖了——
 * 与 V4 迁移注释记录的 {@code system} 列同类问题。</p>
 *
 * <p>本测试在独立 schema {@code req7_flyway} 内用 Flyway 全量迁移（V1–V5）+ {@code ddl-auto=none}
 * 复刻生产 schema，钉死「分配（clear + add）+ save」写路径：关联行带应用层 TSID id 落库、可回读；
 * 并覆盖「重新分配」以守住 orphanRemoval（移除旧关联）路径不撞 NOT NULL。</p>
 *
 * <p>隔离方式：schema 经连接参数 {@code currentSchema=req7_flyway} 指定（而非全局
 * {@code hibernate.default_schema}），HQL 与原生 SQL 都经 search_path 一致解析到 req7_flyway，
 * 且该参数只作用于本测试的数据源连接池、不渗入其他测试上下文。表结构仍由 Flyway 全量迁移生成。</p>
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:25432/aieducenter_test?currentSchema=req7_flyway",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=req7_flyway",
        "spring.flyway.default-schema=req7_flyway",
        "spring.jpa.hibernate.ddl-auto=none",
        "admin.app-registry.base-url=http://localhost:8088"
})
@Transactional
class RoleAssignmentFlywaySchemaIntegrationTest {

    @Autowired
    private AdminRoleRepository roleRepository;

    @Autowired
    private AdminMenuRepository menuRepository;

    @Autowired
    private AdminUserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    @AfterAll
    void dropSchema() throws Exception {
        // 清理：删除本测试专用的独立 schema，不污染共享测试库
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS req7_flyway CASCADE");
        }
    }

    @Test
    @DisplayName("角色分配菜单：关联行带应用层 TSID id 落库并可回读（真 Flyway schema）")
    void given_roleAssignMenus_when_persistAgainstFlywaySchema_then_associationRowInserted() {
        // Given：先持久化菜单与角色（拿到真实 id）
        AdminMenu menu = menuRepository.save(
                menu("菜单_" + suffix(), "/m/" + suffix()));
        AdminRole role = roleRepository.save(
                new AdminRole("角色_" + suffix(), "ROLE_" + suffix(), "测试", 1));
        flushAndClear();

        // When：clear + add（复刻 assignMenus 的全量覆盖写路径）
        role = roleRepository.findById(role.getId()).orElseThrow();
        role.clearMenus();
        role.addMenu(menu.getId());
        roleRepository.save(role);
        // 旧 IDENTITY 实体在此 flush 对 plain BIGINT PK 列触发 INSERT，id 无来源 → NOT NULL 违例
        flushAndClear();

        // Then：关联行落库，id 由应用层 TSID 生成（非 null）
        AdminRole reloaded = roleRepository.findById(role.getId()).orElseThrow();
        assertThat(reloaded.getMenuIds()).containsExactly(menu.getId());
        assertThat(reloaded.getRoleMenus()).hasSize(1);
        assertThat(reloaded.getRoleMenus().iterator().next().getId())
                .as("关联实体 id 应由应用层 TSID 生成").isNotNull();
    }

    @Test
    @DisplayName("角色分配权限：关联行带 TSID id 落库，permission_name 非空（真 Flyway schema）")
    void given_roleAssignPermissions_when_persistAgainstFlywaySchema_then_associationRowInserted() {
        AdminRole role = roleRepository.save(
                new AdminRole("角色_" + suffix(), "ROLE_" + suffix(), "测试", 1));
        flushAndClear();

        role = roleRepository.findById(role.getId()).orElseThrow();
        role.clearPermissions();
        role.addPermission("admin:user:read", "用户查看");
        roleRepository.save(role);
        flushAndClear();

        AdminRole reloaded = roleRepository.findById(role.getId()).orElseThrow();
        assertThat(reloaded.getPermissionCodes()).containsExactly("admin:user:read");
        AdminRolePermission permission = reloaded.getRolePermissions().iterator().next();
        assertThat(permission.getId()).isNotNull();
        assertThat(permission.getPermissionName()).isEqualTo("用户查看");
    }

    @Test
    @DisplayName("用户分配角色：关联行带应用层 TSID id 落库并可回读（真 Flyway schema）")
    void given_userAssignRoles_when_persistAgainstFlywaySchema_then_associationRowInserted() {
        AdminRole role = roleRepository.save(
                new AdminRole("角色_" + suffix(), "ROLE_" + suffix(), "测试", 1));
        AdminUser user = userRepository.save(
                new AdminUser("u" + suffix(), "encoded-pwd", "昵称"));
        flushAndClear();

        user = userRepository.findById(user.getId()).orElseThrow();
        user.clearRoles();
        user.addRole(role.getId());
        userRepository.save(user);
        flushAndClear();

        AdminUser reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getRoleIds()).containsExactly(role.getId());
    }

    @Test
    @DisplayName("重新分配（再次 clear+add）走 orphanRemoval，移除旧关联不撞 NOT NULL（真 Flyway schema）")
    void given_reassignMenus_when_clearAndAddAgain_then_orphanRemovedWithoutNullViolation() {
        AdminMenu menuA = menuRepository.save(
                menu("菜单A_" + suffix(), "/a/" + suffix()));
        AdminMenu menuB = menuRepository.save(
                menu("菜单B_" + suffix(), "/b/" + suffix()));
        AdminRole role = roleRepository.save(
                new AdminRole("角色_" + suffix(), "ROLE_" + suffix(), "测试", 1));
        flushAndClear();

        // 第一次分配 menuA
        role = roleRepository.findById(role.getId()).orElseThrow();
        role.clearMenus();
        role.addMenu(menuA.getId());
        roleRepository.save(role);
        flushAndClear();

        // 重新分配 menuB：clear 移除 menuA → orphanRemoval。
        // 若 @OneToMany 的 @JoinColumn 双写（非只读），Hibernate 会先 UPDATE role_id=null 解引用，
        // 在 role_id NOT NULL 上必现违例（AdminUser 已用只读 JoinColumn 规避，见该处注释）。
        role = roleRepository.findById(role.getId()).orElseThrow();
        role.clearMenus();
        role.addMenu(menuB.getId());
        roleRepository.save(role);
        flushAndClear();

        AdminRole reloaded = roleRepository.findById(role.getId()).orElseThrow();
        assertThat(reloaded.getMenuIds()).containsExactly(menuB.getId());
        assertThat(reloaded.getRoleMenus()).hasSize(1);
    }

    // ========== helpers ==========

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /** 便捷构造菜单（Soybean 模型全字段，本测试只关心关联落库，其余缺省）。 */
    private static AdminMenu menu(String menuName, String routePath) {
        return new AdminMenu(menuName, menuName, routePath, null, null, null, null, 1, MenuType.MENU,
                null, false, false, false, false, null, null, null, null, null);
    }
}
