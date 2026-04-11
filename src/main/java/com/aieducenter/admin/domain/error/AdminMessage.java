package com.aieducenter.admin.domain.error;

import com.cartisan.core.exception.CodeMessage;

/**
 * Admin 模块消息定义。
 *
 * <h3>消息分类</h3>
 * <ul>
 *   <li>格式校验错误 (400): USERNAME_INVALID, PASSWORD_WEAK</li>
 *   <li>唯一性错误 (409): USERNAME_ALREADY_EXISTS, ROLE_CODE_ALREADY_EXISTS, PERMISSION_CODE_ALREADY_EXISTS</li>
 *   <li>密码错误 (400): PASSWORD_INCORRECT</li>
 *   <li>资源不存在 (404): ADMIN_NOT_FOUND, ROLE_NOT_FOUND, MENU_NOT_FOUND, PERMISSION_NOT_FOUND</li>
 *   <li>登录错误 (401): LOGIN_FAILED, ADMIN_DISABLED</li>
 *   <li>业务限制 (403): LAST_ADMIN_CANNOT_DELETE, ROLE_IN_USE, SUPER_ADMIN_CANNOT_DELETE</li>
 *   <li>菜单限制 (403): MENU_HAS_CHILDREN, MENU_DEPTH_EXCEEDED, MENU_INVALID_PARENT</li>
 * </ul>
 *
 * @since 0.1.0
 */
public enum AdminMessage implements CodeMessage {

    // ========== 格式校验错误 (400) ==========

    /**
     * 用户名格式不正确。
     */
    USERNAME_INVALID(400, "ADMIN_001", "用户名格式不正确"),

    /**
     * 密码强度不足。
     */
    PASSWORD_WEAK(400, "ADMIN_002", "密码强度不足"),

    // ========== 唯一性错误 (409) ==========

    /**
     * 用户名已存在。
     */
    USERNAME_ALREADY_EXISTS(409, "ADMIN_003", "用户名已存在"),

    /**
     * 角色编码已存在。
     */
    ROLE_CODE_ALREADY_EXISTS(409, "ADMIN_004", "角色编码已存在"),

    /**
     * 权限编码已存在。
     */
    PERMISSION_CODE_ALREADY_EXISTS(409, "ADMIN_004_1", "权限编码已存在"),

    // ========== 密码错误 (400) ==========

    /**
     * 密码错误。
     */
    PASSWORD_INCORRECT(400, "ADMIN_005", "密码错误"),

    // ========== 资源不存在 (404) ==========

    /**
     * 管理员不存在。
     */
    ADMIN_NOT_FOUND(404, "ADMIN_006", "管理员不存在"),

    /**
     * 角色不存在。
     */
    ROLE_NOT_FOUND(404, "ADMIN_007", "角色不存在"),

    /**
     * 菜单不存在。
     */
    MENU_NOT_FOUND(404, "ADMIN_008", "菜单不存在"),

    /**
     * 权限不存在。
     */
    PERMISSION_NOT_FOUND(404, "ADMIN_009", "权限不存在"),

    // ========== 登录错误 (401) ==========

    /**
     * 登录失败。
     */
    LOGIN_FAILED(401, "ADMIN_010", "用户名或密码错误"),

    /**
     * 管理员已被禁用。
     */
    ADMIN_DISABLED(401, "ADMIN_011", "管理员已被禁用"),

    // ========== 业务限制 (403) ==========

    /**
     * 不能删除最后一个管理员。
     */
    LAST_ADMIN_CANNOT_DELETE(403, "ADMIN_012", "不能删除最后一个管理员"),

    /**
     * 角色正在使用中，不能删除。
     */
    ROLE_IN_USE(403, "ADMIN_013", "角色正在使用中，不能删除"),

    /**
     * 菜单有子菜单，不能删除。
     */
    MENU_HAS_CHILDREN(403, "ADMIN_014", "菜单有子菜单，不能删除"),

    /**
     * 菜单层级超限。
     */
    MENU_DEPTH_EXCEEDED(403, "ADMIN_014_1", "菜单层级不能超过3级"),

    /**
     * 菜单父级设置无效。
     */
    MENU_INVALID_PARENT(403, "ADMIN_014_2", "不能将菜单设置为自己的父级或后代"),

    /**
     * 超级管理员角色不能删除。
     */
    SUPER_ADMIN_CANNOT_DELETE(403, "ADMIN_013_1", "超级管理员角色不能删除");

    private final int httpStatus;
    private final String code;
    private final String message;

    AdminMessage(int httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
