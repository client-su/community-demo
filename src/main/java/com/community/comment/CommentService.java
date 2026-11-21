package com.community.comment;

import com.community.comment.dto.CommentVO;
import com.community.comment.dto.CreateCommentRequest;
import com.community.common.BizException;
import com.community.common.ErrorCode;
import com.community.common.UserContext;
import com.community.content.ContentMapper;
import com.community.content.ContentService;
import com.community.content.dto.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 评论读写。
 *
 * <p>计数以 content.comment_count 为 DB 唯一真相，加减用 GREATEST 夹到 0；
 * 评论读取直查 DB（不依赖 Redis），Redis 故障不影响主流程。
 */
@Service
public class CommentService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final CommentMapper commentMapper;
    private final ContentMapper contentMapper;
    private final ContentService contentService;

    public CommentService(CommentMapper commentMapper, ContentMapper contentMapper,
                          ContentService contentService) {
        this.commentMapper = commentMapper;
        this.contentMapper = contentMapper;
        this.contentService = contentService;
    }

    /**
     * 某条内容下的评论列表（匿名可读，游标分页）。
     *
     * <p>内容不存在/已删时返回 40401——评论列表入口与内容详情同生死。
     */
    public PageResponse<CommentVO> list(long contentId, Long cursor, Integer size) {
        contentMapper.findVisibleById(contentId)
                .orElseThrow(() -> new BizException(ErrorCode.CONTENT_NOT_FOUND));

        int limit = (size == null || size < 1) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        long effectiveCursor = (cursor == null) ? Long.MAX_VALUE : cursor;

        List<Comment> rows = commentMapper.pageByContent(contentId, effectiveCursor, limit + 1);

        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }

        List<CommentVO> items = rows.stream().map(this::toVO).toList();
        Long nextCursor = hasMore ? rows.get(rows.size() - 1).getId() : null;
        return new PageResponse<>(items, nextCursor, hasMore);
    }

    /**
     * 发评论：插入评论 + 内容评论数 +1，同一事务。
     *
     * <p>插入后回读一次（走主键）拿昵称与数据库生成的 created_at，
     * 与应用本地时钟无关（与发布内容同法）。
     */
    @Transactional
    public CommentVO create(long contentId, CreateCommentRequest request) {
        long userId = UserContext.require();
        contentMapper.findVisibleById(contentId)
                .orElseThrow(() -> new BizException(ErrorCode.CONTENT_NOT_FOUND));

        Comment comment = new Comment();
        comment.setContentId(contentId);
        comment.setUserId(userId);
        comment.setBody(request.body().trim());
        comment.setDeleted(Comment.NOT_DELETED);

        commentMapper.insert(comment);
        contentMapper.addCommentCount(contentId, 1);
        // comment_count 真的 +1 了：失效详情缓存，让详情读到新鲜计数（对齐点赞路径）
        contentService.invalidateDetailCache(contentId);

        Comment saved = commentMapper.findVisibleById(comment.getId()).orElseThrow();
        return toVO(saved);
    }

    /**
     * 删评论（软删），只能删自己的。
     *
     * <p>「不存在 / 不是我的 / 已删」统一返回 40402（防资源枚举，
     * 对齐内容删除的做法）。先查后删是为了拿到 content_id 回减计数。
     */
    @Transactional
    public void delete(long commentId) {
        long userId = UserContext.require();

        Comment comment = commentMapper.findVisibleById(commentId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
        }

        commentMapper.softDelete(commentId);
        contentMapper.addCommentCount(comment.getContentId(), -1);
        // comment_count 真的 -1 了：失效详情缓存，让详情读到新鲜计数
        contentService.invalidateDetailCache(comment.getContentId());
    }

    private CommentVO toVO(Comment c) {
        return new CommentVO(c.getId(), c.getContentId(), c.getUserId(),
                c.getNickname(), c.getBody(), c.getCreatedAt());
    }
}
