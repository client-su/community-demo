package com.community.like;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 直接打真实 Redis（开发环境 6379）。用固定 contentId 并在结束后清理，避免污染。
 */
@SpringBootTest
class LikeRedisServiceTest {

    private static final long CONTENT_ID = 990001L;

    @Autowired
    private LikeRedisService likeRedisService;

    @AfterEach
    void tearDown() {
        likeRedisService.deleteCache(CONTENT_ID);
    }

    @Test
    void setDedupIsIdempotent() {
        boolean first = likeRedisService.add(CONTENT_ID, 100L);
        boolean second = likeRedisService.add(CONTENT_ID, 100L);
        boolean otherUser = likeRedisService.add(CONTENT_ID, 200L);

        System.out.println("用户100 第一次 add -> " + first);
        System.out.println("用户100 第二次 add -> " + second + "（已存在，返回 false）");
        System.out.println("用户200 第一次 add -> " + otherUser);
        System.out.println("集合大小 = " + likeRedisService.size(CONTENT_ID)
                + ", TTL = " + likeRedisService.ttlSeconds(CONTENT_ID) + "s");
        System.out.println("100 是否已赞 = " + likeRedisService.isLiked(CONTENT_ID, 100L)
                + ", 300 是否已赞 = " + likeRedisService.isLiked(CONTENT_ID, 300L));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(otherUser).isTrue();
        assertThat(likeRedisService.size(CONTENT_ID)).isEqualTo(2);
        assertThat(likeRedisService.isLiked(CONTENT_ID, 300L)).isFalse();
        assertThat(likeRedisService.hasCache(CONTENT_ID)).isTrue();
    }

    @Test
    void removeIsIdempotentAndEmptySetDisappears() {
        likeRedisService.add(CONTENT_ID, 100L);

        boolean first = likeRedisService.remove(CONTENT_ID, 100L);
        boolean second = likeRedisService.remove(CONTENT_ID, 100L);

        System.out.println("第一次 remove -> " + first + ", 第二次 -> " + second);
        System.out.println("集合是否还存在 = " + likeRedisService.hasCache(CONTENT_ID)
                + "（Redis 会自动删除空集合）");

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(likeRedisService.hasCache(CONTENT_ID)).isFalse();
    }

    @Test
    void missingCacheIsDetectable() {
        likeRedisService.deleteCache(CONTENT_ID);

        System.out.println("缓存不存在时 hasCache = " + likeRedisService.hasCache(CONTENT_ID)
                + ", size = " + likeRedisService.size(CONTENT_ID)
                + ", isLiked = " + likeRedisService.isLiked(CONTENT_ID, 100L));

        assertThat(likeRedisService.hasCache(CONTENT_ID)).isFalse();
        assertThat(likeRedisService.size(CONTENT_ID)).isZero();
        // 注意：这里 false 不代表用户没赞过，只代表"缓存不知道" —— 必须回源 DB
        assertThat(likeRedisService.isLiked(CONTENT_ID, 100L)).isFalse();
    }
}
