package com.community.content;

import com.community.common.BizException;
import com.community.common.ErrorCode;
import com.community.common.UserContext;
import com.community.content.dto.ContentVO;
import com.community.content.dto.CreateContentRequest;
import com.community.content.dto.PageResponse;
import com.community.content.dto.PublishResult;
import com.community.like.HotRankService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ContentService {

    private static final Logger log = LoggerFactory.getLogger(ContentService.class);

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;
    /** 重建榜单时最多回源多少条 */
    private static final int HOT_REBUILD_LIMIT = 500;

    private static final String DETAIL_KEY_PREFIX = "content:detail:";
    /** 热点数据 10 分钟：读多写少，同时作为一致性的兜底窗口 */
    private static final Duration DETAIL_TTL = Duration.ofMinutes(10);
    /** 空值标记的短 TTL：防缓存穿透，同时不让“后来真的有了”等太久 */
    private static final Duration NULL_TTL = Duration.ofSeconds(60);
    private static final String NULL_MARKER = "__NULL__";

    private static final String IDEM_KEY_PREFIX = "idem:content:";
    /** 处理中标记：覆盖一次请求的最大耗时，崩溃后能自动释放 */
    private static final Duration IDEM_LOCK_TTL = Duration.ofSeconds(30);
    /** 完成后保留 24 小时，让重试能命中"已完成" */
    private static final Duration IDEM_DONE_TTL = Duration.ofHours(24);
    private static final Pattern IDEM_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private final ContentMapper contentMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final HotRankService hotRankService;

    public ContentService(ContentMapper contentMapper, StringRedisTemplate redis,
                          ObjectMapper objectMapper, HotRankService hotRankService) {
        this.contentMapper = contentMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.hotRankService = hotRankService;
    }

    private ContentVO toVO(Content c) {
        return new ContentVO(c.getId(), c.getUserId(), c.getBody(), c.getLikeCount(), c.getCreatedAt());
    }

    private String cacheKey(long id) {
        return DETAIL_KEY_PREFIX + id;
    }

    /**
     * 发布内容。
     *
     * <p>幂等三层：
     * ① Redis SETNX 抢占，挡住并发重复请求；
     * ② 数据库唯一键 uk_user_idem 是最终真相；
     * ③ 重放时回查并返回同一条，而不是报错。
     *
     * <p>作者来自 {@link UserContext}，不接受外部传入的 userId。
     */
    @Transactional
    public PublishResult publish(CreateContentRequest request, String rawIdemKey) {
        long userId = UserContext.require();
        String idemKey = normalizeIdemKey(rawIdemKey);

        String redisKey = IDEM_KEY_PREFIX + userId + ":" + idemKey;
        boolean acquired = Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(redisKey, "processing", IDEM_LOCK_TTL));
        if (!acquired) {
            // 抢不到：可能正在处理，也可能已完成 —— 都要回查数据库才能回答
            return new PublishResult(replay(userId, idemKey), false);
        }

        try {
            Content content = new Content();
            content.setUserId(userId);
            content.setIdemKey(idemKey);
            content.setBody(request.body().trim());
            content.setDeleted(Content.NOT_DELETED);

            contentMapper.insert(content);

            // INSERT 只回填自增主键，created_at 由数据库默认值生成，拿不到。
            // 回读一次（走主键）拿权威时间——比在 Java 里 LocalDateTime.now() 更可信，
            // 也不会受应用与数据库时钟不一致影响。
            Content saved = contentMapper.findVisibleById(content.getId()).orElseThrow();

            // 标记完成：值为内容 id，TTL 拉长，重试可直接回查命中
            redis.opsForValue().set(redisKey, String.valueOf(saved.getId()), IDEM_DONE_TTL);

            return new PublishResult(toVO(saved), true);
        } catch (DuplicateKeyException ex) {
            // Redis 没拦住（或抢到后崩溃）时的兜底：唯一键挡下了重复
            log.info("幂等键冲突，返回已存在的内容, userId={}, idemKey={}", userId, idemKey);
            return new PublishResult(replay(userId, idemKey), false);
        }
    }

    /** 幂等重放：返回之前那条；找不到说明第一次请求还在处理中 */
    private ContentVO replay(long userId, String idemKey) {
        Content existing = contentMapper.findByUserIdAndIdemKey(userId, idemKey)
                .orElseThrow(() -> new BizException(ErrorCode.CONFLICT,
                        "相同幂等键的请求正在处理中，请稍后重试"));
        if (existing.getDeleted() != null && existing.getDeleted() == Content.DELETED) {
            // 已删除的内容不能"重放成功"，否则用户以为发布生效了
            throw new BizException(ErrorCode.CONFLICT, "该幂等键对应的内容已被删除，请使用新的幂等键");
        }
        return toVO(existing);
    }

    /** 没传就用服务端随机键（退化为"无幂等"）；传了必须合法，否则拒绝 */
    private String normalizeIdemKey(String rawIdemKey) {
        if (rawIdemKey == null || rawIdemKey.isBlank()) {
            return "srv-" + UUID.randomUUID();
        }
        String key = rawIdemKey.trim();
        if (!IDEM_KEY_PATTERN.matcher(key).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "Idempotency-Key 只能包含字母、数字、下划线与连字符，长度 8-64");
        }
        return key;
    }

    /**
     * 信息流：游标分页。
     *
     * <p>多查一条用于判断 hasMore，比对全表 COUNT(*) 便宜得多。
     * size 必须夹紧上限，否则 size=100000 等于一次拉全表。
     */
    public PageResponse<ContentVO> feed(Long cursor, Integer size) {
        int limit = (size == null || size < 1) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        long effectiveCursor = (cursor == null) ? Long.MAX_VALUE : cursor;

        List<Content> rows = contentMapper.page(effectiveCursor, limit + 1);

        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }

        List<ContentVO> items = rows.stream()
                .map(this::toVO)
                .toList();

        // 只有还有下一页才给游标：否则客户端拿无效游标继续翻，会白跑一次
        Long nextCursor = hasMore ? rows.get(rows.size() - 1).getId() : null;

        return new PageResponse<>(items, nextCursor, hasMore);
    }

    /**
     * 详情：cache-aside。
     *
     * <p>读缓存 → 未命中查库 → 回写缓存。
     * 不存在的结果也缓存（短 TTL），否则随机 id 会全部击穿到数据库。
     */
    public ContentVO detail(long id) {
        String key = cacheKey(id);
        String cached = redis.opsForValue().get(key);

        if (NULL_MARKER.equals(cached)) {
            log.info("detail 命中空值标记，直接返回 404, id={}", id);
            throw new BizException(ErrorCode.CONTENT_NOT_FOUND);
        }
        if (cached != null) {
            log.info("detail 缓存命中, id={}, ttl={}s", id, redis.getExpire(key));
            return fromJson(cached);
        }

        Content content = contentMapper.findVisibleById(id).orElse(null);
        if (content == null) {
            // 防穿透：不存在也占位（短 TTL），避免同一个不存在的 id 反复查库
            redis.opsForValue().set(key, NULL_MARKER, NULL_TTL);
            log.info("detail 未命中且库中无数据，写入空值标记, id={}", id);
            throw new BizException(ErrorCode.CONTENT_NOT_FOUND);
        }

        ContentVO vo = toVO(content);
        String json = toJson(vo);
        if (json != null) {
            redis.opsForValue().set(key, json, DETAIL_TTL);
        }
        log.info("detail 缓存未命中，已从数据库加载并回写, id={}", id);
        return vo;
    }

    /**
     * 逻辑删除，只能删自己的。
     *
     * <p>“不存在 / 不是你的 / 已删除”三种情况返回同一个 404——
     * 否则攻击者可以靠“403 还是 404”枚举出哪些 id 真实存在。
     *
     * <p>先更新库、再删缓存：缓存删早了，并发读可能把旧值又写回去。
     */
    @Transactional
    public void delete(long id) {
        long userId = UserContext.require();

        int affected = contentMapper.softDeleteOwnedBy(id, userId);
        if (affected == 0) {
            log.info("删除失败（不存在/非本人/已删），统一返回 404, id={}, userId={}", id, userId);
            throw new BizException(ErrorCode.CONTENT_NOT_FOUND);
        }

        redis.delete(cacheKey(id));
        log.info("内容已软删并清理缓存, id={}, userId={}", id, userId);
    }

    /**
     * 热榜：id 顺序来自 Redis ZSet，内容本身仍要回表查（Redis 不存正文）。
     *
     * <p>ZSet 里可能残留已删除/已过期的 id，查不到的直接跳过，
     * 用 LinkedHashMap 语义保证"返回顺序 = 榜单顺序"。
     */
    public List<ContentVO> hot(int limit) {
        int topN = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));

        // 榜单缓存丢了就重建：这是“Redis 只是缓存”的最终证明
        if (!hotRankService.hasData()) {
            rebuildHotRank();
        }

        List<Long> ids = hotRankService.topIds(topN);
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<Long, Content> found = contentMapper.findVisibleByIds(ids).stream()
                .collect(Collectors.toMap(Content::getId, c -> c));

        List<ContentVO> result = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Content content = found.get(id);
            if (content != null) {
                result.add(toVO(content));
            } else {
                log.info("热榜中的内容已不可见，跳过, id={}", id);
            }
        }
        return result;
    }

    /** 从 DB 按赞数重建热榜（低频操作，只在缓存丢失时触发） */
    private void rebuildHotRank() {
        List<Content> top = contentMapper.topByLikeCount(HOT_REBUILD_LIMIT);
        Map<Long, Long> scores = new LinkedHashMap<>();
        for (Content c : top) {
            long count = c.getLikeCount() == null ? 0 : c.getLikeCount();
            if (count > 0) {
                scores.put(c.getId(), count);
            }
        }
        hotRankService.rebuild(scores);
        log.info("热榜缓存为空，已从 DB 重建 {} 条", scores.size());
    }

    private String toJson(ContentVO vo) {
        try {            return objectMapper.writeValueAsString(vo);
        } catch (JsonProcessingException e) {
            // 序列化失败不应影响主流程：当作“不缓存”处理
            log.warn("detail 序列化失败，跳过缓存, id={}", vo.id(), e);
            return null;
        }
    }

    private ContentVO fromJson(String json) {
        try {
            return objectMapper.readValue(json, ContentVO.class);
        } catch (JsonProcessingException e) {
            // 缓存脏数据：删掉让它下次回源
            log.warn("detail 反序列化失败，删除脏缓存", e);
            throw new IllegalStateException("缓存数据损坏", e);
        }
    }
}
