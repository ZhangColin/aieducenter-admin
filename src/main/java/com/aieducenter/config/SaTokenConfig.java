package com.aieducenter.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cn.dev33.satoken.stp.StpInterface;

import com.aieducenter.admin.application.AdminUserPermissionAppService;
import com.cartisan.security.authorization.AuthorizationBypassResolver;

/**
 * Sa-Token 配置。
 *
 * <p>为统一后台配置 {@link StpInterface} 实现，按管理员角色/权限返回权限与角色编码；并声明超管授权 bypass
 * （消费 cartisan-security 的 {@link AuthorizationBypassResolver} SPI）。</p>
 *
 * <p>本应用只有运营用户（Operator）一种登录身份，统一使用 Sa-Token 默认 loginType（{@code "login"}），
 * 不再按 loginType 区分——该区分源自旧单体（运营与终端用户同进程）时代，admin 独立成仓后已无意义
 * （见 ADR-0001）。故 {@link StpInterface} 无条件返回 admin 权限/角色。</p>
 *
 * <p>超管 bypass：admin 只声明"谁是超管"（委托 {@link AdminUserPermissionAppService#isSuperAdmin}），
 * bypass 语义归框架——{@code SecurityInterceptor} 在确认登录后、检查角色/权限前询问此 resolver，命中则跳过
 * {@code @RequireRole}/{@code @RequirePermission}（{@code @RequireAuth} 登录要求不受影响，无后门）。
 * 见 ADR-0002。</p>
 */
@Configuration
public class SaTokenConfig {

    private final AdminUserPermissionAppService adminPermissionAppService;

    public SaTokenConfig(AdminUserPermissionAppService adminPermissionAppService) {
        this.adminPermissionAppService = adminPermissionAppService;
    }

    @Bean
    public StpInterface cartisanStpInterface() {
        return new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return adminPermissionAppService.getPermissions(toAdminId(loginId));
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return adminPermissionAppService.getRoleCodes(toAdminId(loginId));
            }

            /**
             * Sa-Token 经 Redis-Jackson 存取 loginId，反序列化后可能是 String / Number；
             * 统一收敛为 Long（admin 主键类型）。
             */
            private Long toAdminId(Object loginId) {
                if (loginId instanceof Number number) {
                    return number.longValue();
                }
                return Long.valueOf(loginId.toString());
            }
        };
    }

    /**
     * 超管授权 bypass：有 {@code SUPER_ADMIN} 角色即跳过 {@code @RequireRole} / {@code @RequirePermission}。
     *
     * <p>框架 SPI 的 javadoc 即以此 bean 为示例。判定标准（"谁是超管"）完全由应用决定，框架不特化业务概念。
     * 注意：框架在每个鉴权请求调用一次、不缓存；{@code isSuperAdmin} 现按请求查库，对齐 Sa-Token
     * {@code getPermissionList} 的 session 缓存模式的优化留待将来。</p>
     */
    @Bean
    public AuthorizationBypassResolver superAdminAuthorizationBypassResolver() {
        return adminPermissionAppService::isSuperAdmin;
    }
}
