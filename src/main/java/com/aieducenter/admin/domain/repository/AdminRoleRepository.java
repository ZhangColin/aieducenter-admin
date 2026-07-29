package com.aieducenter.admin.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aieducenter.admin.domain.aggregate.AdminRole;
import com.cartisan.data.jpa.repository.BaseRepository;

/**
 * 角色仓储接口。
 *
 * @since 0.1.0
 */
public interface AdminRoleRepository extends BaseRepository<AdminRole, Long> {

    Optional<AdminRole> findByCode(String code);

    /**
     * 批量按 id 查询未软删角色。
     *
     * <p>显式排除软删：{@code admin_user_role} 关联行无软删标志，角色被软删后关联仍在，
     * 此类残留关联对应的角色不得回显。过滤显式进行，不依赖框架级 {@code @SQLRestriction}
     * （其当前不生效，见 cartisan-boot#2）——显式过滤在框架修复落地前后行为一致。</p>
     */
    List<AdminRole> findByIdInAndDeletedFalse(Collection<Long> ids);

    /**
     * 检查角色是否被管理员使用。
     */
    @Query("SELECT COUNT(ur) > 0 FROM AdminUserRole ur WHERE ur.roleId = :roleId")
    boolean isUsedByAnyAdmin(@Param("roleId") Long roleId);
}
