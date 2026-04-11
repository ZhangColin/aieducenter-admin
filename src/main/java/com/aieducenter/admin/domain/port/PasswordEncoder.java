package com.aieducenter.admin.domain.port;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * 密码编码器端口。
 *
 * <p>南向接口，定义密码编码能力。</p>
 * <p>使用 PortType.CLIENT 表示这是对基础设施服务的端口。</p>
 *
 * @since 0.1.0
 */
@Port(PortType.CLIENT)
public interface PasswordEncoder {

    /**
     * 加密密码。
     *
     * @param plainPassword 明文密码
     * @return 加密后的密码
     */
    String encode(String plainPassword);

    /**
     * 验证密码。
     *
     * @param plainPassword 明文密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    boolean matches(String plainPassword, String encodedPassword);
}