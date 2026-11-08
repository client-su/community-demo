package com.community.like;

import com.community.common.UserContext;
import com.community.content.Content;
import com.community.content.ContentMapper;
import com.community.like.dto.LikeResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Redis 全挂时的降级验证：写路径与读路径都必须还能工作。
 */
@SpringBootTest
@Transactional
class LikeDegradeTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private ContentMapper contentMapper;

    @MockitoBean
    private LikeRedisService likeRedis;

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
    void likeStillSucceedsWhenRedisIsDown() {
        long contentId = newContent(1L);
        UserContext.set(1L);

        // 所有 Redis 操作都失败
        when(likeRedis.add(anyLong(), anyLong()))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        // addScore 返回 void，只能用 doThrow 形式打桩
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(hotRank).addScore(anyLong(), anyDouble());

        LikeResult result = likeService.like(contentId);

        System.out.println("Redis 故障时点赞结果 = " + result);
        assertThat(result.liked()).isTrue();
        assertThat(result.likeCount()).isEqualTo(1L);
        // 数据真的落库了（Redis 失败不影响真相）
        assertThat(contentMapper.findVisibleById(contentId).orElseThrow().getLikeCount()).isEqualTo(1);
    }

    @Test
    void stateFallsBackToDatabaseWhenRedisIsDown() {
        long contentId = newContent(1L);
        UserContext.set(1L);

        when(likeRedis.hasCache(anyLong()))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        likeService.like(contentId);          // 先落库（此时 Redis 桩返回默认值，不影响 DB）
        LikeResult state = likeService.state(contentId);

        System.out.println("Redis 故障时查询状态 = " + state);
        assertThat(state.liked()).isTrue();
        assertThat(state.likeCount()).isEqualTo(1L);
    }
}
