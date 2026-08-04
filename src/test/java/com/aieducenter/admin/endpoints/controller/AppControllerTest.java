package com.aieducenter.admin.endpoints.controller;

import com.aieducenter.admin.application.AppManagementAppService;
import com.aieducenter.admin.application.dto.response.AppDetailResponse;
import com.aieducenter.admin.application.dto.response.AppSummaryResponse;
import com.cartisan.web.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AppController 接线测试（URL → 方法 → 服务、状态码）。
 */
@ExtendWith(MockitoExtension.class)
class AppControllerTest {

    @Mock
    private AppManagementAppService appManagementAppService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        AppController controller = new AppController(appManagementAppService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void given_query_when_list_then_returnPage() throws Exception {
        when(appManagementAppService.list(any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0L, 1, 20));

        mvc.perform(get("/api/admin/apps")
                        .param("page", "0").param("size", "20"))
                .andExpect(status().isOk());

        verify(appManagementAppService).list(any(), any());
    }

    @Test
    void given_id_when_getDetail_then_returnApp() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(appManagementAppService.getDetail(1L)).thenReturn(
                new AppDetailResponse(1L, "my-app", "My App", "desc",
                        1, "启用",
                        new AppDetailResponse.ApiKeyInfo(10L, "my-app", 1, "启用", now, now),
                        null, now, now));

        mvc.perform(get("/api/admin/apps/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.appCode").value("my-app"))
                .andExpect(jsonPath("$.data.name").value("My App"))
                .andExpect(jsonPath("$.data.apiKey.apiKey").value("my-app"));

        verify(appManagementAppService).getDetail(1L);
    }
}
