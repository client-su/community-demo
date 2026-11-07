package com.community.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ContentMapperTest {

    @Autowired
    private ContentMapper contentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Content sample(long userId, String body) {
        Content content = new Content();
        content.setUserId(userId);
        // 幂等键在第 8 步才由客户端提供；现在必须给不重复的值，空串会撞 uk_user_idem
        content.setIdemKey(UUID.randomUUID().toString());
        content.setBody(body);
        return content;
    }

    @Test
    void insertAndFindVisible() {
        Content c = sample(1L, "第一条内容");
        assertThat(contentMapper.insert(c)).isEqualTo(1);
        assertThat(c.getId()).isNotNull();

        Content loaded = contentMapper.findVisibleById(c.getId()).orElseThrow();
        System.out.println("回读 = id:" + loaded.getId() + " userId:" + loaded.getUserId()
                + " body:" + loaded.getBody() + " createdAt:" + loaded.getCreatedAt());

        assertThat(loaded.getBody()).isEqualTo("第一条内容");
        assertThat(loaded.getUserId()).isEqualTo(1L);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void softDeletedIsInvisible() {
        Content c = sample(1L, "将被软删");
        contentMapper.insert(c);

        jdbcTemplate.update("UPDATE `content` SET deleted = 1 WHERE id = ?", c.getId());

        assertThat(contentMapper.findVisibleById(c.getId())).isEmpty();
        System.out.println("软删后 findVisibleById = " + contentMapper.findVisibleById(c.getId()));
    }

    @Test
    void cursorPagination() {
        for (int i = 1; i <= 5; i++) {
            contentMapper.insert(sample(2L, "内容-" + i));
        }

        List<Content> firstPage = contentMapper.page(Long.MAX_VALUE, 3);
        System.out.println("第一页: " + firstPage.stream().map(Content::getId).toList()
                + " -> " + firstPage.stream().map(Content::getBody).toList());

        // 下一页的游标 = 本页最后一条的 id（不是页码）
        long nextCursor = firstPage.get(firstPage.size() - 1).getId();
        List<Content> secondPage = contentMapper.page(nextCursor, 3);
        System.out.println("第二页(cursor=" + nextCursor + "): "
                + secondPage.stream().map(Content::getId).toList()
                + " -> " + secondPage.stream().map(Content::getBody).toList());

        assertThat(firstPage).hasSize(3);
        assertThat(secondPage).hasSize(2);
        // 游标是本页最后一条的 id：它本身已被本页消费，所以下一页必须严格小于它
        assertThat(firstPage.get(firstPage.size() - 1).getId()).isEqualTo(nextCursor);
        assertThat(firstPage.subList(0, firstPage.size() - 1).stream().map(Content::getId))
                .allMatch(id -> id > nextCursor, "除最后一条外，第一页的 id 都应大于游标");
        assertThat(secondPage.stream().map(Content::getId))
                .allMatch(id -> id < nextCursor, "第二页的 id 都应小于游标");
        assertThat(secondPage.get(0).getBody()).isEqualTo("内容-2");
    }

    @Test
    void pageQueryPlan() {
        List<Map<String, Object>> plan = jdbcTemplate.queryForList("""
                EXPLAIN SELECT id, user_id, body, created_at, updated_at
                  FROM `content`
                 WHERE deleted = 0 AND id < 1000
                 ORDER BY id DESC
                 LIMIT 3
                """);
        Map<String, Object> row = plan.get(0);
        System.out.println("EXPLAIN -> type=" + row.get("type")
                + ", key=" + row.get("key")
                + ", rows=" + row.get("rows")
                + ", Extra=" + row.get("Extra"));

        assertThat(plan).hasSize(1);
    }
}
