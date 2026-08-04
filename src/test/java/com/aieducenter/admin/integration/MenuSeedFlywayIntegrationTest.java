package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import com.aieducenter.admin.application.MenuManagementAppService;
import com.aieducenter.admin.application.dto.query.MenuQuery;
import com.aieducenter.admin.application.dto.response.MenuResponse;
import com.aieducenter.admin.domain.enums.MenuIconType;
import com.aieducenter.admin.domain.enums.MenuType;
import com.cartisan.web.response.PageResponse;

/**
 * 种子菜单回归（issue #14 / #26 / #30）：真 Flyway 全量迁移（V1–V11）下的 Soybean 种子。
 *
 * <p>背景：测试库默认 {@code flyway.enabled=false} + {@code ddl-auto=create}——Hibernate 按实体建表、
 * Flyway 迁移<b>整条链路在套件里从不执行</b>。#13 落地 V6 时仅人工验过一次「干净启动」，
 * 自动化层对「全新库 init 后种子存在且结构正确」零覆盖（AC3）。本类补这块回归：在独立 schema 内跑真
 * Flyway V1–V11 + {@code ddl-auto=none} 复刻生产 schema，钉死八条 Soybean 种子经应用服务（{@code GET /menus}
 * 分页与 {@code GET /menus/tree} 树端点的后端入口）可读、字段完整、directory 作路由前缀容器。</p>
 *
 * <p>种子契约（V6 + V10 + V11；ID 沿用 V2/V5 保留段 10/20/30/40/50/60/70/90）：</p>
 * <pre>
 *   home         menu       /home          layout.base$view.home   mdi:monitor-dashboard           order=1   i18n=route.home
 *   app          directory  /app           layout.base             carbon:application              order=2   i18n=route.app
 *     app_list     menu     /app/list      view.app_list           carbon:application              order=1   i18n=route.app_list
 *     app_detail   menu     /app/list/:id  view.app_detail         carbon:application              order=2 hideInMenu  i18n=route.app_detail
 *   manage       directory  /manage        layout.base             carbon:cloud-service-management order=99  i18n=route.manage
 *     manage_user  menu     /manage/user   view.manage_user        ic:round-manage-accounts         order=1   i18n=route.manage_user
 *     manage_role  menu     /manage/role   view.manage_role        carbon:user-role                order=2   i18n=route.manage_role
 *     manage_menu  menu     /manage/menu   view.manage_menu        material-symbols:route          order=3 keepAlive  i18n=route.manage_menu
 * </pre>
 *
 * <p>隔离方式：经连接参数 {@code currentSchema=menu_seed_flyway} 指定独立 schema（同
 * {@link RoleAssignmentFlywaySchemaIntegrationTest} 的 req7_flyway 套路），HQL/原生 SQL 经 search_path
 * 一致解析，且只作用于本测试数据源连接池；类末 {@code @AfterAll} 删 schema，不污染共享测试库。</p>
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:25432/aieducenter_test?currentSchema=menu_seed_flyway",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=menu_seed_flyway",
        "spring.flyway.default-schema=menu_seed_flyway",
        "spring.jpa.hibernate.ddl-auto=none",
        "admin.app-registry.base-url=http://localhost:8088"
})
class MenuSeedFlywayIntegrationTest {

    @Autowired
    private MenuManagementAppService menuAppService;

    @Autowired
    private DataSource dataSource;

    @AfterAll
    void dropSchema() throws Exception {
        // 清理：删除本测试专用独立 schema，不污染共享测试库
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS menu_seed_flyway CASCADE");
        }
    }

    @Test
    @DisplayName("全新库 Flyway V1–V11 init 后：GET /menus 扁平分页返回八条 Soybean 种子、字段完整")
    void given_cleanFlywayMigration_when_findAll_then_eightSoybeanSeedsWithFullMetadata() {
        PageResponse<MenuResponse> page = menuAppService.findAll(
                new MenuQuery(null, null, null, null), Pageable.ofSize(20));

        assertThat(page.total()).as("V10+V11 种子应恰好 8 条").isEqualTo(8);

        // 按 routeName 索引（Soybean 路由唯一键），逐条断言全字段
        Map<String, MenuResponse> byRoute = page.items().stream()
                .collect(Collectors.toMap(MenuResponse::routeName, Function.identity()));

        // —— 首页（顶级 menu）——
        MenuResponse home = byRoute.get("home");
        assertThat(home).as("缺 home 种子").isNotNull();
        assertThat(home.menuName()).isEqualTo("首页");
        assertThat(home.menuType()).isEqualTo(MenuType.MENU);
        assertThat(home.parentId()).isNull();
        assertThat(home.routePath()).isEqualTo("/home");
        assertThat(home.component()).isEqualTo("layout.base$view.home");
        assertThat(home.i18nKey()).isEqualTo("route.home");
        assertThat(home.icon()).isEqualTo("mdi:monitor-dashboard");
        assertThat(home.iconType()).isEqualTo(MenuIconType.ICONIFY);
        assertThat(home.sortOrder()).isEqualTo(1);
        assertThat(home.keepAlive()).isFalse();

        // —— 应用管理（directory，路由前缀容器）——
        MenuResponse app = byRoute.get("app");
        assertThat(app).as("缺 app 种子").isNotNull();
        assertThat(app.menuName()).isEqualTo("应用管理");
        assertThat(app.menuType()).isEqualTo(MenuType.DIRECTORY);
        assertThat(app.parentId()).isNull();
        assertThat(app.routePath()).isEqualTo("/app");
        assertThat(app.component()).isEqualTo("layout.base");
        assertThat(app.i18nKey()).isEqualTo("route.app");
        assertThat(app.icon()).isEqualTo("carbon:application");
        assertThat(app.iconType()).isEqualTo(MenuIconType.ICONIFY);
        assertThat(app.sortOrder()).isEqualTo(2);
        assertThat(app.keepAlive()).isFalse();

        // —— 应用（menu，挂应用管理下）——
        MenuResponse appList = byRoute.get("app_list");
        assertThat(appList).as("缺 app_list 种子").isNotNull();
        assertThat(appList.menuName()).isEqualTo("应用");
        assertThat(appList.menuType()).isEqualTo(MenuType.MENU);
        assertThat(appList.parentId()).isEqualTo(app.id());
        assertThat(appList.routePath()).isEqualTo("/app/list");
        assertThat(appList.component()).isEqualTo("view.app_list");
        assertThat(appList.i18nKey()).isEqualTo("route.app_list");
        assertThat(appList.icon()).isEqualTo("carbon:application");
        assertThat(appList.iconType()).isEqualTo(MenuIconType.ICONIFY);
        assertThat(appList.sortOrder()).isEqualTo(1);
        assertThat(appList.keepAlive()).isFalse();

        // —— 应用详情（menu，挂应用管理下，hideInMenu，详情页路由注册）——
        MenuResponse appDetail = byRoute.get("app_detail");
        assertThat(appDetail).as("缺 app_detail 种子").isNotNull();
        assertThat(appDetail.menuName()).isEqualTo("应用详情");
        assertThat(appDetail.menuType()).isEqualTo(MenuType.MENU);
        assertThat(appDetail.parentId()).isEqualTo(app.id());
        assertThat(appDetail.routePath()).isEqualTo("/app/list/:id");
        assertThat(appDetail.component()).isEqualTo("view.app_detail");
        assertThat(appDetail.i18nKey()).isEqualTo("route.app_detail");
        assertThat(appDetail.icon()).isEqualTo("carbon:application");
        assertThat(appDetail.iconType()).isEqualTo(MenuIconType.ICONIFY);
        assertThat(appDetail.sortOrder()).isEqualTo(2);
        assertThat(appDetail.keepAlive()).isFalse();
        assertThat(appDetail.hideInMenu()).as("app_detail 应 hideInMenu（详情页不在 sidebar 显示）").isTrue();

        // —— 系统管理（directory，路由前缀容器，sort_order V10 9→99）——
        MenuResponse manage = byRoute.get("manage");
        assertThat(manage).as("缺 manage 种子").isNotNull();
        assertThat(manage.menuName()).isEqualTo("系统管理");
        assertThat(manage.menuType()).isEqualTo(MenuType.DIRECTORY);
        assertThat(manage.parentId()).isNull();
        assertThat(manage.routePath()).isEqualTo("/manage");
        assertThat(manage.component()).isEqualTo("layout.base");
        assertThat(manage.i18nKey()).isEqualTo("route.manage");
        assertThat(manage.icon()).isEqualTo("carbon:cloud-service-management");
        assertThat(manage.iconType()).isEqualTo(MenuIconType.ICONIFY);
        assertThat(manage.sortOrder()).isEqualTo(99);

        // —— 用户管理（menu，挂系统管理下）——
        MenuResponse user = byRoute.get("manage_user");
        assertThat(user).as("缺 manage_user 种子").isNotNull();
        assertThat(user.menuType()).isEqualTo(MenuType.MENU);
        assertThat(user.parentId()).isEqualTo(manage.id());
        assertThat(user.routePath()).isEqualTo("/manage/user");
        assertThat(user.component()).isEqualTo("view.manage_user");
        assertThat(user.i18nKey()).isEqualTo("route.manage_user");
        assertThat(user.icon()).isEqualTo("ic:round-manage-accounts");
        assertThat(user.iconType()).isEqualTo(MenuIconType.ICONIFY);
        assertThat(user.sortOrder()).isEqualTo(1);

        // —— 角色管理（menu，挂系统管理下）——
        MenuResponse role = byRoute.get("manage_role");
        assertThat(role).as("缺 manage_role 种子").isNotNull();
        assertThat(role.menuType()).isEqualTo(MenuType.MENU);
        assertThat(role.parentId()).isEqualTo(manage.id());
        assertThat(role.routePath()).isEqualTo("/manage/role");
        assertThat(role.component()).isEqualTo("view.manage_role");
        assertThat(role.i18nKey()).isEqualTo("route.manage_role");
        assertThat(role.icon()).isEqualTo("carbon:user-role");
        assertThat(role.iconType()).isEqualTo(MenuIconType.ICONIFY);
        assertThat(role.sortOrder()).isEqualTo(2);

        // —— 菜单管理（menu，挂系统管理下，Soybean keepAlive）——
        MenuResponse menu = byRoute.get("manage_menu");
        assertThat(menu).as("缺 manage_menu 种子").isNotNull();
        assertThat(menu.menuType()).isEqualTo(MenuType.MENU);
        assertThat(menu.parentId()).isEqualTo(manage.id());
        assertThat(menu.routePath()).isEqualTo("/manage/menu");
        assertThat(menu.component()).isEqualTo("view.manage_menu");
        assertThat(menu.i18nKey()).isEqualTo("route.manage_menu");
        assertThat(menu.icon()).isEqualTo("material-symbols:route");
        assertThat(menu.iconType()).isEqualTo(MenuIconType.ICONIFY);
        assertThat(menu.sortOrder()).isEqualTo(3);
        assertThat(menu.keepAlive()).as("manage_menu 应 keepAlive（镜像 soybean/example）").isTrue();

        // AC4：icon 一律 iconify id（prefix:name 形），无 Material Symbols 字体名残留（V5 裸名如 'dashboard'）
        assertThat(page.items()).allSatisfy(m ->
                assertThat(m.icon()).as("%s 的 icon 应为 iconify id（prefix:name）", m.routeName())
                        .matches("^[a-z0-9-]+:[a-z0-9-]+$"));
    }

    @Test
    @DisplayName("全新库 Flyway init 后：GET /menus/tree 返回 home + app(directory→app_list) + manage(directory→user/role/menu) 结构")
    void given_cleanFlywayMigration_when_findTree_then_directoryStructureCorrect() {
        var tree = menuAppService.findTree();

        // 三个根：home（menu）+ app（directory）+ manage（directory）
        assertThat(tree).hasSize(3);

        MenuResponse home = tree.stream().filter(m -> "home".equals(m.routeName())).findFirst().orElseThrow();
        assertThat(home.menuType()).isEqualTo(MenuType.MENU);
        assertThat(home.children()).isEmpty();

        MenuResponse app = tree.stream().filter(m -> "app".equals(m.routeName())).findFirst().orElseThrow();
        assertThat(app.menuType()).isEqualTo(MenuType.DIRECTORY);
        // app directory 挂两个 menu：app_list + app_detail（按 sortOrder 升序）
        assertThat(app.children()).extracting(MenuResponse::routeName)
                .containsExactly("app_list", "app_detail");
        assertThat(app.children()).allSatisfy(child ->
                assertThat(child.menuType()).isEqualTo(MenuType.MENU));

        MenuResponse manage = tree.stream().filter(m -> "manage".equals(m.routeName())).findFirst().orElseThrow();
        assertThat(manage.menuType()).isEqualTo(MenuType.DIRECTORY);
        // directory 作路由前缀容器，挂三个 menu，按 sortOrder 升序
        assertThat(manage.children()).extracting(MenuResponse::routeName)
                .containsExactly("manage_user", "manage_role", "manage_menu");
        assertThat(manage.children()).allSatisfy(child ->
                assertThat(child.menuType()).isEqualTo(MenuType.MENU));
    }

    @Test
    @DisplayName("种子 count + 分页参数无关：page=0 size=1 时 total 仍为 8")
    void given_smallPageSize_when_findAll_then_totalStillEight() {
        PageResponse<MenuResponse> page = menuAppService.findAll(
                new MenuQuery(null, null, null, null), PageRequest.of(0, 1));

        assertThat(page.items()).hasSize(1);
        assertThat(page.total()).isEqualTo(8);
        assertThat(page.size()).isEqualTo(1);
    }
}
