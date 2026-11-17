package com.community.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户主页与「我的」维度：公开资料 / 某人发布 / 我发布的 / 我赞过的。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserPageFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String userA;
    private String userB;
    private String tokenA;
    private String tokenB;
    private long userIdA;
    private long contentIdA;

    @BeforeEach
    void setUp() throws Exception {
        long ts = System.currentTimeMillis() % 1_000_000;
        userA = "upa_" + ts;
        userB = "upb_" + ts;

        register(userA, "Alice");
        register(userB, "Bob");
        tokenA = login(userA);
        tokenB = login(userB);

        userIdA = profileId(tokenA);

        // A 发布一条内容
        String body = mockMvc.perform(post("/api/contents")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", "up-" + ts)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"用户主页测试内容\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        contentIdA = objectMapper.readTree(body).path("data").path("id").asLong();

        // B 点赞 A 的内容
        mockMvc.perform(post("/api/contents/" + contentIdA + "/like")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());
    }

    private void register(String username, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!2026\",\"nickname\":\"" + nickname + "\"}"))
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

    private long profileId(String token) throws Exception {
        String body = mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    @Test
    void publicProfileAndUserContents() throws Exception {
        // 公开资料（匿名）
        mockMvc.perform(get("/api/users/" + userIdA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("Alice"));

        // 某人发布的内容（匿名），带昵称
        mockMvc.perform(get("/api/users/" + userIdA + "/contents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(contentIdA))
                .andExpect(jsonPath("$.data.items[0].nickname").value("Alice"));
    }

    @Test
    void myContentsAndMyLikes() throws Exception {
        // A 的我发布的
        mockMvc.perform(get("/api/me/contents").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(contentIdA));

        // B 的我赞过的（赞了 A 的内容）
        mockMvc.perform(get("/api/me/likes").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(contentIdA));

        // 未登录访问「我的」接口 → 401
        mockMvc.perform(get("/api/me/likes"))
                .andExpect(status().isUnauthorized());
    }
}
