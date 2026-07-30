package com.aieducenter.admin.domain.aggregate;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.cartisan.core.domain.AggregateRoot;
import com.cartisan.core.stereotype.Aggregate;
import com.cartisan.data.jpa.domain.AuditableSoftDeletable;
import com.cartisan.data.jpa.id.TsidGenerator;
import com.aieducenter.admin.domain.entity.AdminRoleMenu;
import com.aieducenter.admin.domain.entity.AdminRolePermission;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * AdminRole 聚合根。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>封装角色状态</li>
 *   <li>管理角色关联的菜单和权限</li>
 * </ul>
 *
 * @since 0.1.0
 */
@Entity
@Table(name = "sys_admin_roles")
@Aggregate
public class AdminRole extends AuditableSoftDeletable implements AggregateRoot<AdminRole, Long> {

    /**
     * 超级管理员角色码。
     */
    public static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    @Getter
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Setter
    @Getter
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @Setter
    @Getter
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Setter
    @Getter
    @Column(name = "description", length = 255)
    private String description;

    @Setter
    @Getter
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /**
     * 角色-菜单关联（聚合内实体）。
     *
     * <p>{@code @JoinColumn} 标记只读（{@code insertable=false, updatable=false}）：role_id 列由
     * {@link AdminRoleMenu#getRoleId()} 属性独占可写。否则两侧双写时，{@link #clearMenus()}
     * 移除关联会走「UPDATE role_id=null 解引用」而非 orphanRemoval 的 DELETE，在 role_id NOT NULL
     * 上必现违例——重新分配（clear + add）整路径失败（REQ-7）。与 {@link AdminUser} 的 userRoles 同模式。</p>
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private Set<AdminRoleMenu> roleMenus = new HashSet<>();

    /**
     * 角色-权限关联（聚合内实体）。同 {@link #roleMenus}，JoinColumn 只读以避免双写 orphanRemoval 撞 NOT NULL。
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private Set<AdminRolePermission> rolePermissions = new HashSet<>();

    /**
     * 创建角色。
     */
    public AdminRole(String name, String code, String description, Integer sortOrder) {
        this.name = name;
        this.code = code;
        this.description = description;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
    }

    /**
     * JPA 默认构造函数。
     */
    protected AdminRole() {
    }

    /**
     * JPA 保存前生成 ID。
     */
    @PrePersist
    void prePersist() {
        if (id == null) {
            this.id = TsidGenerator.newInstance().generate();
        }
    }

    // ========== Getter ==========

    public Set<Long> getMenuIds() {
        return roleMenus.stream()
                .map(AdminRoleMenu::getMenuId)
                .collect(Collectors.toSet());
    }

    public Set<String> getPermissionCodes() {
        return rolePermissions.stream()
                .map(AdminRolePermission::getPermissionCode)
                .collect(Collectors.toSet());
    }

    public Set<AdminRoleMenu> getRoleMenus() {
        return roleMenus;
    }

    public Set<AdminRolePermission> getRolePermissions() {
        return rolePermissions;
    }

    // ========== 业务行为 ==========

    /**
     * 是否为超级管理员角色。
     */
    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(this.code);
    }

    /**
     * 添加菜单关联。
     */
    public void addMenu(Long menuId) {
        roleMenus.add(new AdminRoleMenu(this.id, menuId));
    }

    /**
     * 清除所有菜单关联。
     */
    public void clearMenus() {
        roleMenus.clear();
    }

    /**
     * 添加权限关联。
     */
    public void addPermission(String permissionCode, String permissionName) {
        rolePermissions.add(new AdminRolePermission(this.id, permissionCode, permissionName));
    }

    /**
     * 清除所有权限关联。
     */
    public void clearPermissions() {
        rolePermissions.clear();
    }
}
