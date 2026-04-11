package com.aieducenter.admin.domain.aggregate;

import java.util.List;

import cn.hutool.core.collection.CollUtil;

import com.cartisan.core.domain.AggregateRoot;
import com.cartisan.core.stereotype.Aggregate;
import com.cartisan.data.jpa.domain.AuditableSoftDeletable;
import com.cartisan.data.jpa.id.TsidGenerator;
import com.aieducenter.admin.domain.enums.MenuType;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * AdminMenu 聚合根。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>封装菜单状态</li>
 *   <li>支持树形结构（最多3级）</li>
 * </ul>
 *
 * @since 0.1.0
 */
@Entity
@Table(name = "sys_admin_menus")
@Aggregate
public class AdminMenu extends AuditableSoftDeletable implements AggregateRoot<AdminMenu, Long> {

    public static final int MAX_DEPTH = 3;

    @Getter
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Setter
    @Getter
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Setter
    @Getter
    @Column(name = "path", length = 255)
    private String path;

    @Setter
    @Getter
    @Column(name = "icon", length = 50)
    private String icon;

    @Setter
    @Getter
    @Column(name = "parent_id")
    private Long parentId;

    @Setter
    @Getter
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Getter
    @Column(name = "type", nullable = false)
    private MenuType type = MenuType.MENU;

    // 子菜单（不持久化，查询时组装）
    @Transient
    private List<AdminMenu> children = CollUtil.newArrayList();

    /**
     * 创建菜单。
     */
    public AdminMenu(String name, String path, String icon, Long parentId, Integer sortOrder) {
        this.name = name;
        this.path = path;
        this.icon = icon;
        this.parentId = parentId;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
    }

    /**
     * JPA 默认构造函数。
     */
    protected AdminMenu() {
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

    public List<AdminMenu> getChildren() {
        return children;
    }

    // ========== Setter ==========

    public void setType(MenuType type) {
        this.type = type != null ? type : MenuType.MENU;
    }

    public void setChildren(List<AdminMenu> children) {
        this.children = children != null ? children : CollUtil.newArrayList();
    }

    // ========== 业务行为 ==========

    /**
     * 添加子菜单。
     */
    public void addChild(AdminMenu child) {
        this.children.add(child);
    }

    /**
     * 是否为根菜单。
     */
    public boolean isRoot() {
        return this.parentId == null;
    }
}
