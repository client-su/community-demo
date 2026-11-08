package com.community.like;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 点赞的去重与计数缓存。
 *
 * <p>Set 的唯一职责是"挡住重复请求"，不承担"数据真相"。
 * Redis 丢了这个集合，数据库 content_like 依然完整，可以重建。
 */
@Service
public class LikeRedisService {

    private static final String KEY_PREFIX = "like:content:";
    /** 冷内容自动过期释放；热内容每次写入会续期，长期驻留 */
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;

    public LikeRedisService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String key(long contentId) {
        return KEY_PREFIX + contentId;
    }

    /**
     * @return true = 本次新增（之前没赞过）；false = 已存在
     */
    public boolean add(long contentId, long userId) {
        Long added = redis.opsForSet().add(key(contentId), String.valueOf(userId));
        boolean isNew = added != null && added > 0;
        if (isNew) {
            redis.expire(key(contentId), TTL);
        }
        return isNew;
    }

    /**
     * @return true = 本次真的移除了；false = 本来就不在集合里
     */
    public boolean remove(long contentId, long userId) {
        Long removed = redis.opsForSet().remove(key(contentId), String.valueOf(userId));
        return removed != null && removed > 0;
    }

    public boolean isLiked(long contentId, long userId) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(key(contentId), String.valueOf(userId)));
    }

    public long size(long contentId) {
        Long size = redis.opsForSet().size(key(contentId));
        return size == null ? 0 : size;
    }

    /** 集合是否还在：不在就说明缓存已过期/丢失，需要回源重建 */
    public boolean hasCache(long contentId) {
        return Boolean.TRUE.equals(redis.hasKey(key(contentId)));
    }

    public long ttlSeconds(long contentId) {
        Long ttl = redis.getExpire(key(contentId));
        return ttl == null ? -2 : ttl;
    }

    public void deleteCache(long contentId) {
        redis.delete(key(contentId));
    }
}
