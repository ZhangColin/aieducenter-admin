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

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * AdminMenuController API 测试。
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
        // Given
        when(menuManagementAppService.findTree()).thenReturn(List.of(
                new MenuResponse(1L, "用户管理", "/users", "user", null, 1, null),
                new MenuResponse(2L, "角色管理", "/roles", "role", null, 2, null)
        ));

        // When & Then
        mvc.perform(get("/api/admin/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("用户管理"))
                .andExpect(jsonPath("$.data[1].id").value(2))
                .andExpect(jsonPath("$.data[1].name").value("角色管理"));

        verify(menuManagementAppService).findTree();
    }

    @Test
    void given_authenticatedUser_when_findById_then_returnMenu() throws Exception {
        // Given
        when(menuManagementAppService.findById(1L))
                .thenReturn(new MenuResponse(1L, "用户管理", "/users", "user", null, 1, null));

        // When & Then
        mvc.perform(get("/api/admin/menus/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("用户管理"));

        verify(menuManagementAppService).findById(1L);
    }

    @Test
    void given_validCommand_when_createMenu_then_returnMenuId() throws Exception {
        // Given
        String json = """
                {
                    "name": "用户管理",
                    "path": "/users",
                    "icon": "user",
                    "parentId": null,
                    "sortOrder": 1
                }
                """;

        when(menuManagementAppService.create(any(CreateMenuCommand.class))).thenReturn(1L);

        // When & Then
        mvc.perform(post("/api/admin/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        verify(menuManagementAppService).create(any(CreateMenuCommand.class));
    }

    @Test
    void given_validCommand_when_updateMenu_then_success() throws Exception {
        // Given
        String json = """
                {
                    "name": "新名称",
                    "path": "/new/path",
                    "icon": "newIcon",
                    "parentId": null,
                    "sortOrder": 2
                }
                """;

        // When & Then
        mvc.perform(put("/api/admin/menus/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(menuManagementAppService).update(eq(1L), any(UpdateMenuCommand.class));
    }

    @Test
    void given_existingMenu_when_deleteMenu_then_success() throws Exception {
        // When & Then
        mvc.perform(delete("/api/admin/menus/1"))
                .andExpect(status().isOk());

        verify(menuManagementAppService).delete(1L);
    }
}
