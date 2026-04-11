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

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "admin_id")
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

    // ========== 业务行为 ==========


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
     */
    public void disable() {
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
