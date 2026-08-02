package com.aieducenter.admin.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.aieducenter.admin.domain.enums.AdminRoleStatus;
import com.cartisan.data.jpa.repository.BaseRepository;

/**
 * 角色仓储接口。
 *
 * @since 0.1.0
 */
public interface AdminRoleRepository extends BaseRepository<AdminRole, Long> {

    Optional<AdminRole> findByCode(String code);

    /**
     * 按状态查询角色（{@code GET /roles/all} 字典：仅启用），按 sortOrder 升序、id 升序兜底
     * （对齐菜单侧排序先例，issue #19）。
     */
    List<AdminRole> findByStatusOrderBySortOrderAscIdAsc(AdminRoleStatus status);

    /**
     * 批量按 id 查询角色（用户列表/详情的角色摘要回显：本页角色 ID 一次取齐，无 N+1）。
     */
    List<AdminRole> findByIdIn(Collection<Long> ids);

    /**
     * 批量按 id + 状态查询角色。
     *
     * <p>汇总聚合（roleCodes/permissions/menus）专用：禁用角色视为不存在（CONTEXT.md「RBAC」
     * 条目决策①，issue #21），调用方传 {@code AdminRoleStatus.ENABLED}。</p>
     */
    List<AdminRole> findByIdInAndStatus(Collection<Long> ids, AdminRoleStatus status);

    /**
     * 检查角色是否被管理员使用。
     */
    @Query("SELECT COUNT(ur) > 0 FROM AdminUserRole ur WHERE ur.roleId = :roleId")
    boolean isUsedByAnyAdmin(@Param("roleId") Long roleId);
}
