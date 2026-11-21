package com.community.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 搜索流程：发布 → 中文关键词命中 → 软删不出现 → 游标分页不重不漏。
 *
 * <p>刻意不加 @Transactional：InnoDB 全文索引只索引【已提交】的数据，
 * 事务里刚插入的行 MATCH 搜不到。因此测试结束必须手工清理 DB 与 Redis。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ContentSearchTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redis;

    private String username;
    private String token;
    private final List<Long> createdContentIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        username = "search_" + (System.currentTimeMillis() % 1_000_000);
        clearKeys("rate:*");
        clearKeys("idem:content:*");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!2026\"}"))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!2026\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(body).path("data").path("token").asText();
    }

    @AfterEach
    void tearDown() {
        for (Long id : createdContentIds) {
            jdbcTemplate.update("DELETE FROM `content` WHERE id = ?", id);
        }
        jdbcTemplate.update("DELETE FROM `user` WHERE username = ?", username);
        clearKeys("rate:*");
        clearKeys("idem:content:*");
    }

    private void clearKeys(String pattern) {
        Set<String> keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private long publish(String text) throws Exception {
        String response = mockMvc.perform(post("/api/contents")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "sr-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"" + text + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).path("data").path("id").asLong();
        createdContentIds.add(id);
        return id;
    }

    @Test
    void chineseKeywordMatchesOnlyRelevantContent() throws Exception {
        long hit = publish("周末去爬山看日出，风很大");
        long miss = publish("今天在家钓鱼，一无所获");

        mockMvc.perform(get("/api/contents/search").param("q", "爬山"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + hit + ")]").exists())
                .andExpect(jsonPath("$.data.items[?(@.id == " + miss + ")]").doesNotExist());
    }

    @Test
    void missingOrBlankKeywordIs400() throws Exception {
        // 缺 q 参数
        mockMvc.perform(get("/api/contents/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        // q 是空白
        mockMvc.perform(get("/api/contents/search").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void deletedContentDoesNotAppearInSearch() throws Exception {
        long id = publish("搜得到的已删内容标记词");
        jdbcTemplate.update("UPDATE `content` SET deleted = 1 WHERE id = ?", id);

        mockMvc.perform(get("/api/contents/search").param("q", "标记词"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + id + ")]").doesNotExist());
    }

    @Test
    void cursorPaginationDoesNotRepeatOrSkip() throws Exception {
        // 三条都含同一关键词，size=2 应分成 2 页且不重不漏
        long id1 = publish("分页关键词甲乙丙");
        long id2 = publish("分页关键词甲乙丙");
        long id3 = publish("分页关键词甲乙丙");

        String page1Body = mockMvc.perform(get("/api/contents/search")
                        .param("q", "分页关键词").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        JsonNode page1 = objectMapper.readTree(page1Body).path("data");
        long cursor = page1.path("nextCursor").asLong();
        assertThat(cursor).isGreaterThan(0);

        List<Long> page1Ids = new ArrayList<>();
        page1.path("items").forEach(n -> page1Ids.add(n.path("id").asLong()));

        String page2Body = mockMvc.perform(get("/api/contents/search")
                        .param("q", "分页关键词").param("size", "2").param("cursor", String.valueOf(cursor)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode page2 = objectMapper.readTree(page2Body).path("data");
        List<Long> page2Ids = new ArrayList<>();
        page2.path("items").forEach(n -> page2Ids.add(n.path("id").asLong()));

        // 库里可能还有其他内容命中同一个 2-gram（如演示数据），
        // 所以只断言：本测试的三条一条不少、两页之间无重复（id 严格 < cursor 的必然结果）。
        List<Long> all = new ArrayList<>(page1Ids);
        all.addAll(page2Ids);
        assertThat(page1Ids).hasSize(2);
        assertThat(all).contains(id1, id2, id3);
        assertThat(all).doesNotHaveDuplicates();
    }
}
