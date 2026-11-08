package com.community.common;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 固定窗口限流。
 *
 * <p>为什么用 Redis 计数而不是本地 AtomicInteger：
 * 多实例部署时本地计数相当于把阈值放大 N 倍，限流形同虚设。
 *
 * <p>已知缺陷：窗口边界处可能瞬间放行 2 倍流量（59s 打满 + 60s 再打满）。
 * 需要平滑时改用滑动窗口（ZSet 存时间戳）或令牌桶。
 */
@Service
public class RateLimitService {

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public record Decision(boolean allowed, long count, long limit, long retryAfterSeconds) {
    }

    /**
     * @param action   动作名，如 login、publish、like
     * @param subject  限流主体：IP 或 userId
     * @param limit    窗口内允许的次数
     * @param window   窗口长度
     */
    public Decision tryAcquire(String action, String subject, int limit, Duration window) {
        long windowSeconds = Math.max(1, window.toSeconds());
        long bucket = Instant.now().getEpochSecond() / windowSeconds;
        String key = "rate:" + action + ":" + subject + ":" + bucket;

        Long count = redis.opsForValue().increment(key);
        long used = count == null ? 0 : count;

        // 只在窗口的第一个请求上设置过期：每次都设会把窗口不断后移
        if (used == 1) {
            redis.expire(key, Duration.ofSeconds(windowSeconds));
        }

        // 距离本窗口结束还有多少秒
        long retryAfter = windowSeconds - (Instant.now().getEpochSecond() % windowSeconds);
        return new Decision(used <= limit, used, limit, retryAfter);
    }
}
