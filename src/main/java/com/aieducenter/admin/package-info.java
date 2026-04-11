/**
 * Platform Admin Context。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>平台运营人员管理（管理员、角色、权限、菜单）</li>
 *   <li>管理员登录认证</li>
 *   <li>RBAC 权限体系</li>
 * </ul>
 *
 * <h3>限界上下文</h3>
 * <p>平台运营后台，独立于 Account Context 和 Tenant Context</p>
 *
 * <h3>包结构</h3>
 * <ul>
 *   <li>domain - 领域层：聚合根（AdminUser、AdminRole、AdminMenu）、实体（AdminUserRole）、仓储接口、领域服务、枚举、端口接口</li>
 *   <li>application - 应用层：应用服务（AdminUserManagementAppService、AdminUserAuthAppService 等）、DTO</li>
 *   <li>infrastructure - 基础设施层：持久化、缓存、消息队列等南向接口适配器</li>
 *   <li>endpoints - 北向接口适配器层：REST API（Controller）、外部 API、MQ Listener、定时任务</li>
 * </ul>
 *
 * @since 0.1.0
 */
@com.cartisan.core.stereotype.BoundedContext(name = "PlatformAdmin", subDomain = com.cartisan.core.stereotype.SubDomain.SUPPORTING)
package com.aieducenter.admin;
