package com.community.like;

import com.community.common.BizException;
import com.community.common.ErrorCode;
import com.community.common.UserContext;
import com.community.content.Content;
import com.community.content.ContentMapper;
import com.community.content.ContentService;
import com.community.content.dto.ContentVO;
import com.community.content.dto.PageResponse;
import com.community.like.dto.LikeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 点赞/取消点赞。
 *
 * <p>写路径顺序：校验内容 → 写 DB（真相）→ 更新计数 → 尽力维护 Redis。
 * Redis 失败只记日志，不影响请求成功——否则 Redis 抖一下用户就点不了赞。
 */
@Service
public class LikeService {

    private static final Logger log = LoggerFactory.getLogger(LikeService.class);

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final LikeMapper likeMapper;
    private final ContentMapper contentMapper;
    private final LikeRedisService likeRedis;
    private final HotRankService hotRank;
    private final ContentService contentService;

    public LikeService(LikeMapper likeMapper, ContentMapper contentMapper,
                       LikeRedisService likeRedis, HotRankService hotRank,
                       ContentService contentService) {
        this.likeMapper = likeMapper;
        this.contentMapper = contentMapper;
        this.likeRedis = likeRedis;
        this.hotRank = hotRank;
        this.contentService = contentService;
    }

    @Transactional
    public LikeResult like(long contentId) {
        long userId = UserContext.require();
        requireVisibleContent(contentId);

        // 唯一键保证幂等：返回 1 才是"真的新增"，否则重复点赞会把计数加爆
        int inserted = likeMapper.insertIgnore(userId, contentId);
        if (inserted == 1) {
            contentMapper.addLikeCount(contentId, 1);
        }

        try {
            // 无论是否新增都确保集合里有他（Redis 丢过数据时这就顺手修复了）
            likeRedis.add(contentId, userId);
        } catch (RuntimeException ex) {
            log.warn("Redis 维护失败（不影响点赞结果）, contentId={}, userId={}", contentId, userId, ex);
        }

        if (inserted == 1) {
            updateHotRank(contentId, 1);
            // likeCount 真的 +1 了，失效详情缓存让详情读到新鲜计数
            contentService.invalidateDetailCache(contentId);
        }

        return currentState(contentId, true);
    }

    @Transactional
    public LikeResult unlike(long contentId) {
        long userId = UserContext.require();
        requireVisibleContent(contentId);

        int deleted = likeMapper.delete(userId, contentId);
        if (deleted == 1) {
            // GREATEST 兜底：即使数据异常导致计数为 0，也不会变成负数
            contentMapper.addLikeCount(contentId, -1);
        }

        try {
            likeRedis.remove(contentId, userId);
        } catch (RuntimeException ex) {
            log.warn("Redis 维护失败（不影响取消结果）, contentId={}, userId={}", contentId, userId, ex);
        }

        if (deleted == 1) {
            updateHotRank(contentId, -1);
            // likeCount 真的 -1 了，失效详情缓存让详情读到新鲜计数
            contentService.invalidateDetailCache(contentId);
        }

        return currentState(contentId, false);
    }

    /** 热榜分数是“缓存”，失败不影响主流程 */
    private void updateHotRank(long contentId, double delta) {
        try {
            hotRank.addScore(contentId, delta);
        } catch (RuntimeException ex) {
            log.warn("热榜更新失败（可由 DB 重建）, contentId={}, delta={}", contentId, delta, ex);
        }
    }

    /**
     * 查询"我是否赞过 + 当前赞数"。
     *
     * <p>read-through：集合不存在说明缓存丢了，先回源 DB 重建；
     * Redis 完全不可用时退回直接查 DB —— 接口必须能用。
     */
    public LikeResult state(long contentId) {
        long userId = UserContext.require();
        Content content = contentMapper.findVisibleById(contentId)
                .orElseThrow(() -> new BizException(ErrorCode.CONTENT_NOT_FOUND));

        boolean liked;
        try {
            if (!likeRedis.hasCache(contentId)) {
                long rebuilt = likeRedis.rebuild(contentId, likeMapper.findUserIdsByContent(contentId));
                log.info("点赞集合缺失，已从 DB 重建, contentId={}, members={}", contentId, rebuilt);
            }
            liked = likeRedis.isLiked(contentId, userId);
        } catch (RuntimeException ex) {
            // 降级：Redis 不可用也要能回答"我赞过吗"
            log.warn("Redis 不可用，回源 DB 判断点赞状态, contentId={}, userId={}", contentId, userId, ex);
            liked = likeMapper.exists(userId, contentId);
        }

        long count = content.getLikeCount() == null ? 0 : content.getLikeCount();
        return new LikeResult(contentId, liked, count);
    }

    /** 以数据库为准返回当前状态 */
    private LikeResult currentState(long contentId, boolean liked) {        Content content = contentMapper.findVisibleById(contentId)
                .orElseThrow(() -> new BizException(ErrorCode.CONTENT_NOT_FOUND));
        long count = content.getLikeCount() == null ? 0 : content.getLikeCount();
        return new LikeResult(contentId, liked, count);
    }

    private void requireVisibleContent(long contentId) {
        contentMapper.findVisibleById(contentId)
                .orElseThrow(() -> new BizException(ErrorCode.CONTENT_NOT_FOUND));
    }

    /**
     * 「我赞过的内容」游标分页，游标是 content_like.id（点赞先后）。
     *
     * <p>身份来自 {@link UserContext}，不接受外部传入的 userId。
     */
    public PageResponse<ContentVO> likedByMe(Long cursor, Integer size) {
        long userId = UserContext.require();
        int limit = (size == null || size < 1) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        long effectiveCursor = (cursor == null) ? Long.MAX_VALUE : cursor;

        List<LikedContentRow> rows = likeMapper.pageLikedByUser(userId, effectiveCursor, limit + 1);

        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }

        List<ContentVO> items = rows.stream()
                .map(r -> new ContentVO(r.getId(), r.getUserId(), r.getNickname(),
                        r.getBody(), r.getLikeCount(), r.getCreatedAt()))
                .toList();

        Long nextCursor = hasMore ? rows.get(rows.size() - 1).getLikeId() : null;
        return new PageResponse<>(items, nextCursor, hasMore);
    }
}
