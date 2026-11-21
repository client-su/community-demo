package com.community.bookmark;

import com.community.bookmark.dto.BookmarkResult;
import com.community.common.BizException;
import com.community.common.ErrorCode;
import com.community.common.UserContext;
import com.community.content.Content;
import com.community.content.ContentMapper;
import com.community.content.dto.ContentVO;
import com.community.content.dto.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收藏/取消收藏。
 *
 * <p>与 {@code LikeService} 完全平行但零交集：不碰 like_count、不碰热榜、不写 Redis。
 * 收藏是私有动作，数据库一行关系就是全部状态，没有需要加速的热数据。
 */
@Service
public class BookmarkService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final BookmarkMapper bookmarkMapper;
    private final ContentMapper contentMapper;

    public BookmarkService(BookmarkMapper bookmarkMapper, ContentMapper contentMapper) {
        this.bookmarkMapper = bookmarkMapper;
        this.contentMapper = contentMapper;
    }

    @Transactional
    public BookmarkResult add(long contentId) {
        long userId = UserContext.require();
        requireVisibleContent(contentId);

        // 唯一键保证幂等：重复收藏不报错、不产生重复行
        bookmarkMapper.insertIgnore(userId, contentId);
        return new BookmarkResult(contentId, true);
    }

    /**
     * 取消收藏：不做内容可见性校验——内容软删后仍要允许用户清掉这条收藏，
     * 否则会产生永远删不掉的孤儿行。
     */
    @Transactional
    public BookmarkResult remove(long contentId) {
        long userId = UserContext.require();
        bookmarkMapper.delete(userId, contentId);
        return new BookmarkResult(contentId, false);
    }

    /** 查询「我是否收藏了」：内容必须可见，按钮只对可见内容有意义 */
    public BookmarkResult state(long contentId) {
        long userId = UserContext.require();
        requireVisibleContent(contentId);
        boolean bookmarked = bookmarkMapper.exists(userId, contentId);
        return new BookmarkResult(contentId, bookmarked);
    }

    /**
     * 「我收藏的」游标分页，游标是 bookmark.id（收藏先后）。
     * 身份来自 {@link UserContext}，不接受外部传入的 userId。
     */
    public PageResponse<ContentVO> bookmarkedByMe(Long cursor, Integer size) {
        long userId = UserContext.require();
        int limit = (size == null || size < 1) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        long effectiveCursor = (cursor == null) ? Long.MAX_VALUE : cursor;

        List<BookmarkedContentRow> rows = bookmarkMapper.pageByUser(userId, effectiveCursor, limit + 1);

        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }

        List<ContentVO> items = rows.stream()
                .map(r -> new ContentVO(r.getId(), r.getUserId(), r.getNickname(),
                        r.getBody(), r.getLikeCount(), r.getCommentCount(), r.getCreatedAt()))
                .toList();

        Long nextCursor = hasMore ? rows.get(rows.size() - 1).getBookmarkId() : null;
        return new PageResponse<>(items, nextCursor, hasMore);
    }

    private void requireVisibleContent(long contentId) {
        Content content = contentMapper.findVisibleById(contentId)
                .orElseThrow(() -> new BizException(ErrorCode.CONTENT_NOT_FOUND));
    }
}
