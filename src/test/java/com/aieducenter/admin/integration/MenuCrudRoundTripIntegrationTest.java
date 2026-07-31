package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.admin.application.MenuManagementAppService;
import com.aieducenter.admin.application.dto.command.CreateMenuCommand;
import com.aieducenter.admin.application.dto.response.MenuResponse;
import com.aieducenter.admin.domain.entity.MenuQueryParam;
import com.aieducenter.admin.domain.enums.AdminUserStatus;
import com.aieducenter.admin.domain.enums.MenuIconType;
import com.aieducenter.admin.domain.enums.MenuType;
import com.aieducenter.admin.domain.repository.AdminMenuRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 菜单 CRUD round-trip：建菜单带全部 Soybean 路由生成器字段 → 查回字段一致（issue #13 验收）。
 *
 * <p>覆盖：全部字段持久化（含 {@code query} JSON 列）、MapStruct 实体→响应、
 * 整数 code ↔ BaseEnum 的 JSON 反序列化（框架 BaseEnumDeserializer）。</p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class MenuCrudRoundTripIntegrationTest {

    @Autowired
    private MenuManagementAppService menuManagementAppService;

    @Autowired
    private AdminMenuRepository menuRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        menuRepository.deleteAllInBatch();
    }

    @Test
    void given_menuWithAllSoybeanFields_when_createAndFind_then_roundTrip() {
        // Given：承载全部 Soybean 路由生成器字段
        List<MenuQueryParam> query = List.of(new MenuQueryParam("id", "1"), new MenuQueryParam("tab", "detail"));
        CreateMenuCommand command = new CreateMenuCommand(
                "用户管理", "manage_user", "/manage/user", "view.manage_user",
                "mdi:account", MenuIconType.ICONIFY, null, 3, MenuType.MENU,
                "route.manage_user", true, false, true, false,
                "manage", "/ext", 2,
                query, AdminUserStatus.ACTIVE);

        // When
        Long id = menuManagementAppService.create(command);
        MenuResponse back = menuManagementAppService.findById(id);

        // Then：全部字段 round-trip 一致
        assertThat(back.id()).isEqualTo(id);
        assertThat(back.menuName()).isEqualTo("用户管理");
        assertThat(back.routeName()).isEqualTo("manage_user");
        assertThat(back.routePath()).isEqualTo("/manage/user");
        assertThat(back.component()).isEqualTo("view.manage_user");
        assertThat(back.icon()).isEqualTo("mdi:account");
        assertThat(back.iconType()).isEqualTo(MenuIconType.ICONIFY);
        assertThat(back.sortOrder()).isEqualTo(3);
        assertThat(back.menuType()).isEqualTo(MenuType.MENU);
        assertThat(back.i18nKey()).isEqualTo("route.manage_user");
        assertThat(back.keepAlive()).isTrue();
        assertThat(back.constant()).isFalse();
        assertThat(back.multiTab()).isTrue();
        assertThat(back.hideInMenu()).isFalse();
        assertThat(back.activeMenu()).isEqualTo("manage");
        assertThat(back.href()).isEqualTo("/ext");
        assertThat(back.fixedIndexInTab()).isEqualTo(2);
        assertThat(back.query()).containsExactlyElementsOf(query);
        assertThat(back.status()).isEqualTo(AdminUserStatus.ACTIVE);
        assertThat(back.createdAt()).isNotNull();  // 审计出站
        assertThat(back.updatedAt()).isNotNull();
    }

    @Test
    void given_directory_when_createAndFind_then_menuType_directory() {
        Long id = menuManagementAppService.create(new CreateMenuCommand(
                "系统管理", "manage", "/manage", "layout.base", null, null,
                null, 1, MenuType.DIRECTORY, "route.manage", false, false, false, false,
                null, null, null, null, null));

        assertThat(menuManagementAppService.findById(id).menuType()).isEqualTo(MenuType.DIRECTORY);
    }

    @Test
    void given_jsonWithIntegerCodes_when_deserialize_then_enumsAndQueryResolved() throws Exception {
        // 框架 BaseEnumDeserializer：整数 code → BaseEnum（menuType=2→MENU / iconType=1→ICONIFY / status=1→ACTIVE）
        String json = """
                {
                  "menuName": "用户管理",
                  "routeName": "manage_user",
                  "routePath": "/manage/user",
                  "component": "view.manage_user",
                  "icon": "mdi:account",
                  "iconType": 1,
                  "parentId": null,
                  "sortOrder": 3,
                  "menuType": 2,
                  "i18nKey": "route.manage_user",
                  "keepAlive": true,
                  "constant": false,
                  "multiTab": true,
                  "hideInMenu": false,
                  "fixedIndexInTab": 2,
                  "query": [{"key": "id", "value": "1"}],
                  "status": 1
                }
                """;

        CreateMenuCommand cmd = objectMapper.readValue(json, CreateMenuCommand.class);

        assertThat(cmd.menuType()).isEqualTo(MenuType.MENU);
        assertThat(cmd.iconType()).isEqualTo(MenuIconType.ICONIFY);
        assertThat(cmd.status()).isEqualTo(AdminUserStatus.ACTIVE);
        assertThat(cmd.keepAlive()).isTrue();
        assertThat(cmd.multiTab()).isTrue();
        assertThat(cmd.fixedIndexInTab()).isEqualTo(2);
        assertThat(cmd.query()).containsExactly(new MenuQueryParam("id", "1"));
    }

    @Test
    void given_updatedMenu_when_find_then_fieldsReplaced() {
        Long id = menuManagementAppService.create(new CreateMenuCommand(
                "旧名", "old", "/old", null, null, null, null, 0, MenuType.MENU,
                null, false, false, false, false, null, null, null, null, null));

        menuManagementAppService.update(id, new com.aieducenter.admin.application.dto.command.UpdateMenuCommand(
                "新名", "manage_user", "/manage/user", "view.manage_user", "mdi:account", MenuIconType.LOCAL,
                null, 5, MenuType.MENU, "route.manage_user", true, false, false, true,
                null, null, null, null, AdminUserStatus.DISABLED));

        MenuResponse back = menuManagementAppService.findById(id);
        assertThat(back.menuName()).isEqualTo("新名");
        assertThat(back.routePath()).isEqualTo("/manage/user");
        assertThat(back.iconType()).isEqualTo(MenuIconType.LOCAL);
        assertThat(back.sortOrder()).isEqualTo(5);
        assertThat(back.keepAlive()).isTrue();
        assertThat(back.hideInMenu()).isTrue();
        assertThat(back.status()).isEqualTo(AdminUserStatus.DISABLED);
    }
}
