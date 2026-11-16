package com.community.content;

import com.community.common.UserContext;
import com.community.content.dto.ContentVO;
import com.community.content.dto.CreateContentRequest;
import com.community.content.dto.PublishResult;
import com.community.like.HotRankService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * 内容路径的 Redis 降级验证：detail / publish / delete / hot 在 Redis 全挂时仍可用。
 *
 * <p>与 {@code LikeDegradeTest} 互补：后者验证点赞路径，这里验证内容路径。
 * Redis 不可用时主流程必须还能走——DB 才是真相，缓存只是加速。
 */
@SpringBootTest
@Transactional
class ContentDegradeTest {

    @Autowired
    private ContentService contentService;

    @Autowired
    private ContentMapper contentMapper;

    @MockitoBean
    private StringRedisTemplate redis;

    @MockitoBean
    private HotRankService hotRank;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private long newContent(long userId) {
        Content c = new Content();
        c.setUserId(userId);
        c.setIdemKey("degrade-" + UUID.randomUUID());
        c.setBody("降级测试内容");
        contentMapper.insert(c);
        return c.getId();
    }

    @Test
    void detailFallsBackToDatabaseWhenRedisIsDown() {
        long id = newContent(1L);
        // 所有 opsForValue 调用都失败：safeGet 当作未命中，safeSet 跳过
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("redis down"));
        UserContext.set(1L);

        ContentVO vo = contentService.detail(id);

        System.out.println("Redis 故障时详情 = " + vo);
        assertThat(vo.id()).isEqualTo(id);
        assertThat(vo.body()).isEqualTo("降级测试内容");
    }

    @Test
    void publishStillSucceedsWhenRedisIsDown() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("redis down"));
        UserContext.set(1L);

        PublishResult result = contentService.publish(new CreateContentRequest("降级发布"), null);

        System.out.println("Redis 故障时发布 created = " + result.created());
        assertThat(result.created()).isTrue();
        assertThat(result.content().body()).isEqualTo("降级发布");
    }

    @Test
    void deleteStillSucceedsWhenRedisIsDown() {
        long id = newContent(1L);
        doThrow(new RedisConnectionFailureException("redis down")).when(redis).delete(anyString());
        UserContext.set(1L);

        contentService.delete(id);

        System.out.println("Redis 故障时删除已执行, id=" + id);
        assertThat(contentMapper.findVisibleById(id)).isEmpty();
    }

    @Test
    void hotFallsBackToDatabaseWhenRedisIsDown() {
        long id = newContent(1L);
        contentMapper.addLikeCount(id, 1);   // like_count > 0 才能进热榜
        when(hotRank.hasData()).thenThrow(new RedisConnectionFailureException("redis down"));
        UserContext.set(1L);

        List<ContentVO> hot = contentService.hot(10);

        System.out.println("Redis 故障时热榜 = " + hot);
        assertThat(hot).anyMatch(vo -> vo.id().equals(id));
    }
}
