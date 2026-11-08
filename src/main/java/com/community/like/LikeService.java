package com.community.like;

import com.community.common.BizException;
import com.community.common.ErrorCode;
import com.community.common.UserContext;
import com.community.content.Content;
import com.community.content.ContentMapper;
import com.community.like.dto.LikeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 点赞/取消点赞。
 *
 * <p>写路径顺序：校验内容 → 写 DB（真相）→ 更新计数 → 尽力维护 Redis。
 * Redis 失败只记日志，不影响请求成功——否则 Redis 抖一下用户就点不了赞。
 */
@Service
public class LikeService {

    private static final Logger log = LoggerFactory.getLogger(LikeService.class);

    private final LikeMapper likeMapper;
    private final ContentMapper contentMapper;
    private final LikeRedisService likeRedis;

    public LikeService(LikeMapper likeMapper, ContentMapper contentMapper, LikeRedisService likeRedis) {
        this.likeMapper = likeMapper;
        this.contentMapper = contentMapper;
        this.likeRedis = likeRedis;
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

        return currentState(contentId, false);
    }

    /** 以数据库为准返回当前状态 */
    private LikeResult currentState(long contentId, boolean liked) {
        Content content = contentMapper.findVisibleById(contentId)
                .orElseThrow(() -> new BizException(ErrorCode.CONTENT_NOT_FOUND));
        long count = content.getLikeCount() == null ? 0 : content.getLikeCount();
        return new LikeResult(contentId, liked, count);
    }

    private void requireVisibleContent(long contentId) {
        contentMapper.findVisibleById(contentId)
                .orElseThrow(() -> new BizException(ErrorCode.CONTENT_NOT_FOUND));
    }
}
