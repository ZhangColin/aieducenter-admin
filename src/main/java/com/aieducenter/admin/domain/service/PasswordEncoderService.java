package com.aieducenter.admin.domain.service;

import com.cartisan.core.stereotype.DomainService;
import com.aieducenter.admin.domain.port.PasswordEncoder;

/**
 * 密码编码领域服务。
 *
 * <p>负责密码加密和验证逻辑。</p>
 * <p>封装南向端口，提供领域层的密码处理能力。</p>
 *
 * @since 0.1.0
 */
@DomainService
public class PasswordEncoderService {

    private final PasswordEncoder encoder;

    public PasswordEncoderService(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    /**
     * 加密明文密码。
     *
     * @param plainPassword 明文密码
     * @return 加密后的密码
     */
    public String encodePassword(String plainPassword) {
        return encoder.encode(plainPassword);
    }

    /**
     * 验证密码。
     *
     * @param plainPassword 明文密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    public boolean verifyPassword(String plainPassword, String encodedPassword) {
        return encoder.matches(plainPassword, encodedPassword);
    }
}