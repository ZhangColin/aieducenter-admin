package com.aieducenter.admin.domain.aggregate;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.cartisan.core.domain.AggregateRoot;
import com.cartisan.core.stereotype.Aggregate;
import static com.cartisan.core.util.Assertions.require;
import com.cartisan.data.jpa.domain.Auditable;
import com.cartisan.data.jpa.id.TsidGenerator;
import com.aieducenter.admin.domain.entity.AdminRoleMenu;
import com.aieducenter.admin.domain.entity.AdminRolePermission;
import com.aieducenter.admin.domain.enums.AdminRoleStatus;
import com.aieducenter.admin.domain.error.AdminMessage;

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
public class AdminRole extends Auditable implements AggregateRoot<AdminRole, Long> {

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
     * 角色状态（启用/禁用）。仅经 {@link #disable()} / {@link #enable()} 变更——
     * 无 @Setter，避免绕过 SUPER_ADMIN 守卫直写。
     */
    @Getter
    @Column(name = "status", nullable = false)
    private AdminRoleStatus status = AdminRoleStatus.ENABLED;

    /**
     * 默认首页 route name（登录后按角色落地页，Soybean home）。可空。
     */
    @Setter
    @Getter
    @Column(name = "home", length = 100)
    private String home;

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
     * 删除前守卫（应用服务在 {@code repository.delete()} 之前显式调用）。
     *
     * <p>SUPER_ADMIN 角色不可删——它是破窗号（内置 {@code admin}）的全权救援角色；
     * 删了则破窗号虽在、救援能力失效，仍会锁死（ADR-0003 修订）。守卫在聚合内单一执行点，
     * 仿 {@link AdminUser#requireDeletable()} 破窗号 guard。
     * 本应用删除为物理删除（ADR-0005），守卫不再挂框架软删入口（{@code markAsDeleted} 已随
     * 软删基类一并移除——框架对「有 markAsDeleted 方法但非 SoftDeletable」的实体走反射软存，
     * 残留该方法会使物理删除失效）。</p>
     */
    public void requireDeletable() {
        require(!isSuperAdmin(), AdminMessage.SUPER_ADMIN_CANNOT_DELETE);
    }

    /**
     * 禁用角色。SUPER_ADMIN 角色不可禁（同理保救援角色永不失效）。
     */
    public void disable() {
        require(!isSuperAdmin(), AdminMessage.SUPER_ADMIN_CANNOT_DISABLE);
        this.status = AdminRoleStatus.DISABLED;
    }

    /**
     * 启用角色。
     */
    public void enable() {
        this.status = AdminRoleStatus.ENABLED;
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
