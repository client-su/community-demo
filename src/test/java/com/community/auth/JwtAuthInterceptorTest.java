package com.community.auth;

import com.community.common.BizException;
import com.community.common.ErrorCode;
import com.community.common.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 拦截器直接单元测试：不启动 MVC 也能验证三条边界。
 */
@SpringBootTest
class JwtAuthInterceptorTest {

    @Autowired
    private JwtAuthInterceptor interceptor;

    @Autowired
    private JwtService jwtService;

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
        String token = jwtService.issue(99L).token();

        boolean pass = interceptor.preHandle(request("Bearer " + token), response, new Object());

        assertThat(pass).isTrue();
        assertThat(UserContext.get()).isEqualTo(99L);
        System.out.println("合法 token -> 放行，UserContext.userId = " + UserContext.get());

        interceptor.afterCompletion(request("Bearer " + token), response, new Object(), null);
        assertThat(UserContext.get()).isNull();
        System.out.println("afterCompletion 后 UserContext = " + UserContext.get());
    }

    @Test
    void optionsPreflightPassesWithoutToken() {
        MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/api/me");
        assertThat(interceptor.preHandle(preflight, response, new Object())).isTrue();
        System.out.println("OPTIONS 预检不带 token 也放行");
    }
}
