package com.community.bookmark;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 收藏流程：登录 → 发布 → 收藏（幂等）→ 收藏态 → 我的收藏 → 取消 → 收藏态反转。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookmarkFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redis;

    private String username;
    private String token;
    private long contentId;

    @BeforeEach
    void setUp() throws Exception {
        username = "bookmark_" + (System.currentTimeMillis() % 1_000_000);
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

        // 发布一条内容作为收藏对象
        String published = mockMvc.perform(post("/api/contents")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "bm-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"收藏流程测试内容\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        contentId = objectMapper.readTree(published).path("data").path("id").asLong();
    }

    @AfterEach
    void tearDown() {
        clearKeys("rate:*");
        clearKeys("idem:content:*");
    }

    private void clearKeys(String pattern) {
        Set<String> keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void bookmarkReplayStateAndUnbookmark() throws Exception {
        // 1) 收藏：bookmarked = true
        mockMvc.perform(post("/api/contents/" + contentId + "/bookmark")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentId").value(contentId))
                .andExpect(jsonPath("$.data.bookmarked").value(true));

        // 2) 重复收藏：幂等，仍是 true
        mockMvc.perform(post("/api/contents/" + contentId + "/bookmark")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarked").value(true));

        // 3) 收藏态查询
        mockMvc.perform(get("/api/contents/" + contentId + "/bookmark")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarked").value(true));

        // 4) 我的收藏里能查到
        mockMvc.perform(get("/api/me/bookmarks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + contentId + ")]").exists());

        // 5) 取消收藏
        mockMvc.perform(delete("/api/contents/" + contentId + "/bookmark")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarked").value(false));

        // 6) 取消后状态反转，列表里也查不到
        mockMvc.perform(get("/api/contents/" + contentId + "/bookmark")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarked").value(false));

        mockMvc.perform(get("/api/me/bookmarks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + contentId + ")]").doesNotExist());
    }

    @Test
    void bookmarkNonexistentContentIs404() throws Exception {
        mockMvc.perform(post("/api/contents/999999999/bookmark")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void bookmarkRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/contents/" + contentId + "/bookmark"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void unbookmarkWithoutBookmarkIsIdempotent() throws Exception {
        // 没收藏过也取消：200，不报错
        mockMvc.perform(delete("/api/contents/" + contentId + "/bookmark")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarked").value(false));
    }

    @Test
    void bookmarkDoesNotAffectLikeCountOrHotRank() throws Exception {
        // 收藏不影响点赞数
        mockMvc.perform(post("/api/contents/" + contentId + "/bookmark")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String feed = mockMvc.perform(get("/api/contents").param("size", "50"))
                .andReturn().getResponse().getContentAsString();
        JsonNode items = objectMapper.readTree(feed).path("data").path("items");
        assertThat(items.findValues("id").stream().map(JsonNode::asLong))
                .contains(contentId);

        String hot = mockMvc.perform(get("/api/contents/hot"))
                .andReturn().getResponse().getContentAsString();
        JsonNode hotItems = objectMapper.readTree(hot).path("data");
        // 0 赞的内容不会出现在热榜里（收藏不推热榜）
        assertThat(hotItems.findValues("id").stream().map(JsonNode::asLong))
                .doesNotContain(contentId);
    }
}
