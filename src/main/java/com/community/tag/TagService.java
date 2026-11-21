package com.community.tag;

import com.community.common.BizException;
import com.community.common.ErrorCode;
import com.community.content.Content;
import com.community.content.dto.ContentVO;
import com.community.content.dto.PageResponse;
import com.community.tag.dto.TagVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 标签读取与内容-标签绑定。
 *
 * <p>bind 不自己开事务：由发布链路（ContentService.publish 的 @Transactional）承载，
 * 标签校验失败会连内容插入一起回滚。
 */
@Service
public class TagService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final TagMapper tagMapper;

    public TagService(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    /** 标签列表（匿名可读）：预置标签全量返回 */
    public List<TagVO> list() {
        return tagMapper.findAll().stream()
                .map(t -> new TagVO(t.getId(), t.getName()))
                .toList();
    }

    /** 按标签读内容（匿名可读，游标分页）。标签不存在 → 40403 */
    public PageResponse<ContentVO> contentsByTag(long tagId, Long cursor, Integer size) {
        tagMapper.findById(tagId).orElseThrow(() -> new BizException(ErrorCode.TAG_NOT_FOUND));

        int limit = (size == null || size < 1) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        long effectiveCursor = (cursor == null) ? Long.MAX_VALUE : cursor;

        List<Content> rows = tagMapper.pageContentsByTag(tagId, effectiveCursor, limit + 1);

        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }

        List<ContentVO> items = rows.stream().map(this::toVO).toList();
        Long nextCursor = hasMore ? rows.get(rows.size() - 1).getId() : null;
        return new PageResponse<>(items, nextCursor, hasMore);
    }

    /**
     * 发布时绑定标签（幂等）：先校验全部存在，再 INSERT IGNORE。
     *
     * <p>空列表直接返回（不带标签发布是合法行为）。去重后再校验：
     * 存在的 id 数 != 去重后的请求数，说明有非法标签。
     */
    public void bind(long contentId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        List<Long> distinct = tagIds.stream().distinct().toList();
        List<Long> existing = tagMapper.findExistingIds(distinct);
        if (existing.size() != distinct.size()) {
            throw new BizException(ErrorCode.TAG_NOT_FOUND);
        }
        tagMapper.insertContentTags(contentId, distinct);
    }

    private ContentVO toVO(Content c) {
        return new ContentVO(c.getId(), c.getUserId(), c.getNickname(),
                c.getBody(), c.getLikeCount(), c.getCommentCount(), c.getCreatedAt());
    }
}
