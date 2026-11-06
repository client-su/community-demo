package com.community.common;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 安全相关的 Redis 操作：目前只有 JWT 黑名单。
 *
 * <p>为什么需要它：JWT 无状态、签出去就收不回。登出/踢人只能靠"黑名单"——
 * 服务端记住被作废的 jti，鉴权时查一下。
 */
@Service
public class SecurityRedisService {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redis;

    public SecurityRedisService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 把 jti 加入黑名单。
     *
     * <p>TTL = token 剩余有效期：token 一过期，JWT 验签自己就会拒绝，
     * 黑名单记录留着毫无意义。这样黑名单不会无限膨胀。
     */
    public void blacklistJti(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofSeconds(1);
        }
        redis.opsForValue().set(BLACKLIST_PREFIX + jti, "1", ttl);
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + jti));
    }

    /** 供测试/运维查看剩余 TTL（秒），不存在返回 -2 */
    public long blacklistTtlSeconds(String jti) {
        Long ttl = redis.getExpire(BLACKLIST_PREFIX + jti);
        return ttl == null ? -2L : ttl;
    }
}
