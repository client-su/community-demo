package com.community.like;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 热榜：ZSet 维护 内容 id → 赞数 的有序集合。
 *
 * <p>为什么不直接 ORDER BY like_count：
 * ① 每次请求都要扫索引/回表排序；② 高赞内容更新 like_count 会锁同一行；
 * ③ 将来要接"时间衰减/浏览权重"，只需改 score 计算，不用动 SQL。
 */
@Service
public class HotRankService {

    private static final String KEY = "hot:content";

    private final StringRedisTemplate redis;

    public HotRankService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 原子加减分；减到 0 及以下时移出榜单，避免 TopN 里混入 0 赞内容 */
    public void addScore(long contentId, double delta) {
        String member = String.valueOf(contentId);
        Double score = redis.opsForZSet().incrementScore(KEY, member, delta);
        if (score != null && score <= 0) {
            redis.opsForZSet().remove(KEY, member);
        }
    }

    /** 榜单前 N 的 id（按分数降序） */
    public List<Long> topIds(int limit) {
        Set<String> members = redis.opsForZSet().reverseRange(KEY, 0, limit - 1L);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(members.size());
        for (String member : members) {
            ids.add(Long.parseLong(member));
        }
        return ids;
    }

    /** 带分数版本，便于排查/展示 */
    public List<ZSetOperations.TypedTuple<String>> topWithScores(int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redis.opsForZSet().reverseRangeWithScores(KEY, 0, limit - 1L);
        return tuples == null ? List.of() : new ArrayList<>(tuples);
    }

    public void rebuildFromScores(long contentId, long score) {
        if (score > 0) {
            redis.opsForZSet().add(KEY, String.valueOf(contentId), score);
        } else {
            redis.opsForZSet().remove(KEY, String.valueOf(contentId));
        }
    }

    /** 榜单是否存在（空集合会被 Redis 删掉） */
    public boolean hasData() {
        Long size = redis.opsForZSet().zCard(KEY);
        return size != null && size > 0;
    }

    /** 用 DB 的赞数整体重建榜单 */
    public void rebuild(java.util.Map<Long, Long> scores) {
        redis.delete(KEY);
        if (scores == null || scores.isEmpty()) {
            return;
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = new java.util.HashSet<>();
        scores.forEach((id, score) -> tuples.add(
                ZSetOperations.TypedTuple.of(String.valueOf(id), score.doubleValue())));
        redis.opsForZSet().add(KEY, tuples);
    }
}
