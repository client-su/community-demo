package com.community.bookmark;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 收藏关系表：幂等插入 / 幂等删除 / 唯一键兜底 / 游标分页。
 * 与 LikeMapperTest 同构，验证 bookmark 表的行为与 content_like 一致。
 */
@SpringBootTest
@Transactional
class BookmarkMapperTest {

    @Autowired
    private BookmarkMapper bookmarkMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 直接造一条内容返回 id：bookmark 只存关系，内容由 content 表提供 */
    private long newContent(long authorId) {
        jdbcTemplate.update("INSERT INTO `content` (user_id, idem_key, body) VALUES (?, ?, ?)",
                authorId, UUID.randomUUID().toString(), "收藏测试内容");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM `content` WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, authorId);
    }

    @Test
    void insertIgnoreIsIdempotent() {
        long userId = 9301L;
        long contentId = newContent(9301L);

        int first = bookmarkMapper.insertIgnore(userId, contentId);
        int second = bookmarkMapper.insertIgnore(userId, contentId);

        System.out.println("第一次收藏 -> " + first + " 行，第二次 -> " + second + " 行（重复被忽略）");
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(bookmarkMapper.exists(userId, contentId)).isTrue();
    }

    @Test
    void deleteIsIdempotent() {
        long userId = 9302L;
        long contentId = newContent(9302L);
        bookmarkMapper.insertIgnore(userId, contentId);

        int first = bookmarkMapper.delete(userId, contentId);
        int second = bookmarkMapper.delete(userId, contentId);

        System.out.println("第一次取消收藏 -> " + first + " 行，第二次 -> " + second + " 行");
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(bookmarkMapper.exists(userId, contentId)).isFalse();
    }

    @Test
    void uniqueKeyRejectsRawDuplicateInsert() {
        long userId = 9303L;
        long contentId = newContent(9303L);

        jdbcTemplate.update("INSERT INTO `bookmark` (user_id, content_id) VALUES (?, ?)",
                userId, contentId);

        // 不走 INSERT IGNORE 时，数据库会明确拒绝 —— 这就是最后防线
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO `bookmark` (user_id, content_id) VALUES (?, ?)",
                userId, contentId))
                .isInstanceOf(DataIntegrityViolationException.class);

        System.out.println("裸 INSERT 重复收藏 -> DataIntegrityViolationException（唯一键拦截）");
    }

    @Test
    void pageByUserReturnsInReverseBookmarkOrder() {
        long userId = 9304L;
        long contentA = newContent(9304L);
        long contentB = newContent(9304L);

        bookmarkMapper.insertIgnore(userId, contentA);
        bookmarkMapper.insertIgnore(userId, contentB);

        List<BookmarkedContentRow> page = bookmarkMapper.pageByUser(userId, Long.MAX_VALUE, 10);

        System.out.println("我收藏的 = " + page.stream().map(BookmarkedContentRow::getId).toList());
        // 按 bookmark.id 倒序：后收藏的（contentB）在前
        assertThat(page).hasSize(2);
        assertThat(page.get(0).getId()).isEqualTo(contentB);
        assertThat(page.get(1).getId()).isEqualTo(contentA);
        assertThat(page.get(0).getBookmarkId()).isNotNull();
        assertThat(page.get(0).getBody()).isEqualTo("收藏测试内容");
    }
}
