package com.aieducenter.admin.domain.aggregate;

import java.util.List;

import cn.hutool.core.collection.CollUtil;

import com.cartisan.core.domain.AggregateRoot;
import com.cartisan.core.stereotype.Aggregate;

import com.cartisan.data.jpa.domain.AuditableSoftDeletable;
import com.cartisan.data.jpa.id.TsidGenerator;
import com.aieducenter.admin.domain.entity.MenuQueryParam;
import com.aieducenter.admin.domain.enums.AdminUserStatus;
import com.aieducenter.admin.domain.enums.MenuIconType;
import com.aieducenter.admin.domain.enums.MenuType;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * AdminMenu 聚合根——菜单 = Soybean 路由生成器数据源（见 ADR-0004）。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>承载 Soybean {@code @elegant-router} 渲染路由所需的全部元数据</li>
 *   <li>支持树形结构（{@link #MAX_DEPTH} 级，供父级选择器/角色分配）</li>
 * </ul>
 *
 * <p>{@code menuType} 两值：{@link MenuType#DIRECTORY directory}(1) 容器 /
 * {@link MenuType#MENU menu}(2) 叶子。{@code type}/{@code routePath}/{@code icon}
 * 等语义一律以 Soybean 源码为准——后端透传、不自创 path 不变量（旧 {@code ADMIN_014_3} 已废）。</p>
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

    @Getter
    @Setter
    @Column(name = "menu_name", nullable = false, length = 100)
    private String menuName;

    @Getter
    @Setter
    @Column(name = "route_name", length = 100)
    private String routeName;

    @Getter
    @Setter
    @Column(name = "route_path", length = 255)
    private String routePath;

    @Getter
    @Setter
    @Column(name = "component", length = 255)
    private String component;

    @Getter
    @Setter
    @Column(name = "icon", length = 100)
    private String icon;

    @Getter
    @Setter
    @Column(name = "icon_type", nullable = false)
    private MenuIconType iconType = MenuIconType.ICONIFY;

    @Getter
    @Setter
    @Column(name = "parent_id")
    private Long parentId;

    @Getter
    @Setter
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Getter
    @Column(name = "menu_type", nullable = false)
    private MenuType menuType = MenuType.MENU;

    @Getter
    @Setter
    @Column(name = "i18n_key", length = 100)
    private String i18nKey;

    @Getter
    @Setter
    @Column(name = "keep_alive", nullable = false)
    private boolean keepAlive = false;

    @Getter
    @Setter
    @Column(name = "constant", nullable = false)
    private boolean constant = false;

    @Getter
    @Setter
    @Column(name = "multi_tab", nullable = false)
    private boolean multiTab = false;

    @Getter
    @Setter
    @Column(name = "hide_in_menu", nullable = false)
    private boolean hideInMenu = false;

    @Getter
    @Setter
    @Column(name = "active_menu", length = 100)
    private String activeMenu;

    @Getter
    @Setter
    @Column(name = "href", length = 255)
    private String href;

    @Getter
    @Setter
    @Column(name = "fixed_index_in_tab")
    private Integer fixedIndexInTab;

    @Getter
    @Setter
    @Column(name = "query", columnDefinition = "text")
    @Convert(converter = com.aieducenter.admin.domain.entity.MenuQueryParamConverter.class)
    private List<MenuQueryParam> query = CollUtil.newArrayList();

    @Getter
    @Setter
    @Column(name = "status", nullable = false)
    private AdminUserStatus status = AdminUserStatus.ACTIVE;

    // 子菜单（不持久化，查询时组装）
    @Transient
    private List<AdminMenu> children = CollUtil.newArrayList();

    /**
     * 创建菜单（承载 Soybean 路由生成器全字段）。
     */
    public AdminMenu(String menuName, String routeName, String routePath, String component,
                     String icon, MenuIconType iconType, Long parentId, Integer sortOrder, MenuType menuType,
                     String i18nKey, boolean keepAlive, boolean constant, boolean multiTab, boolean hideInMenu,
                     String activeMenu, String href, Integer fixedIndexInTab,
                     List<MenuQueryParam> query, AdminUserStatus status) {
        applyFields(menuName, routeName, routePath, component, icon, iconType, parentId, sortOrder, menuType,
                i18nKey, keepAlive, constant, multiTab, hideInMenu, activeMenu, href, fixedIndexInTab, query, status);
    }

    /**
     * 整体更新字段（应用层调用）。
     */
    public void updateDetails(String menuName, String routeName, String routePath, String component,
                              String icon, MenuIconType iconType, Long parentId, Integer sortOrder, MenuType menuType,
                              String i18nKey, boolean keepAlive, boolean constant, boolean multiTab, boolean hideInMenu,
                              String activeMenu, String href, Integer fixedIndexInTab,
                              List<MenuQueryParam> query, AdminUserStatus status) {
        applyFields(menuName, routeName, routePath, component, icon, iconType, parentId, sortOrder, menuType,
                i18nKey, keepAlive, constant, multiTab, hideInMenu, activeMenu, href, fixedIndexInTab, query, status);
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

    // ========== Getter（含业务逻辑的手写访问器） ==========

    public List<AdminMenu> getChildren() {
        return children;
    }

    // ========== Setter（含业务逻辑的手写修改器） ==========

    public void setMenuType(MenuType menuType) {
        this.menuType = menuType != null ? menuType : MenuType.MENU;
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

    /**
     * 是否启用。
     */
    public boolean isEnabled() {
        return this.status == AdminUserStatus.ACTIVE;
    }

    // ========== 私有方法 ==========

    /**
     * 统一赋值 Soybean 路由生成器字段（缺省归一，无 path 不变量）。
     */
    private void applyFields(String menuName, String routeName, String routePath, String component,
                             String icon, MenuIconType iconType, Long parentId, Integer sortOrder, MenuType menuType,
                             String i18nKey, boolean keepAlive, boolean constant, boolean multiTab, boolean hideInMenu,
                             String activeMenu, String href, Integer fixedIndexInTab,
                             List<MenuQueryParam> query, AdminUserStatus status) {
        this.menuName = menuName;
        this.routeName = routeName;
        this.routePath = routePath;
        this.component = component;
        this.icon = icon;
        this.iconType = iconType != null ? iconType : MenuIconType.ICONIFY;
        this.parentId = parentId;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.menuType = menuType != null ? menuType : MenuType.MENU;
        this.i18nKey = i18nKey;
        this.keepAlive = keepAlive;
        this.constant = constant;
        this.multiTab = multiTab;
        this.hideInMenu = hideInMenu;
        this.activeMenu = activeMenu;
        this.href = href;
        this.fixedIndexInTab = fixedIndexInTab;
        this.query = query != null ? query : CollUtil.newArrayList();
        this.status = status != null ? status : AdminUserStatus.ACTIVE;
    }
}
