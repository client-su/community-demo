package com.community.content;

import com.community.common.UserContext;
import com.community.content.dto.ContentVO;
import com.community.content.dto.CreateContentRequest;
import com.community.content.dto.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ContentService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final ContentMapper contentMapper;

    public ContentService(ContentMapper contentMapper) {
        this.contentMapper = contentMapper;
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
                .map(c -> new ContentVO(c.getId(), c.getUserId(), c.getBody(), c.getCreatedAt()))
                .toList();

        // 只有还有下一页才给游标：否则客户端拿无效游标继续翻，会白跑一次
        Long nextCursor = hasMore ? rows.get(rows.size() - 1).getId() : null;

        return new PageResponse<>(items, nextCursor, hasMore);
    }
}
