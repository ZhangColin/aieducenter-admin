package com.aieducenter.admin.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aieducenter.admin.domain.aggregate.AdminUser;
import com.cartisan.data.jpa.repository.BaseRepository;

/**
 * 管理员仓储接口。
 *
 * @since 0.1.0
 */
public interface AdminUserRepository extends BaseRepository<AdminUser, Long> {

    Optional<AdminUser> findByUsername(String username);

    boolean existsByUsername(String username);

    /**
     * 查询管理员是否拥有指定角色。
     */
    @Query("SELECT COUNT(ur) > 0 FROM AdminUserRole ur " +
           "WHERE ur.adminId = :adminId " +
           "AND EXISTS (SELECT 1 FROM AdminRole r WHERE r.id = ur.roleId AND r.code = :roleCode AND r.deleted = false)")
    boolean hasRole(@Param("adminId") Long adminId, @Param("roleCode") String roleCode);
}
