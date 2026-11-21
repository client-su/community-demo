package com.community.tag;

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
 * 标签流程：匿名读标签列表 → 发布带标签 → 按标签读内容 → 非法标签 40403 且整体回滚。
 *
 * <p>刻意不加 @Transactional：回滚验证需要在事务提交/回滚边界之外观察数据库，
 * 否则“标签校验失败整体回滚”无法被验证（行仍留在测试事务里）。
 * 因此测试结束必须手工清理 DB 与 Redis。
 */
@SpringBootTest
@AutoConfigureMockMvc
class TagFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String username;
    private String token;
    private long tagId;
    private final List<Long> createdContentIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        username = "tag_" + (System.currentTimeMillis() % 1_000_000);
        clearKeys("rate:*");
        clearKeys("idem:content:*");

        // 确保预置标签存在（幂等）
        jdbcTemplate.update("INSERT IGNORE INTO `tag` (name) VALUES ('技术')");
        tagId = jdbcTemplate.queryForObject("SELECT id FROM `tag` WHERE name = '技术'", Long.class);

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
            jdbcTemplate.update("DELETE FROM `content_tag` WHERE content_id = ?", id);
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

    private long publish(String body, String tagIdsJson, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post("/api/contents")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "tg-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"" + body + "\",\"tagIds\":" + tagIdsJson + "}"))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).path("data").path("id").asLong();
        if (id > 0) {
            createdContentIds.add(id);
        }
        return id;
    }

    @Test
    void tagListIsAnonymousReadable() throws Exception {
        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + tagId + ")]").exists());
    }

    @Test
    void publishWithTagThenReadByTag() throws Exception {
        long contentId = publish("带技术标签的内容", "[" + tagId + "]", 201);
        assertThat(contentId).isGreaterThan(0);

        // 按标签读内容：匿名可读，能查到刚发布的那条
        mockMvc.perform(get("/api/tags/" + tagId + "/contents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + contentId + ")]").exists());
    }

    @Test
    void publishWithoutTagIsBackwardCompatible() throws Exception {
        long contentId = publish("不带标签的内容", "null", 201);
        assertThat(contentId).isGreaterThan(0);

        // 不带标签的内容不应出现在任何标签下
        mockMvc.perform(get("/api/tags/" + tagId + "/contents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + contentId + ")]").doesNotExist());
    }

    @Test
    void invalidTagIdIs404AndContentRollsBack() throws Exception {
        mockMvc.perform(post("/api/contents")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "tg-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"标签非法内容\",\"tagIds\":[99999999]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40403));

        // 事务回滚：内容没有落库（发布与绑标签同一事务，标签校验失败整体回滚）
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `content` WHERE body = '标签非法内容'", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void unknownTagForReadingIs404() throws Exception {
        mockMvc.perform(get("/api/tags/99999999/contents"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40403));
    }
}
