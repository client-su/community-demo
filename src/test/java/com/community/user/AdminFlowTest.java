package com.community.user;

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
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 封禁/解封流程：管理员封禁后旧 token 立即 403 → 登录也被拒 → 解封恢复；
 * 非管理员调管理接口 403；不能封禁自己；目标不存在 404。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redis;

    private String adminName;
    private String userName;
    private String adminToken;
    private String userToken;
    private long adminId;
    private long userId;

    @BeforeEach
    void setUp() throws Exception {
        long ts = System.currentTimeMillis() % 1_000_000;
        adminName = "adm_" + ts;
        userName = "usr_" + ts;
        clearKeys("rate:*");

        register(adminName);
        register(userName);
        adminToken = login(adminName);
        userToken = login(userName);

        adminId = userIdOf(adminName);
        userId = userIdOf(userName);

        // 把 admin 提升为管理员（role=1）
        jdbcTemplate.update("UPDATE `user` SET role = 1 WHERE id = ?", adminId);
    }

    @AfterEach
    void tearDown() {
        clearKeys("rate:*");
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

    private long userIdOf(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM `user` WHERE username = ?", Long.class, username);
    }

    private void setStatus(String token, long targetId, int status, int expectedHttp) throws Exception {
        mockMvc.perform(put("/api/admin/users/" + targetId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":" + status + "}"))
                .andExpect(status().is(expectedHttp));
    }

    @Test
    void adminBansAndUnbansUser() throws Exception {
        // 1) 封禁前：用户正常访问
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        // 2) 管理员封禁
        setStatus(adminToken, userId, 0, 200);

        // 3) 旧 token 立即失效（拦截器点查 status -> 403）
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        // 4) 被封禁后登录也被拒
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + userName + "\",\"password\":\"Passw0rd!2026\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        // 5) 解封后恢复
        setStatus(adminToken, userId, 1, 200);
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdminCallingAdminApiIs403() throws Exception {
        setStatus(userToken, adminId, 0, 403);
        // 权限校验发生在任何写操作之前：管理员的 status 不会被改动
        Integer adminStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM `user` WHERE id = ?", Integer.class, adminId);
        org.assertj.core.api.Assertions.assertThat(adminStatus).isEqualTo(1);
    }

    @Test
    void adminCannotBanSelf() throws Exception {
        setStatus(adminToken, adminId, 0, 403);
    }

    @Test
    void targetNotFoundIs404() throws Exception {
        setStatus(adminToken, 999_999_999L, 0, 404);
        mockMvc.perform(put("/api/admin/users/999999999/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void invalidStatusValueIs400() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void profileExposesRoleOnlyToSelf() throws Exception {
        // 管理员看自己：role = 1
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value(1));

        // 普通用户看自己：role = 0
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value(0));

        // 匿名看公开资料：role 恒为 null，不暴露管理身份
        mockMvc.perform(get("/api/users/" + adminId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").isEmpty());
    }
}
