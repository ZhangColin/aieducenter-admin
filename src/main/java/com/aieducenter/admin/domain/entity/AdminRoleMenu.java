package com.aieducenter.admin.domain.entity;

import java.util.Objects;

import jakarta.persistence.*;
import lombok.Getter;

import com.cartisan.core.domain.DomainEntity;

/**
 * 角色-菜单关联实体。
 *
 * <p>属于 AdminRole 聚合的一部分，无独立审计字段。</p>
 * <p>关联删除采用物理删除，避免唯一索引与软删除冲突。</p>
 *
 * @since 0.1.0
 */
@Entity
@Table(name = "sys_admin_role_menus", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"role_id", "menu_id"})
})
@Getter
public class AdminRoleMenu implements DomainEntity<AdminRoleMenu, Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    /**
     * JPA 默认构造函数。
     */
    protected AdminRoleMenu() {
    }

    /**
     * 创建关联。
     */
    public AdminRoleMenu(Long roleId, Long menuId) {
        this.roleId = roleId;
        this.menuId = Objects.requireNonNull(menuId, "menuId must not be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AdminRoleMenu that = (AdminRoleMenu) o;
        return Objects.equals(roleId, that.roleId) && Objects.equals(menuId, that.menuId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleId, menuId);
    }
}
