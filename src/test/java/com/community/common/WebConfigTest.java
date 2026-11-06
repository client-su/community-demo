package com.community.common;

import com.community.auth.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证白名单划分：受保护路径必须带 token，白名单路径与 /actuator 不受影响。
 * 用一个测试专用端点 /api/ping 来代表"任意受保护业务接口"。
 */
@SpringBootTest
@AutoConfigureMockMvc
class WebConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @TestConfiguration
    static class PingConfig {
        @Bean
        PingController pingController() {
            return new PingController();
        }
    }

    @RestController
    static class PingController {
        @GetMapping("/api/ping")
        Result<Long> ping() {
            return Result.ok(UserContext.get());
        }
    }

    @Test
    void protectedPathWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
        System.out.println("GET /api/ping 无 token -> 401 / code=40100");
    }

    @Test
    void protectedPathWithValidTokenIs200() throws Exception {
        String token = jwtService.issue(7L).token();
        mockMvc.perform(get("/api/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(7));
        System.out.println("GET /api/ping 带合法 token -> 200 / data=7（身份来自 token）");
    }

    @Test
    void loginPathIsWhitelisted() throws Exception {
        // 空 body 会得到 400（校验失败），说明没有被鉴权拦成 401
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        System.out.println("POST /api/auth/login 无 token -> 400（白名单生效，未被拦成 401）");
    }

    @Test
    void actuatorIsNotIntercepted() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        System.out.println("GET /actuator/health 无 token -> 200（不在 /api/** 下）");
    }
}
