package com.community.content;

import com.community.common.BizException;
import com.community.common.ErrorCode;
import com.community.common.UserContext;
import com.community.content.dto.ContentVO;
import com.community.content.dto.CreateContentRequest;
import com.community.content.dto.PageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class ContentService {

    private static final Logger log = LoggerFactory.getLogger(ContentService.class);

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private static final String DETAIL_KEY_PREFIX = "content:detail:";
    /** 热点数据 10 分钟：读多写少，同时作为一致性的兜底窗口 */
    private static final Duration DETAIL_TTL = Duration.ofMinutes(10);
    /** 空值标记的短 TTL：防缓存穿透，同时不让“后来真的有了”等太久 */
    private static final Duration NULL_TTL = Duration.ofSeconds(60);
    private static final String NULL_MARKER = "__NULL__";

    private final ContentMapper contentMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ContentService(ContentMapper contentMapper, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.contentMapper = contentMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    private ContentVO toVO(Content c) {
        return new ContentVO(c.getId(), c.getUserId(), c.getBody(), c.getCreatedAt());
    }

    private String cacheKey(long id) {
        return DETAIL_KEY_PREFIX + id;
    }

    /**
     * 发布内容。
     *
     * <p>作者来自 {@link UserContext}，不接受外部传入的 userId。
     * 幂等键暂时由服务端生成（保证 (user_id, idem_key) 不冲突），
     * 第 8 步改为客户端提供，并用它把"重复提交"变成"返回同一条内容"。
     */
    @Transactional
    public ContentVO publish(CreateContentRequest request) {
        long userId = UserContext.require();

        Content content = new Content();
        content.setUserId(userId);
        content.setIdemKey("srv-" + UUID.randomUUID());
        content.setBody(request.body().trim());
        content.setDeleted(Content.NOT_DELETED);

        contentMapper.insert(content);

        // INSERT 只回填自增主键，created_at 由数据库默认值生成，拿不到。
        // 回读一次（走主键）拿权威时间——比在 Java 里 LocalDateTime.now() 更可信，
        // 也不会受应用与数据库时钟不一致影响。
        Content saved = contentMapper.findVisibleById(content.getId()).orElseThrow();

        return new ContentVO(saved.getId(), saved.getUserId(), saved.getBody(), saved.getCreatedAt());
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

    private String toJson(ContentVO vo) {
        try {
            return objectMapper.writeValueAsString(vo);
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
