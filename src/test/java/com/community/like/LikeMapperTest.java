package com.community.like;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class LikeMapperTest {

    @Autowired
    private LikeMapper likeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertIgnoreIsIdempotent() {
        long userId = 9001L;
        long contentId = 999001L;

        int first = likeMapper.insertIgnore(userId, contentId);
        int second = likeMapper.insertIgnore(userId, contentId);

        System.out.println("第一次 insertIgnore -> " + first + " 行");
        System.out.println("第二次 insertIgnore -> " + second + " 行（重复被忽略）");
        System.out.println("exists = " + likeMapper.exists(userId, contentId)
                + ", count = " + likeMapper.countByContent(contentId));

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(likeMapper.countByContent(contentId)).isEqualTo(1);
        assertThat(likeMapper.exists(userId, contentId)).isTrue();
    }

    @Test
    void deleteIsIdempotent() {
        long userId = 9002L;
        long contentId = 999002L;
        likeMapper.insertIgnore(userId, contentId);

        int first = likeMapper.delete(userId, contentId);
        int second = likeMapper.delete(userId, contentId);

        System.out.println("第一次 delete -> " + first + " 行，第二次 -> " + second + " 行");
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }

    @Test
    void uniqueKeyRejectsRawDuplicateInsert() {
        long userId = 9003L;
        long contentId = 999003L;

        jdbcTemplate.update("INSERT INTO `content_like` (user_id, content_id) VALUES (?, ?)",
                userId, contentId);

        // 不走 INSERT IGNORE 时，数据库会明确拒绝 —— 这就是最后防线
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO `content_like` (user_id, content_id) VALUES (?, ?)",
                userId, contentId))
                .isInstanceOf(DataIntegrityViolationException.class);

        System.out.println("裸 INSERT 重复 -> DataIntegrityViolationException（唯一键拦截）");
    }

    @Test
    void differentUsersCanLikeSameContent() {
        long contentId = 999004L;
        assertThat(likeMapper.insertIgnore(9101L, contentId)).isEqualTo(1);
        assertThat(likeMapper.insertIgnore(9102L, contentId)).isEqualTo(1);

        System.out.println("两个不同用户赞同一内容 -> count = " + likeMapper.countByContent(contentId));
        assertThat(likeMapper.countByContent(contentId)).isEqualTo(2);
    }
}
