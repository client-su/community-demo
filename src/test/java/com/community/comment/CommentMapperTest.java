package com.community.comment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 评论表：插入回填 / 游标分页 / 软删不可见。
 */
@SpringBootTest
@Transactional
class CommentMapperTest {

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long newContent(long authorId) {
        jdbcTemplate.update("INSERT INTO `content` (user_id, idem_key, body) VALUES (?, ?, ?)",
                authorId, UUID.randomUUID().toString(), "评论测试内容");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM `content` WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, authorId);
    }

    private Comment sample(long contentId, long userId, String body) {
        Comment comment = new Comment();
        comment.setContentId(contentId);
        comment.setUserId(userId);
        comment.setBody(body);
        comment.setDeleted(Comment.NOT_DELETED);
        return comment;
    }

    @Test
    void insertBackfillsId() {
        long contentId = newContent(9401L);
        Comment comment = sample(contentId, 9401L, "第一条评论");
        assertThat(commentMapper.insert(comment)).isEqualTo(1);
        assertThat(comment.getId()).isNotNull();

        Comment loaded = commentMapper.findVisibleById(comment.getId()).orElseThrow();
        System.out.println("回读评论 = id:" + loaded.getId() + " contentId:" + loaded.getContentId()
                + " body:" + loaded.getBody() + " createdAt:" + loaded.getCreatedAt());
        assertThat(loaded.getBody()).isEqualTo("第一条评论");
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void pageByContentCursorWorks() {
        long contentId = newContent(9402L);
        for (int i = 1; i <= 5; i++) {
            commentMapper.insert(sample(contentId, 9402L, "评论-" + i));
        }

        List<Comment> firstPage = commentMapper.pageByContent(contentId, Long.MAX_VALUE, 3);
        System.out.println("评论第一页 = " + firstPage.stream().map(Comment::getId).toList());

        // 倒序：第一页是最后插的 3 条
        assertThat(firstPage).hasSize(3);
        assertThat(firstPage.get(0).getBody()).isEqualTo("评论-5");

        long cursor = firstPage.get(2).getId();
        List<Comment> secondPage = commentMapper.pageByContent(contentId, cursor, 3);
        System.out.println("评论第二页 = " + secondPage.stream().map(Comment::getId).toList());
        assertThat(secondPage).hasSize(2);
        assertThat(secondPage.get(0).getBody()).isEqualTo("评论-2");
        assertThat(secondPage.get(1).getBody()).isEqualTo("评论-1");
    }

    @Test
    void softDeletedCommentIsInvisible() {
        long contentId = newContent(9403L);
        Comment comment = sample(contentId, 9403L, "将被软删");
        commentMapper.insert(comment);

        commentMapper.softDelete(comment.getId());

        assertThat(commentMapper.findVisibleById(comment.getId())).isEmpty();
        System.out.println("软删后 findVisibleById = " + commentMapper.findVisibleById(comment.getId()));
    }
}
