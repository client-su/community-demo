package com.community.flow;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内容流程：注册登录 → 发布（幂等）→ 列表 → 详情 → 删除 → 404。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContentFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redis;

    private String username;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        username = "content_flow_" + (System.currentTimeMillis() % 1_000_000);
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
        clearKeys("rate:*");
        clearKeys("idem:content:*");
    }

    private void clearKeys(String pattern) {
        Set<String> keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private long publish(String body, String idemKey, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post("/api/contents")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"" + body + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return node.path("data").path("id").asLong();
    }

    @Test
    void publishListDetailDelete() throws Exception {
        String idemKey = "flow-" + UUID.randomUUID();

        // 1) 首次发布 201
        long id = publish("流程测试内容", idemKey, 201);

        // 2) 同幂等键重放：200 且同 id
        long replayId = publish("流程测试内容", idemKey, 200);
        org.assertj.core.api.Assertions.assertThat(replayId).isEqualTo(id);

        // 3) 列表里能查到
        mockMvc.perform(get("/api/contents").param("size", "50")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + id + ")]").exists());

        // 4) 详情
        mockMvc.perform(get("/api/contents/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("流程测试内容"));

        // 5) 删除
        mockMvc.perform(delete("/api/contents/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 6) 删除后详情 404
        mockMvc.perform(get("/api/contents/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void publishWithoutTokenIs401AndBlankBodyIs400() throws Exception {
        mockMvc.perform(post("/api/contents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"无 token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        mockMvc.perform(post("/api/contents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void deleteOthersContentReturnsSame404() throws Exception {
        // 用另一个用户注册并发布
        String other = "other_" + (System.currentTimeMillis() % 1_000_000);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + other + "\",\"password\":\"Passw0rd!2026\"}"))
                .andExpect(status().isCreated());
        String otherToken = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + other + "\",\"password\":\"Passw0rd!2026\"}"))
                .andReturn().getResponse().getContentAsString())
                .path("data").path("token").asText();

        String otherBody = mockMvc.perform(post("/api/contents")
                        .header("Authorization", "Bearer " + otherToken)
                        .header("Idempotency-Key", "flow-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"别人的内容\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long otherId = objectMapper.readTree(otherBody).path("data").path("id").asLong();

        // 用当前用户删别人的内容：与"不存在"返回完全一致
        mockMvc.perform(delete("/api/contents/" + otherId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        mockMvc.perform(delete("/api/contents/999999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }
}
