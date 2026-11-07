package com.community.content;

import com.community.common.UserContext;
import com.community.content.dto.ContentVO;
import com.community.content.dto.CreateContentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ContentService {

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
}
