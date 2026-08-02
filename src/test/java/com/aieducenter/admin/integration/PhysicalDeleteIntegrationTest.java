package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;

import com.aieducenter.admin.application.AdminUserManagementAppService;
import com.aieducenter.admin.application.MenuManagementAppService;
import com.aieducenter.admin.application.RoleManagementAppService;
import com.aieducenter.admin.application.dto.command.AssignMenusCommand;
import com.aieducenter.admin.application.dto.command.AssignRolesCommand;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.aieducenter.admin.application.dto.command.CreateRoleCommand;
import com.aieducenter.admin.application.dto.query.MenuQuery;
import com.aieducenter.admin.application.dto.response.MenuResponse;
import com.aieducenter.admin.domain.aggregate.AdminMenu;
import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.aieducenter.admin.domain.enums.MenuType;
import com.aieducenter.admin.domain.error.AdminMessage;
import com.aieducenter.admin.domain.repository.AdminMenuRepository;
import com.aieducenter.admin.domain.repository.AdminRoleRepository;
import com.aieducenter.admin.domain.repository.AdminUserRepository;
import com.cartisan.core.exception.DomainException;
import com.cartisan.web.response.PageResponse;

import jakarta.persistence.EntityManager;

/**
 * 物理删除钉住测试（REQ-14 / issue #24，ADR-0005）。
 *
 * <p>三聚合（AdminUser / AdminRole / AdminMenu）自 ADR-0005 起从软删迁移为物理删除
 * （{@code AuditableSoftDeletable} → {@code Auditable}），REQ-14 的「软删用户残留关联使角色
 * 永不可删」病灶从根上消解。本类钉死迁移后的真实行为：</p>
 *
 * <ol>
 *   <li>删除真实生效：{@code repository.delete} 后 {@code findById} 空、{@code findAll} 不含、
 *       DB 直查<b>无该行</b>（反向证据：物理删除，非「读过滤生效」）</li>
 *   <li>REQ-14 复现链：建角色 + 建用户 + 挂角色 → 删用户（关联行物理清除）→ 删角色成功
 *       （不再被 {@code ROLE_IN_USE} 永阻），且 {@code role_menus} 关联行随角色删除级联清除</li>
 *   <li>in-use 守卫仍准确：角色挂有<b>存活</b>用户时删除仍命中 {@code ROLE_IN_USE}</li>
 *   <li>三张主表不再有 {@code deleted} 列（实体 ↔ schema 对齐，information_schema 直查）</li>
 * </ol>
 *
 * <p>走真库 + 真 Spring，直接打 Repository / AppService（不经 HTTP），把行为精确锁定在
 * 「聚合根本身的持久化路径」。前身为 {@code SoftDeleteReadFilterIntegrationTest}
 * （REQ-5 软删读过滤，前提随 ADR-0005 消失而改写）。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PhysicalDeleteIntegrationTest {

    private static final String PASSWORD = "Test1234";

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private AdminUserManagementAppService userAppService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleManagementAppService roleAppService;

    @Autowired
    private AdminRoleRepository adminRoleRepository;

    @Autowired
    private AdminMenuRepository adminMenuRepository;

    @Autowired
    private MenuManagementAppService menuAppService;

    @Test
    @DisplayName("删除用户：findById 为空、findAll 不含、DB 无该行（物理删除真实生效）")
    void given_user_when_delete_then_physicallyRemoved() {
        String username = "pd" + uuidSuffix();
        AdminUser user = adminUserRepository.save(new AdminUser(username, "encoded-pwd-test", "物理删除验证"));
        Long id = user.getId();

        adminUserRepository.delete(adminUserRepository.findById(id).orElseThrow());

        assertThat(adminUserRepository.findById(id))
                .as("findById：物理删除后不可见")
                .isEmpty();
        assertThat(adminUserRepository.findAll().stream().map(AdminUser::getUsername))
                .as("findAll：物理删除后不在列表")
                .doesNotContain(username);
        assertThat(countRows("sys_admin_users", "id", id))
                .as("DB 直查行数应为 0（物理删除，而非软删置位）")
                .isZero();
    }

    @Test
    @DisplayName("删除角色：findById 为空、findAll 不含、DB 无该行")
    void given_role_when_delete_then_physicallyRemoved() {
        String code = "PDR" + uuidSuffix().toUpperCase();
        Long roleId = roleAppService.create(new CreateRoleCommand("物理删除角色_" + code, code, "验证物理删除", 99, null));

        adminRoleRepository.delete(adminRoleRepository.findById(roleId).orElseThrow());

        assertThat(adminRoleRepository.findById(roleId))
                .as("Role findById：物理删除后不可见")
                .isEmpty();
        assertThat(adminRoleRepository.findAll().stream().map(AdminRole::getCode))
                .as("Role findAll：物理删除后不在列表")
                .doesNotContain(code);
        assertThat(countRows("sys_admin_roles", "id", roleId))
                .as("DB 直查行数应为 0")
                .isZero();
    }

    @Test
    @DisplayName("删除菜单：findById 为空、findAll 不含、DB 无该行")
    void given_menu_when_delete_then_physicallyRemoved() {
        String suffix = uuidSuffix();
        AdminMenu menu = adminMenuRepository.save(new AdminMenu("物理删除菜单_" + suffix, "pd_" + suffix, "/pd_" + suffix, null, null, null,
                null, 0, MenuType.MENU, null, false, false, false, false,
                null, null, null, null, null));
        Long id = menu.getId();

        adminMenuRepository.delete(adminMenuRepository.findById(id).orElseThrow());

        assertThat(adminMenuRepository.findById(id))
                .as("Menu findById：物理删除后不可见")
                .isEmpty();
        assertThat(adminMenuRepository.findAll().stream().map(AdminMenu::getMenuName))
                .as("Menu findAll：物理删除后不在列表")
                .doesNotContain("物理删除菜单_" + suffix);
        assertThat(countRows("sys_admin_menus", "id", id))
                .as("DB 直查行数应为 0")
                .isZero();
    }

    @Test
    @DisplayName("REQ-14 复现链：挂角色的用户删除后角色可删，user_roles / role_menus 关联行级联清除")
    void given_roleAssignedToUser_when_deleteUserThenRole_then_succeedsAndLinksCascaded() {
        String suffix = uuidSuffix();

        // 建菜单 → 建角色 → 角色挂菜单（产生 role_menus 行）
        AdminMenu menu = adminMenuRepository.save(new AdminMenu("REQ14菜单_" + suffix, "req14_" + suffix, "/req14_" + suffix, null, null, null,
                null, 0, MenuType.MENU, null, false, false, false, false,
                null, null, null, null, null));
        String roleCode = "REQ14_" + suffix.toUpperCase();
        Long roleId = roleAppService.create(new CreateRoleCommand("REQ14角色_" + suffix, roleCode, "REQ-14 复现", 99, null));
        roleAppService.assignMenus(roleId, new AssignMenusCommand(List.of(menu.getId())));

        // 建用户 → 挂角色（产生 user_roles 行）
        Long userId = userAppService.create(
                new CreateAdminUserCommand("req14" + suffix, PASSWORD, "REQ14 用户", null, null, null));
        userAppService.assignRoles(userId, new AssignRolesCommand(List.of(roleId)));

        // 用户存活时删角色 → 仍命中 ROLE_IN_USE（in-use 守卫对存活用户保持准确）
        assertThatThrownBy(() -> roleAppService.delete(roleId))
                .as("角色挂有存活用户时删除仍应被拒绝")
                .isInstanceOf(DomainException.class)
                .hasMessageContaining(AdminMessage.ROLE_IN_USE.message());

        // 删用户 → 其 user_roles 关联行同事务物理清除
        userAppService.delete(userId);
        assertThat(countRows("sys_admin_user_roles", "admin_id", userId))
                .as("删除用户后 DB 直查 sys_admin_user_roles 应无该用户关联行")
                .isZero();

        // 删角色 → 不再被 in-use 永阻（REQ-14 病灶消除），成功删除
        assertThatCode(() -> roleAppService.delete(roleId))
                .as("挂过已删用户的角色应可正常删除（REQ-14）")
                .doesNotThrowAnyException();
        assertThat(adminRoleRepository.findById(roleId)).isEmpty();
        assertThat(adminRoleRepository.findAll().stream().map(AdminRole::getCode))
                .as("角色列表查无该角色")
                .doesNotContain(roleCode);
        assertThat(countRows("sys_admin_role_menus", "role_id", roleId))
                .as("删除角色后 DB 直查 sys_admin_role_menus 应无该角色关联行")
                .isZero();
    }

    @Test
    @DisplayName("扁平分页 findAll(spec, pageable) 不含已物理删除行")
    void given_deletedMenu_when_findAllPaginated_then_excludedAndPageCorrect() {
        // 唯一前缀：同一上下文其它方法可能残留数据，用 UUID 前缀 + menuName INNER_LIKE 锁定本例范围
        String prefix = "pagpd" + uuidSuffix();

        AdminMenu parent = adminMenuRepository.save(new AdminMenu(
                prefix + "_p", prefix + "_p", "/" + prefix + "_p", null, null, null,
                null, 0, MenuType.DIRECTORY, null, false, false, false, false,
                null, null, null, null, null));
        AdminMenu child = adminMenuRepository.save(new AdminMenu(
                prefix + "_c", prefix + "_c", "/" + prefix + "_c", null, null, null,
                parent.getId(), 0, MenuType.MENU, null, false, false, false, false,
                null, null, null, null, null));
        adminMenuRepository.save(new AdminMenu(
                prefix + "_l", prefix + "_l", "/" + prefix + "_l", null, null, null,
                null, 0, MenuType.MENU, null, false, false, false, false,
                null, null, null, null, null));

        // 物理删除子菜单
        adminMenuRepository.delete(child);

        PageResponse<MenuResponse> page = menuAppService.findAll(
                new MenuQuery(prefix, null, null, null),
                PageRequest.of(0, 10));

        assertThat(page.total())
                .as("扁平分页不含物理删除行，total=2（父 + 独立）")
                .isEqualTo(2);
        assertThat(page.items())
                .as("items 应为 2 条扁平菜单，且不含已删的子菜单")
                .hasSize(2)
                .extracting(MenuResponse::menuName)
                .containsExactlyInAnyOrder(prefix + "_p", prefix + "_l");
        assertThat(countRows("sys_admin_menus", "id", child.getId()))
                .as("子菜单 DB 直查行数应为 0（物理删除，排除「删除没成功」的假阳性）")
                .isZero();
    }

    @Test
    @DisplayName("三张主表无 deleted 列（实体 ↔ schema 对齐，ADR-0005）")
    void given_physicalDeletePolicy_when_inspectSchema_then_noDeletedColumn() {
        for (String table : List.of("sys_admin_users", "sys_admin_roles", "sys_admin_menus")) {
            Number count = (Number) entityManager
                    .createNativeQuery("SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = 'public' AND table_name = :table AND column_name = 'deleted'")
                    .setParameter("table", table)
                    .getSingleResult();
            assertThat(count.longValue())
                    .as("%s 不应再有 deleted 列", table)
                    .isZero();
        }
    }

    // ========== helpers ==========

    /** 原生 SQL 直查行数（不经 Hibernate，反向证据通道）。 */
    private long countRows(String table, String column, Object value) {
        return ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = :value")
                .setParameter("value", value)
                .getSingleResult()).longValue();
    }

    private String uuidSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
