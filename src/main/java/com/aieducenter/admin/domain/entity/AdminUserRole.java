package com.aieducenter.admin.domain.entity;

import java.util.Objects;

import jakarta.persistence.*;
import lombok.Getter;

import com.cartisan.core.domain.DomainEntity;

/**
 * 管理员-角色关联实体。
 *
 * <p>属于 AdminUser 聚合的一部分，无独立审计字段。</p>
 * <p>关联删除采用物理删除，避免唯一索引与软删除冲突。</p>
 *
 * @since 0.1.0
 */
@Entity
@Table(name = "sys_admin_user_roles", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"admin_id", "role_id"})
})
@Getter
public class AdminUserRole implements DomainEntity<AdminUserRole, Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    /**
     * JPA 默认构造函数。
     */
    protected AdminUserRole() {
    }

    /**
     * 创建关联。
     */
    public AdminUserRole(Long adminId, Long roleId) {
        this.adminId = adminId;
        this.roleId = roleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AdminUserRole that = (AdminUserRole) o;
        return Objects.equals(adminId, that.adminId) && Objects.equals(roleId, that.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(adminId, roleId);
    }
}
