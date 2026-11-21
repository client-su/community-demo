package com.community.tag;

import com.community.content.Content;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 标签与关联表：存在性校验 / 幂等绑定 / 按标签游标分页。
 */
@SpringBootTest
@Transactional
class TagMapperTest {

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long newTag(String name) {
        jdbcTemplate.update("INSERT IGNORE INTO `tag` (name) VALUES (?)", name);
        return jdbcTemplate.queryForObject("SELECT id FROM `tag` WHERE name = ?", Long.class, name);
    }

    private long newContent(long authorId) {
        jdbcTemplate.update("INSERT INTO `content` (user_id, idem_key, body) VALUES (?, ?, ?)",
                authorId, UUID.randomUUID().toString(), "标签测试内容");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM `content` WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, authorId);
    }

    @Test
    void findExistingIdsFiltersUnknownIds() {
        long tagA = newTag("测试标签甲");
        long tagB = newTag("测试标签乙");

        List<Long> existing = tagMapper.findExistingIds(List.of(tagA, tagB, 99999999L));

        System.out.println("存在的标签 = " + existing);
        assertThat(existing).containsExactlyInAnyOrder(tagA, tagB);
    }

    @Test
    void insertContentTagsIsIdempotent() {
        long tagA = newTag("测试标签丙");
        long contentId = newContent(9501L);

        int first = tagMapper.insertContentTags(contentId, List.of(tagA));
        int second = tagMapper.insertContentTags(contentId, List.of(tagA));

        System.out.println("第一次绑定 -> " + first + " 行，第二次 -> " + second + " 行（重复被忽略）");
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }

    @Test
    void pageContentsByTagCursorWorks() {
        long tagId = newTag("测试标签丁");
        long c1 = newContent(9502L);
        long c2 = newContent(9502L);
        long c3 = newContent(9502L);
        tagMapper.insertContentTags(c1, List.of(tagId));
        tagMapper.insertContentTags(c2, List.of(tagId));
        tagMapper.insertContentTags(c3, List.of(tagId));

        List<Content> page1 = tagMapper.pageContentsByTag(tagId, Long.MAX_VALUE, 2);
        System.out.println("按标签第一页 = " + page1.stream().map(Content::getId).toList());

        assertThat(page1).hasSize(2);
        assertThat(page1.get(0).getId()).isEqualTo(c3);

        List<Content> page2 = tagMapper.pageContentsByTag(tagId, page1.get(1).getId(), 2);
        System.out.println("按标签第二页 = " + page2.stream().map(Content::getId).toList());
        assertThat(page2).hasSize(1);
        assertThat(page2.get(0).getId()).isEqualTo(c1);
    }
}
