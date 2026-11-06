package com.community.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器。
 *
 * <p>BCrypt 的三个性质：
 * <ul>
 *   <li>单向不可逆：只有 encode / matches，没有 decode；忘记密码只能重置</li>
 *   <li>自动加盐：每次 encode 都产生随机盐，同一密码两次结果不同 → 彩虹表失效</li>
 *   <li>成本可调：strength 每 +1，计算量翻倍</li>
 * </ul>
 *
 * <p>strength 的取舍：10 ≈ 单次 50-100ms，能阻挡批量爆破，又不至于把登录接口拖垮。
 * 调高前先压测——它占的是 CPU，高峰期登录接口会直接受形响。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder(
            @Value("${app.security.bcrypt-strength:10}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }
}
