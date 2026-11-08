package com.community.like;

import com.community.common.UserContext;
import com.community.content.Content;
import com.community.content.ContentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发点赞：50 个线程用同一个用户点同一条内容。
 *
 * <p>刻意不加 @Transactional —— 事务会把写入塞进同一连接，既测不出并发也验证不了提交后的状态。
 * 因此测试结束必须手工清理 DB 与 Redis。
 */
@SpringBootTest
class LikeConcurrencyTest {

    private static final int THREADS = 50;

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeMapper likeMapper;

    @Autowired
    private ContentMapper contentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redis;

    private Long createdContentId;

    @AfterEach
    void tearDown() {
        UserContext.clear();
        if (createdContentId != null) {
            jdbcTemplate.update("DELETE FROM `content_like` WHERE content_id = ?", createdContentId);
            jdbcTemplate.update("DELETE FROM `content` WHERE id = ?", createdContentId);
            redis.delete("like:content:" + createdContentId);
            redis.opsForZSet().remove("hot:content", String.valueOf(createdContentId));
            redis.delete("content:detail:" + createdContentId);
        }
    }

    private long newContent(long userId) {
        Content c = new Content();
        c.setUserId(userId);
        c.setIdemKey("concurrency-" + UUID.randomUUID());
        c.setBody("并发测试内容");
        c.setDeleted(Content.NOT_DELETED);
        contentMapper.insert(c);
        createdContentId = c.getId();
        return c.getId();
    }

    @Test
    void fiftyThreadsSameUserLikeOnce() throws Exception {
        long contentId = newContent(1L);
        long userId = 77L;

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(THREADS);
        AtomicInteger success = new AtomicInteger();
        List<Throwable> errors = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                try {
                    // ThreadLocal 不跨线程：每个工作线程自己设置
                    UserContext.set(userId);
                    startGate.await();
                    likeService.like(contentId);
                    success.incrementAndGet();
                } catch (Throwable t) {
                    synchronized (errors) {
                        errors.add(t);
                    }
                } finally {
                    UserContext.clear();
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();                       // 同时放行
        boolean finished = doneGate.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        long likeRows = likeMapper.countByContent(contentId);
        Integer likeCount = contentMapper.findVisibleById(contentId).orElseThrow().getLikeCount();
        Long redisMembers = redis.opsForSet().size("like:content:" + contentId);

        System.out.println("并发线程数 = " + THREADS);
        System.out.println("成功返回 = " + success.get() + "，异常数 = " + errors.size());
        System.out.println("content_like 行数 = " + likeRows);
        System.out.println("content.like_count = " + likeCount);
        System.out.println("Redis 集合成员数 = " + redisMembers);

        if (!errors.isEmpty()) {
            System.out.println("首个异常: " + errors.get(0));
        }

        assertThat(finished).as("所有任务应在超时前完成").isTrue();
        assertThat(errors).as("并发点赞不应抛异常（INSERT IGNORE 把冲突转为 0 行）").isEmpty();
        assertThat(success.get()).isEqualTo(THREADS);
        assertThat(likeRows).as("唯一键保证一人一条").isEqualTo(1);
        assertThat(likeCount).as("只有真正新增的那一次才加计数").isEqualTo(1);
        assertThat(redisMembers).as("Redis 集合去重").isEqualTo(1L);
    }

    @Test
    void twoDifferentUsersBothSucceed() throws Exception {
        long contentId = newContent(1L);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            long userId = (i % 2 == 0) ? 101L : 102L;   // 只有两个用户
            pool.submit(() -> {
                try {
                    UserContext.set(userId);
                    startGate.await();
                    likeService.like(contentId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    UserContext.clear();
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        doneGate.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        long likeRows = likeMapper.countByContent(contentId);
        Integer likeCount = contentMapper.findVisibleById(contentId).orElseThrow().getLikeCount();

        System.out.println("两个用户各 25 线程 -> content_like 行数 = " + likeRows
                + ", like_count = " + likeCount);

        assertThat(likeRows).as("每个用户各一条").isEqualTo(2);
        assertThat(likeCount).isEqualTo(2);
    }
}
