package com.aieducenter.admin.domain.repository;

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
     * 检查角色是否被管理员使用。
     */
    @Query("SELECT COUNT(ur) > 0 FROM AdminUserRole ur WHERE ur.roleId = :roleId")
    boolean isUsedByAnyAdmin(@Param("roleId") Long roleId);
}
