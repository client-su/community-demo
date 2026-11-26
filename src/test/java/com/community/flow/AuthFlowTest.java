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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证流程：注册 → 重复注册 409 → 登录 → 未授权访问 401。
 *
 * <p>注意：限流键在 Redis 里不会随事务回滚，必须每个用例前清掉，
 * 否则同一个 127.0.0.1 连续跑几次登录就会被 429 拦住（测试互相污染）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redis;

    private String username;

    @BeforeEach
    void setUp() {
        username = "flow_" + (System.currentTimeMillis() % 1_000_000);
        clearRateLimitKeys();
    }

    @AfterEach
    void tearDown() {
        clearRateLimitKeys();
    }

    private void clearRateLimitKeys() {
        Set<String> keys = redis.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private String json(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void registerThenLoginThenAccessMe() throws Exception {
        // 1) 注册
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, "Passw0rd!2026")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.username").value(username))
                // 安全回归：响应体绝不能出现密码哈希
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        // 2) 重复注册 -> 409
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, "Passw0rd!2026")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));

        // 3) 登录拿 token
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, "Passw0rd!2026")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();

        JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = body.path("data").path("token").asText();
        assertThat(token).isNotBlank();

        // 4) 带 token 访问 /api/me
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));

        // 5) 不带头/带错 token -> 401
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithWrongPasswordIs401WithUnifiedMessage() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, "Passw0rd!2026")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));

        // 用户不存在时，响应必须完全一致（防用户枚举）
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("no_such_user_xyz", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void registerWithBadInputIs400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("ab", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("username")))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("password")));
    }

    /**
     * 登录限流：同一 IP 每分钟 5 次，第 6 次 429。
     *
     * <p>固定窗口按 epochSecond/60 分桶，若刚好跨桶，6 次请求会被算进两个窗口导致用例偶发失败。
     * 所以临近桶边界时先等过去；一个桶只剩 3 秒以上，6 次请求（每次一次 BCrypt）绰绰有余。
     */
    @Test
    void loginIsRateLimitedAfterFiveAttemptsPerMinute() throws Exception {
        long secondInWindow = Instant.now().getEpochSecond() % 60;
        if (secondInWindow >= 57) {
            Thread.sleep((60 - secondInWindow) * 1000 + 200);
        }

        // 独占一个 IP：限流键按 IP + 时间桶隔离，避免和同类的其他请求共用计数
        String ip = "198.51.100.7";
        String body = json("no_such_user_xyz", "wrong-password");

        // 前 5 次：凭证错误，正常 401（此时限流还放行）
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(request -> {
                                request.setRemoteAddr(ip);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40101));
        }

        // 第 6 次：在进入 Controller 之前就被 RateLimitInterceptor 挡住
        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42900))
                .andExpect(header().string("X-RateLimit-Limit", "5"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("Retry-After"));
    }
}
