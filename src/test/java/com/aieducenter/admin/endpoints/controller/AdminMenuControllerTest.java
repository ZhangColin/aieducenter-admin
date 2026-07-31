package com.aieducenter.admin.endpoints.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.aieducenter.admin.application.MenuManagementAppService;
import com.aieducenter.admin.application.dto.command.CreateMenuCommand;
import com.aieducenter.admin.application.dto.command.UpdateMenuCommand;
import com.aieducenter.admin.application.dto.response.MenuResponse;
import com.aieducenter.admin.domain.enums.MenuType;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * AdminMenuController API 测试（接线层：URL → 方法 → 服务、状态码、@Valid）。
 *
 * <p>枚举的整数 code ↔ BaseEnum 反/序列化由框架 BaseEnumDeserializer/Serializer 经全 Spring 上下文
 * 处理，在 {@code MenuCrudRoundTripIntegrationTest} 以 MockMvc + 真 ObjectMapper 覆盖；
 * 本 standalone 接线测试发送枚举名仅用于驱动控制器路由。</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminMenuControllerTest {

    @Mock
    private MenuManagementAppService menuManagementAppService;

    private AdminMenuController controller;
    private org.springframework.test.web.servlet.MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new AdminMenuController(menuManagementAppService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void given_authenticatedUser_when_findTree_then_returnMenus() throws Exception {
        when(menuManagementAppService.findTree()).thenReturn(List.of(
                menuResp(1L, "用户管理", "manage_user", "/manage/user"),
                menuResp(2L, "角色管理", "manage_role", "/manage/role")
        ));

        mvc.perform(get("/api/admin/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].menuName").value("用户管理"))
                .andExpect(jsonPath("$.data[1].id").value(2))
                .andExpect(jsonPath("$.data[1].menuName").value("角色管理"));

        verify(menuManagementAppService).findTree();
    }

    @Test
    void given_authenticatedUser_when_findById_then_returnMenu() throws Exception {
        when(menuManagementAppService.findById(1L))
                .thenReturn(menuResp(1L, "用户管理", "manage_user", "/manage/user"));

        mvc.perform(get("/api/admin/menus/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.menuName").value("用户管理"));

        verify(menuManagementAppService).findById(1L);
    }

    @Test
    void given_validCommand_when_createMenu_then_returnMenuId() throws Exception {
        String json = """
                {
                    "menuName": "用户管理",
                    "routeName": "manage_user",
                    "routePath": "/manage/user",
                    "component": "view.manage_user",
                    "icon": "mdi:account",
                    "iconType": "ICONIFY",
                    "parentId": null,
                    "sortOrder": 1,
                    "menuType": "MENU",
                    "i18nKey": "route.manage_user",
                    "status": "ACTIVE"
                }
                """;

        when(menuManagementAppService.create(any(CreateMenuCommand.class))).thenReturn(1L);

        mvc.perform(post("/api/admin/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        verify(menuManagementAppService).create(any(CreateMenuCommand.class));
    }

    @Test
    void given_validCommand_when_updateMenu_then_success() throws Exception {
        String json = """
                {
                    "menuName": "新名称",
                    "routeName": "manage_user",
                    "routePath": "/manage/user",
                    "parentId": null,
                    "sortOrder": 2,
                    "menuType": "MENU"
                }
                """;

        mvc.perform(put("/api/admin/menus/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(menuManagementAppService).update(eq(1L), any(UpdateMenuCommand.class));
    }

    @Test
    void given_existingMenu_when_deleteMenu_then_success() throws Exception {
        mvc.perform(delete("/api/admin/menus/1"))
                .andExpect(status().isOk());

        verify(menuManagementAppService).delete(1L);
    }

    // ========== helper ==========

    /** 构造精简 MenuResponse（Soybean 必备字段，其余缺省）。 */
    private static MenuResponse menuResp(Long id, String menuName, String routeName, String routePath) {
        return new MenuResponse(
                id, menuName, routeName, routePath, null, null, null, null, null,
                MenuType.MENU, null, false, false, false, false, null, null, null,
                null, null, null, null, null);
    }
}
