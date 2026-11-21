package com.community.auth;

import com.community.common.BizException;
import com.community.common.ErrorCode;
import com.community.common.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 拦截器直接单元测试：不启动 MVC 也能验证鉴权边界。
 *
 * <p>封禁校验会按 userId 点查 user 表，涉及身份的用例必须插入真实用户行；
 * @Transactional 让这些行在测试结束后自动回滚。
 */
@SpringBootTest
@Transactional
class JwtAuthInterceptorTest {

    @Autowired
    private JwtAuthInterceptor interceptor;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private MockHttpServletRequest request(String authHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        if (authHeader != null) {
            request.addHeader("Authorization", authHeader);
        }
        return request;
    }

    /** 插入一个真实用户，返回自增 id（status 默认 1 正常） */
    private long newUser() {
        String username = "itc_" + (System.currentTimeMillis() % 1_000_000);
        jdbcTemplate.update("INSERT INTO `user` (username, password_hash) VALUES (?, 'x')", username);
        return jdbcTemplate.queryForObject("SELECT id FROM `user` WHERE username = ?", Long.class, username);
    }

    @Test
    void noHeaderIs401() {
        assertThatThrownBy(() -> interceptor.preHandle(request(null), response, new Object()))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        System.out.println("无 Authorization -> 401 缺少 Bearer 令牌");
    }

    @Test
    void nonBearerSchemeIs401() {
        assertThatThrownBy(() -> interceptor.preHandle(request("Basic dXNlcjpwYXNz"), response, new Object()))
                .isInstanceOf(BizException.class);
        System.out.println("Basic 方案 -> 401");
    }

    @Test
    void garbageTokenIs401() {
        assertThatThrownBy(() -> interceptor.preHandle(request("Bearer not-a-jwt"), response, new Object()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无效");
        System.out.println("乱写的 token -> 401 无效的登录凭证");
    }

    @Test
    void validTokenPassesAndSetsContext() {
        long userId = newUser();
        String token = jwtService.issue(userId).token();

        boolean pass = interceptor.preHandle(request("Bearer " + token), response, new Object());

        assertThat(pass).isTrue();
        assertThat(UserContext.get()).isEqualTo(userId);
        System.out.println("合法 token -> 放行，UserContext.userId = " + UserContext.get());

        interceptor.afterCompletion(request("Bearer " + token), response, new Object(), null);
        assertThat(UserContext.get()).isNull();
        System.out.println("afterCompletion 后 UserContext = " + UserContext.get());
    }

    @Test
    void tokenOfDeletedUserIs401() {
        // 一个从未存在的 userId：验签通过但查无此人 -> 401（登录态失效）
        long ghostId = 999_999_000L;
        String token = jwtService.issue(ghostId).token();

        assertThatThrownBy(() -> interceptor.preHandle(request("Bearer " + token), response, new Object()))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        System.out.println("已删除用户的 token -> 401");
    }

    @Test
    void tokenOfBannedUserIs403() {
        long userId = newUser();
        jdbcTemplate.update("UPDATE `user` SET status = 0 WHERE id = ?", userId);
        String token = jwtService.issue(userId).token();

        assertThatThrownBy(() -> interceptor.preHandle(request("Bearer " + token), response, new Object()))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        System.out.println("被封禁用户的 token -> 403");
    }

    @Test
    void optionsPreflightPassesWithoutToken() {
        MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/api/me");
        assertThat(interceptor.preHandle(preflight, response, new Object())).isTrue();
        System.out.println("OPTIONS 预检不带 token 也放行");
    }
}
