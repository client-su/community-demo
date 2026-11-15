package com.community.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 匿名访问边界。
 *
 * <p>防的回归是：将来调整鉴权白名单时"顺手"把写接口也放开——那样任何人都能匿名发内容，
 * 而现有 45 个用例全都是带 token 调用的，一个都不会报警。
 *
 * <p>同时锁住另一半语义：匿名能读，但"需要知道我是谁"的接口仍然必须登录。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ContentAnonymousAccessTest {

    private static final long MISSING_ID = 999_999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousCanReadFeedAndHot() throws Exception {
        mockMvc.perform(get("/api/contents").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/contents/hot").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void anonymousDetailPassesAuthButStill404WhenMissing() throws Exception {
        // 404 而不是 401，才证明请求真的穿过了鉴权层
        mockMvc.perform(get("/api/contents/" + MISSING_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void anonymousPublishIsStill401() throws Exception {
        mockMvc.perform(post("/api/contents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"匿名发布试试\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void anonymousLikeStateIsStill401() throws Exception {
        // 点赞态要回答"你赞过没有"，匿名没有"你"，必须登录
        mockMvc.perform(get("/api/contents/1/like"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void brokenTokenOnPublicReadIsStill401() throws Exception {
        mockMvc.perform(get("/api/contents").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }
}
