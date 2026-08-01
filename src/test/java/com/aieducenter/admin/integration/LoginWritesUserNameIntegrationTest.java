package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import com.aieducenter.admin.application.AdminUserManagementAppService;
import com.aieducenter.admin.application.dto.command.CreateAdminUserCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 登录写会话 userName 集成测试（Phase 0 Bug ④：框架根因已修后的端到端回归）。
 *
 * <p>断言登录成功后，Sa-Token 会话的 {@code "userName"} key 被写入登录用户昵称——
 * 这是后续请求 {@code RequestContext.userName} 被 {@code SecurityFilter} 正确填充的前提
 * （{@code SecurityFilter} 从 {@code StpUtil.getSession().get("userName")} 读取）。</p>
 *
 * <p>历史：Bug ④ 根因（{@code AuthenticationService.login()} 把会话建立与 userName 写入割裂、
 * 逼调用方越过抽象摸 {@code StpUtil}）已在 cartisan-boot commit {@code efcb1e7} 修复——{@code login}
 * 新签名接收 {@code userName}、由框架写入 session。admin 已迁到新签名（传昵称）、删除曾绕过抽象的
 * {@code StpUtil.getSession().set(...)} 临时补丁。本类现在是真链路回归：HTTP 登录 →
 * 框架 {@code SaTokenAuthenticationService.login} 写 session → SecurityFilter 读 → RequestContext。</p>
 *
 * <p>断言点（login-session seam）：HTTP 登录后直接读会话
 * {@code StpUtil.getSessionByLoginId(userId).get("userName")}，不新建测试专用 controller。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LoginWritesUserNameIntegrationTest {

    private static final String PASSWORD = "Test1234";

    private final AdminUserManagementAppService userAppService;
    private final TestRestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int port;

    @Autowired
    LoginWritesUserNameIntegrationTest(
            AdminUserManagementAppService userAppService,
            TestRestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${local.server.port}") int port) {
        this.userAppService = userAppService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.port = port;
    }

    @Test
    @DisplayName("登录成功后会话 userName == 登录用户昵称")
    void given_validLogin_when_login_then_sessionUserNameIsNickname() throws Exception {
        String suffix = uuidSuffix();
        String username = "loginop" + suffix;
        String nickname = "登录运营_" + suffix;
        Long userId = userAppService.create(
                new CreateAdminUserCommand(username, PASSWORD, nickname, null, null, null));

        ResponseEntity<String> response = login(username);

        assertThat(response.getStatusCode().is2xxSuccessful()).as("登录应成功：%s", response.getBody()).isTrue();
        // SecurityFilter 后续从 StpUtil.getSession().get("userName") 读取；同一 loginId 的会话在此断言
        Object sessionUserName = StpUtil.getSessionByLoginId(userId).get("userName");
        assertThat(sessionUserName)
                .as("登录后应将昵称写入会话 userName（供 RequestContext.userName 填充）")
                .isEqualTo(nickname);
    }

    private ResponseEntity<String> login(String username) {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"rememberMe\":false}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/admin/auth/login"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        // 提前校验 token 存在，便于失败时快速定位
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            assertThat(root.path("data").path("token").asText())
                    .as("登录应返回 token：%s", response.getBody()).isNotBlank();
        } catch (Exception e) {
            throw new AssertionError("解析登录响应失败：" + response.getBody(), e);
        }
        return response;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String uuidSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
