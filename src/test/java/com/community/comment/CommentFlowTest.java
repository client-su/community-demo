package com.community.comment;

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
 * 评论流程：发布 → 发评论（201，计数+1）→ 匿名读评论 → 删自己的（200，计数-1）
 * → 删别人的 404 → 对不存在内容发评论 40401 → 空正文 400。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redis;

    private String userA;
    private String userB;
    private String tokenA;
    private String tokenB;
    private long contentId;

    @BeforeEach
    void setUp() throws Exception {
        long ts = System.currentTimeMillis() % 1_000_000;
        userA = "cfa_" + ts;
        userB = "cfb_" + ts;
        clearKeys("rate:*");
        clearKeys("idem:content:*");

        register(userA);
        register(userB);
        tokenA = login(userA);
        tokenB = login(userB);

        String body = mockMvc.perform(post("/api/contents")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", "cf-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"评论流程测试内容\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        contentId = objectMapper.readTree(body).path("data").path("id").asLong();
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

    private void register(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!2026\"}"))
                .andExpect(status().isCreated());
    }

    private String login(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!2026\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("token").asText();
    }

    private long comment(String token, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post("/api/contents/" + contentId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"" + body + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    @Test
    void commentListCreateDeleteAndCount() throws Exception {
        // 1) 发评论 -> 201
        long commentId = comment(tokenB, "写得不错", 201);
        assertThat(commentId).isGreaterThan(0);

        // 2) 计数 +1 反映在详情里
        mockMvc.perform(get("/api/contents/" + contentId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentCount").value(1));

        // 3) 匿名可读评论列表，昵称来自 JOIN
        mockMvc.perform(get("/api/contents/" + contentId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(commentId))
                .andExpect(jsonPath("$.data.items[0].body").value("写得不错"));

        // 4) 删自己的评论 -> 200，计数 -1
        mockMvc.perform(delete("/api/comments/" + commentId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/contents/" + contentId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentCount").value(0));
    }

    @Test
    void deleteOthersCommentIs404() throws Exception {
        long commentId = comment(tokenB, "这条评论不是 A 的", 201);

        mockMvc.perform(delete("/api/comments/" + commentId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40402));
    }

    @Test
    void deleteNonexistentCommentIs404() throws Exception {
        mockMvc.perform(delete("/api/comments/999999999")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40402));
    }

    @Test
    void commentOnMissingContentIs404() throws Exception {
        mockMvc.perform(post("/api/contents/999999999/comments")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"对着空气评论\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void blankCommentBodyIs400() throws Exception {
        mockMvc.perform(post("/api/contents/" + contentId + "/comments")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void commentRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/contents/" + contentId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"未登录发评论\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }
}
