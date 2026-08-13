package com.aieducenter.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;

import com.aieducenter.admin.account.application.AccountManagementAppService;
import com.aieducenter.admin.account.application.dto.query.AccountQuery;
import com.aieducenter.admin.account.application.dto.response.AccountManagementDetailResponse;
import com.aieducenter.admin.account.application.dto.response.AccountSummaryResponse;
import com.aieducenter.admin.account.application.dto.wire.AccountReasonWireRequest;
import com.aieducenter.admin.account.application.dto.wire.AccountSearchWireRequest;
import com.aieducenter.admin.account.application.dto.wire.AccountWireResponse;
import com.aieducenter.admin.account.infrastructure.AccountClient;
import com.cartisan.core.exception.BaseCodeMessage;
import com.cartisan.core.exception.DomainException;
import com.cartisan.openapi.client.OpenApiClientException;
import com.cartisan.web.response.PageResponse;

/**
 * 账号管理 BFF 集成测试——mock {@link AccountClient}，验证 {@link AccountManagementAppService}
 * 在 Spring 上下文中的完整接线（DI、wire→response DTO 映射、筛选/分页透传、异常转译）。
 *
 * <p>镜像 {@code PaymentBffIntegrationTest}（脱胎于 {@code AppBffIntegrationTest}）。
 * 不模拟安全层（权限在 {@code AccountRbacEnforcementIntegrationTest} 覆盖）；
 * 不直测 {@link AccountClient}（与 {@code PaymentClient} / {@code AppRegistryClient} 一致，client bean 直接 mock）。</p>
 *
 * <p>对接 identity {@code GET /api/account}（#70 已冻结）：mock 数据按 identity
 * {@code AccountManagementView} 真实形状构造（userId=Long TSID、status=Integer BaseEnum code
 * 1=ACTIVE/0=DISABLED、locked/hasPassword=原始 boolean、无注册时间字段）。本测试在 client 边界 mock，
 * 验证 controller→appservice→client 通路（DTO 映射 / 筛选映射 / 分页契约 / 错误翻译）。</p>
 *
 * <p>另覆盖 #53：管理详情（{@code GET /api/account/{userId}/management}，wire→detail 映射 + 错误翻译）、
 * 三个状态写端点（disable/activate/unlock，{@code {reason}} wire 透传 + 回读详情 + 错误翻译）。operator 身份
 * 透传（经 RequestContext，非 body）的端到端证据在 {@code AccountRbacEnforcementIntegrationTest}（真实登录 +
 * mocked client 边界抓 RequestContext）。</p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AccountBffIntegrationTest {

    @Autowired
    private AccountManagementAppService accountAppService;

    @MockBean
    private AccountClient accountClient;

    // ========== list · DTO 映射 + 分页契约 ==========

    @Test
    void given_accounts_when_list_then_returnMappedPage() {
        when(accountClient.listAccounts(any(AccountSearchWireRequest.class), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(
                        // ACTIVE、未锁、已设密码的常规账号
                        new AccountWireResponse(1001L, "alice@example.com", "13800000001",
                                "爱丽丝", "https://cdn/avatar1.png", 1, false, true),
                        // DISABLED、系统锁定、纯验证码账号（无密码、无资料）
                        new AccountWireResponse(1002L, "bob@example.com", "13800000002",
                                null, null, 0, true, false)
                ), 28L, 0, 20));

        var page = accountAppService.list(
                new AccountQuery(null, null, null, null, null, null, null),
                PageRequest.of(0, 20));

        // 分页契约：total/page/size 沿用 identity 回显
        assertThat(page.total()).isEqualTo(28L);
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(20);
        // DTO 映射：wire → response 逐字段
        assertThat(page.items()).hasSize(2);
        AccountSummaryResponse first = page.items().get(0);
        assertThat(first.userId()).isEqualTo(1001L);
        assertThat(first.email()).isEqualTo("alice@example.com");
        assertThat(first.phone()).isEqualTo("13800000001");
        assertThat(first.nickname()).isEqualTo("爱丽丝");
        assertThat(first.avatar()).isEqualTo("https://cdn/avatar1.png");
        assertThat(first.status()).isEqualTo(1);          // ACTIVE BaseEnum code
        assertThat(first.locked()).isFalse();
        assertThat(first.hasPassword()).isTrue();
        AccountSummaryResponse second = page.items().get(1);
        assertThat(second.userId()).isEqualTo(1002L);
        assertThat(second.status()).isEqualTo(0);          // DISABLED BaseEnum code
        assertThat(second.locked()).isTrue();
        assertThat(second.hasPassword()).isFalse();        // 纯验证码账号
        // 资料/头像可空（identity 不一定都返回）
        assertThat(second.nickname()).isNull();
        assertThat(second.avatar()).isNull();
    }

    // ========== list · 筛选映射 + 页码换算 ==========

    @Test
    void given_filtersAndPageable_when_list_then_passQueryAndConvertPage() {
        when(accountClient.listAccounts(any(AccountSearchWireRequest.class), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0L, 2, 20));

        AccountQuery query = new AccountQuery(
                "alice@example.com", "13800000001", 1001L,
                1, true,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59));

        accountAppService.list(query, PageRequest.of(2, 20));

        // query → wire 映射：筛选原样透传（含 userId Long / status Integer code / createdFrom·To）；
        // Spring Pageable 0-based(page=2) → 客户端 1-based(page=3)
        AccountSearchWireRequest expectedWire = new AccountSearchWireRequest(
                "alice@example.com", "13800000001", 1001L,
                1, true,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59));
        verify(accountClient).listAccounts(eq(expectedWire), eq(3), eq(20));
    }

    // ========== list · 错误翻译 ==========

    @Test
    void given_downstream500_when_list_then_throwThirdPartyError() {
        // identity 内部错误（5xx）→ 统一对外 THIRD_PARTY_ERROR（运营侧已认证，下游故障为第三方错误）
        when(accountClient.listAccounts(any(AccountSearchWireRequest.class), anyInt(), anyInt()))
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> accountAppService.list(
                new AccountQuery(null, null, null, null, null, null, null),
                PageRequest.of(0, 20)))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }

    @Test
    void given_downstream400_when_list_then_throwBadRequest() {
        // identity 400（筛选参数非法，如时间区间倒置）→ admin 400（BAD_REQUEST）
        when(accountClient.listAccounts(any(AccountSearchWireRequest.class), anyInt(), anyInt()))
                .thenThrow(new OpenApiClientException(400, "{\"message\":\"bad range\"}"));

        assertThatThrownBy(() -> accountAppService.list(
                new AccountQuery(null, null, null, null, null, null, null),
                PageRequest.of(0, 20)))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.BAD_REQUEST);
    }

    @Test
    void given_downstream404_when_list_then_throwNotFound() {
        when(accountClient.listAccounts(any(AccountSearchWireRequest.class), anyInt(), anyInt()))
                .thenThrow(new OpenApiClientException(404, "{\"message\":\"not found\"}"));

        assertThatThrownBy(() -> accountAppService.list(
                new AccountQuery(null, null, null, null, null, null, null),
                PageRequest.of(0, 20)))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.NOT_FOUND);
    }

    // ========== management detail · DTO 映射 + 错误翻译 ==========

    @Test
    void given_managementDetail_when_get_then_returnMappedDetail() {
        when(accountClient.getManagementDetail(1001L)).thenReturn(
                new AccountWireResponse(1001L, "alice@example.com", "13800000001",
                        "爱丽丝", "https://cdn/avatar1.png", 1, false, true));

        AccountManagementDetailResponse detail = accountAppService.getManagementDetail(1001L);

        // DTO 映射：wire → detail response 逐字段（与列表项同构）
        assertThat(detail.userId()).isEqualTo(1001L);
        assertThat(detail.email()).isEqualTo("alice@example.com");
        assertThat(detail.phone()).isEqualTo("13800000001");
        assertThat(detail.nickname()).isEqualTo("爱丽丝");
        assertThat(detail.avatar()).isEqualTo("https://cdn/avatar1.png");
        assertThat(detail.status()).isEqualTo(1);          // ACTIVE
        assertThat(detail.locked()).isFalse();
        assertThat(detail.hasPassword()).isTrue();
    }

    @Test
    void given_downstream404_when_getManagementDetail_then_throwNotFound() {
        // identity 404（账号不存在）→ admin 404（NOT_FOUND）
        when(accountClient.getManagementDetail(9999L))
                .thenThrow(new OpenApiClientException(404, "{\"message\":\"not found\"}"));

        assertThatThrownBy(() -> accountAppService.getManagementDetail(9999L))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.NOT_FOUND);
    }

    @Test
    void given_downstream500_when_getManagementDetail_then_throwThirdPartyError() {
        when(accountClient.getManagementDetail(1001L))
                .thenThrow(new OpenApiClientException(500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> accountAppService.getManagementDetail(1001L))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.THIRD_PARTY_ERROR);
    }

    // ========== disable · 纯透传 wire + 错误翻译 ==========

    @Test
    void given_validReason_when_disable_then_passReasonAsWireAndNoRefetch() {
        // 纯透传：仅转发 {reason}，不回读（accountClient.disable 为 void，mock 默认 no-op = 封号成功）
        accountAppService.disable(1001L, "违规账号，多次刷单");

        // wire 映射：command.reason → AccountReasonWireRequest.reason（不含 operator 身份——经 RequestContext 透传）
        verify(accountClient).disable(eq(1001L), eq(new AccountReasonWireRequest("违规账号，多次刷单")));
        // 纯透传不回读：写操作不触发 getManagementDetail（避免「成功却因回读抖动报错」）
        verify(accountClient, never()).getManagementDetail(any());
    }

    @Test
    void given_downstream404_when_disable_then_throwNotFound() {
        // identity 404（账号不存在）→ admin 404（NOT_FOUND）
        doThrow(new OpenApiClientException(404, "{\"message\":\"not found\"}"))
                .when(accountClient).disable(eq(9999L), any(AccountReasonWireRequest.class));

        assertThatThrownBy(() -> accountAppService.disable(9999L, "x"))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.NOT_FOUND);
    }

    @Test
    void given_downstream400_when_disable_then_throwBadRequest() {
        // identity 400（reason 缺失——被 @NotBlank 兜，此为下游兜底）→ admin 400（BAD_REQUEST）
        doThrow(new OpenApiClientException(400, "{\"message\":\"reason required\"}"))
                .when(accountClient).disable(eq(1001L), any(AccountReasonWireRequest.class));

        assertThatThrownBy(() -> accountAppService.disable(1001L, "x"))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.BAD_REQUEST);
    }

    // ========== activate / unlock · 纯透传 wire（reason 可空）+ 错误翻译 ==========

    @Test
    void given_optionalReason_when_activate_then_passReasonAsWireAndNoRefetch() {
        accountAppService.activate(1001L, "申诉成功");

        verify(accountClient).activate(eq(1001L), eq(new AccountReasonWireRequest("申诉成功")));
        verify(accountClient, never()).getManagementDetail(any());
    }

    @Test
    void given_nullReason_when_activate_then_passNullReason() {
        // activate/unlock reason 可空：前端不带 body 时 reason 为 null
        accountAppService.activate(1001L, null);

        verify(accountClient).activate(eq(1001L), eq(new AccountReasonWireRequest(null)));
        verify(accountClient, never()).getManagementDetail(any());
    }

    @Test
    void given_validReason_when_unlock_then_passReasonAsWireAndNoRefetch() {
        accountAppService.unlock(1001L, "风控误判");

        verify(accountClient).unlock(eq(1001L), eq(new AccountReasonWireRequest("风控误判")));
        verify(accountClient, never()).getManagementDetail(any());
    }

    @Test
    void given_downstream404_when_unlock_then_throwNotFound() {
        doThrow(new OpenApiClientException(404, "{\"message\":\"not found\"}"))
                .when(accountClient).unlock(eq(9999L), any(AccountReasonWireRequest.class));

        assertThatThrownBy(() -> accountAppService.unlock(9999L, null))
                .isInstanceOf(DomainException.class)
                .matches(e -> ((DomainException) e).getCodeMessage() == BaseCodeMessage.NOT_FOUND);
    }
}
