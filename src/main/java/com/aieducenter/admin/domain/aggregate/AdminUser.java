package com.aieducenter.admin.domain.aggregate;

import java.util.Set;
import java.util.stream.Collectors;

import cn.hutool.core.collection.CollUtil;


import com.cartisan.core.domain.AggregateRoot;
import com.cartisan.core.stereotype.Aggregate;

import static com.cartisan.core.util.Assertions.require;

import com.cartisan.data.jpa.domain.AuditableSoftDeletable;
import com.cartisan.data.jpa.id.TsidGenerator;
import com.aieducenter.admin.domain.entity.AdminUserRole;
import com.aieducenter.admin.domain.error.AdminMessage;
import com.aieducenter.admin.domain.enums.AdminUserStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * AdminUser 聚合根。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>封装管理员状态和行为</li>
 *   <li>管理登录凭证（用户名、密码）</li>
 *   <li>管理个人信息（昵称、邮箱、手机号、头像）</li>
 * </ul>
 *
 * <h3>不变量</h3>
 * <ul>
 *   <li>用户名不能为空且格式正确</li>
 *   <li>密码必须加密存储</li>
 * </ul>
 *
 * @since 0.1.0
 */
@Entity
@Table(name = "sys_admin_users")
@Aggregate
public class AdminUser extends AuditableSoftDeletable implements AggregateRoot<AdminUser, Long> {
    private static final String USERNAME_PATTERN = "^[a-zA-Z][a-zA-Z0-9_]{2,19}$";

    /**
     * 破窗账号的保留 ID（内置 {@code admin}）。
     *
     * <p>运维韧性层：保证"就算角色被改坏、管理员被删光/禁光，也总有一个救援号能登进来"的固定账号。
     * 识别方式为保留 ID（不靠列），与授权层（{@code SUPER_ADMIN} 角色）解耦。详见 CONTEXT.md「破窗账号」。</p>
     */
    public static final long BREAK_GLASS_ADMIN_ID = 1L;

    @Getter
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Getter
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Getter
    @Column(name = "password", nullable = false)
    private String password;

    @Getter
    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Getter
    @Setter
    @Column(name = "email", length = 255)
    private String email;

    @Getter
    @Setter
    @Column(name = "phone", length = 20)
    private String phone;

    @Getter
    @Setter
    @Column(name = "avatar", length = 512)
    private String avatar;

    @Getter
    @Column(name = "status", nullable = false)
    private AdminUserStatus status;

    /**
     * 用户-角色关联（聚合内实体）。
     *
     * <p>admin_id 列由 {@link AdminUserRole#getAdminId()} 属性独占可写映射，故此处
     * {@code @JoinColumn} 标记只读：若两侧同时可写（双写映射），从集合移除关联时 Hibernate
     * 会走「解引用」（UPDATE admin_id=null）而非 orphanRemoval 的 DELETE，在真实库的
     * NOT NULL 约束上必现 23502——重新分配角色（clearRoles + addRole）整体失败。
     * 只读后：插入由子实体属性写 admin_id，移除由 orphanRemoval 发 DELETE。</p>
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "admin_id", insertable = false, updatable = false)
    private final Set<AdminUserRole> userRoles = CollUtil.newHashSet();

    /**
     * 创建管理员。
     *
     * @param username        用户名（必填）
     * @param encodedPassword 加密后的密码（应用服务层已加密）
     * @param nickname        昵称
     */
    public AdminUser(String username, String encodedPassword, String nickname) {
        validateUsername(username);
        this.username = username;
        require(encodedPassword != null, AdminMessage.PASSWORD_WEAK);
        this.password = encodedPassword;
        this.nickname = nickname != null && !nickname.isBlank() ? nickname : username;
        this.status = AdminUserStatus.ACTIVE;
    }

    /**
     * JPA 默认构造函数。
     */
    protected AdminUser() {
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

    public boolean isActive() {
        return this.status == AdminUserStatus.ACTIVE;
    }

    /**
     * 是否为破窗账号（按保留 ID 判定，不入库、不靠列）。
     */
    public boolean isBreakGlass() {
        return this.id != null && this.id == BREAK_GLASS_ADMIN_ID;
    }

    // ========== 业务行为 ==========


    /**
     * 软删入口（框架 {@code BaseRepositoryImpl.delete} 经此方法执行软删）。
     *
     * <p>破窗账号不可删——保证救援入口永远存在。授权（谁是超管）走角色，与此韧性守卫解耦。</p>
     */
    @Override
    public void markAsDeleted() {
        require(!isBreakGlass(), AdminMessage.BREAK_GLASS_CANNOT_DELETE);
        super.markAsDeleted();
    }

    /**
     * 修改密码（已加密）。
     *
     * @param encodedPassword 加密后的密码
     */
    public void changePassword(String encodedPassword) {
        require(encodedPassword != null, AdminMessage.PASSWORD_WEAK);
        this.password = encodedPassword;
    }

    /**
     * 修改用户名。
     */
    public void updateUsername(String newUsername) {
        validateUsername(newUsername);
        this.username = newUsername;
    }

    /**
     * 修改昵称。
     */
    public void updateNickname(String nickname) {
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
    }


    /**
     * 禁用管理员。
     *
     * <p>破窗账号不可禁——避免把救援入口锁死。改由 {@link #enable()} / 改密恢复。</p>
     */
    public void disable() {
        require(!isBreakGlass(), AdminMessage.BREAK_GLASS_CANNOT_DISABLE);
        this.status = AdminUserStatus.DISABLED;
    }

    /**
     * 启用管理员。
     */
    public void enable() {
        this.status = AdminUserStatus.ACTIVE;
    }

    /**
     * 添加角色关联。
     */
    public void addRole(Long roleId) {
        userRoles.add(new AdminUserRole(this.id, roleId));
    }

    /**
     * 清除所有角色关联。
     */
    public void clearRoles() {
        userRoles.clear();
    }

    /**
     * 获取角色 ID 列表。
     */
    public Set<Long> getRoleIds() {
        return userRoles.stream()
                .map(AdminUserRole::getRoleId)
                .collect(Collectors.toSet());
    }

    // ========== 私有方法 ==========

    private void validateUsername(String username) {
        require(
                username != null && username.matches(USERNAME_PATTERN),
                AdminMessage.USERNAME_INVALID
        );
    }

}
