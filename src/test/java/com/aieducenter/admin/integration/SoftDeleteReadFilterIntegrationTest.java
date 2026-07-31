package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import com.aieducenter.admin.application.RoleManagementAppService;
import com.aieducenter.admin.application.dto.command.CreateRoleCommand;
import com.aieducenter.admin.domain.aggregate.AdminMenu;
import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.aieducenter.admin.domain.enums.MenuType;
import com.aieducenter.admin.domain.repository.AdminMenuRepository;
import com.aieducenter.admin.domain.repository.AdminRoleRepository;
import com.aieducenter.admin.domain.repository.AdminUserRepository;

import jakarta.persistence.EntityManager;

/**
 * 软删除读过滤集成测试（REQ-5 / issue #7）。
 *
 * <p>验证框架 cartisan-data-jpa 的 {@code SoftDeletableRestrictionContributor}
 * （ZhangColin/cartisan-boot#2 的修正）对 AdminUser 聚合根的读过滤是否在运行时真实生效——
 * 即「DELETE 返回 200 但用户仍在（查询不过滤 deleted）」的原始病灶是否已消除。</p>
 *
 * <p>三条断言联合钉死，排除假阳性：</p>
 * <ol>
 *   <li>{@code findById(已删 id)} → {@code empty}（详情读路径过滤）</li>
 *   <li>{@code findAll} 不含已删用户（列表读路径过滤）</li>
 *   <li>原生 SQL 直查 {@code deleted = true}（确是读过滤生效，而非「写侧没置位」的假阳性）</li>
 * </ol>
 *
 * <p>第 3 条是反向证据：原生 SQL 不经 Hibernate SQL 生成（框架 javadoc 所述的逃生通道），
 * 能读到已软删记录——若它为 true 而前两条过滤生效，则证明「写成功 + 读过滤」双就位；
 * 若前两条不过滤，则复现 REQ-5；若第 3 条为 false，则说明删除本身没成功（写侧问题）。</p>
 *
 * <p>走真库 + 真 Spring，直接打 Repository（不经 HTTP / 应用层），把病灶精确锁定在
 * 「聚合根本身的 Hibernate 读路径」。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SoftDeleteReadFilterIntegrationTest {

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleManagementAppService roleAppService;

    @Autowired
    private AdminRoleRepository adminRoleRepository;

    @Autowired
    private AdminMenuRepository adminMenuRepository;

    @Test
    @DisplayName("软删后 findById 为空、findAll 不含、DB deleted=true（读过滤真实生效）")
    void given_softDeletedUser_when_readPaths_then_filteredOutAndDbFlagSet() {
        String username = "sd" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        AdminUser user = adminUserRepository.save(new AdminUser(username, "encoded-pwd-test", "软删验证"));
        Long id = user.getId();

        // 软删（走框架 BaseRepositoryImpl.delete → markAsDeleted + save）
        adminUserRepository.delete(adminUserRepository.findById(id).orElseThrow());

        // 1) findById 读路径：已软删 → 不可见
        assertThat(adminUserRepository.findById(id))
                .as("findById 应过滤已软删记录")
                .isEmpty();
        // 2) findAll 读路径：已软删 → 不在列表
        assertThat(adminUserRepository.findAll().stream().map(AdminUser::getUsername))
                .as("findAll 应过滤已软删记录")
                .doesNotContain(username);
        // 3) 反向证据：原生 SQL 直查（不经 Hibernate SQL 生成）确认 deleted 已置位
        Boolean deletedFlag = (Boolean) entityManager
                .createNativeQuery("SELECT deleted FROM sys_admin_users WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();
        assertThat(deletedFlag)
                .as("DB deleted 标志应已置为 true（排除「删除没成功」的假阳性）")
                .isTrue();
    }

    @Test
    @DisplayName("Role 聚合软删读过滤同样生效（一处框架修复全愈）")
    void given_softDeletedRole_when_readPaths_then_filteredOutAndDbFlagSet() {
        // REQ-5 评论实测 Role 同样中招；启动日志已见 Contributor 为 AdminRole 注册过滤，此处运行时坐实
        String code = "SDR" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        Long roleId = roleAppService.create(new CreateRoleCommand("软删角色_" + code, code, "验证 Role 读过滤", 99));

        // 经仓储直接软删（绕过应用层「使用中不可删」守卫，聚焦读路径）
        adminRoleRepository.delete(adminRoleRepository.findById(roleId).orElseThrow());

        assertThat(adminRoleRepository.findById(roleId))
                .as("Role findById 应过滤已软删记录")
                .isEmpty();
        assertThat(adminRoleRepository.findAll().stream().map(AdminRole::getCode))
                .as("Role findAll 应过滤已软删记录")
                .doesNotContain(code);
        Boolean deletedFlag = (Boolean) entityManager
                .createNativeQuery("SELECT deleted FROM sys_admin_roles WHERE id = :id")
                .setParameter("id", roleId)
                .getSingleResult();
        assertThat(deletedFlag)
                .as("Role DB deleted 标志应已置为 true")
                .isTrue();
    }

    @Test
    @DisplayName("Menu 聚合软删读过滤同样生效（issue #13 回归）")
    void given_softDeletedMenu_when_readPaths_then_filteredOutAndDbFlagSet() {
        // 菜单模型切到 Soybean 后，确认三聚合共用同一读过滤 Contributor 在 Menu 上也生效
        AdminMenu menu = adminMenuRepository.save(new AdminMenu("软删菜单", "sd_menu", "/sd", null, null, null,
                null, 0, MenuType.MENU, null, false, false, false, false,
                null, null, null, null, null));
        Long id = menu.getId();

        adminMenuRepository.delete(adminMenuRepository.findById(id).orElseThrow());

        assertThat(adminMenuRepository.findById(id))
                .as("Menu findById 应过滤已软删记录")
                .isEmpty();
        assertThat(adminMenuRepository.findAll().stream().map(AdminMenu::getMenuName))
                .as("Menu findAll 应过滤已软删记录")
                .doesNotContain("软删菜单");
        Boolean deletedFlag = (Boolean) entityManager
                .createNativeQuery("SELECT deleted FROM sys_admin_menus WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();
        assertThat(deletedFlag)
                .as("Menu DB deleted 标志应已置为 true")
                .isTrue();
    }
}
