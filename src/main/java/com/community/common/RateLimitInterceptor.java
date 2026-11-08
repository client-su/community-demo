package com.community.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 执行 {@link RateLimit} 注解。
 *
 * <p>必须排在鉴权拦截器之后注册：by=USER 需要 UserContext 已经被填充。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitService rateLimitService;

    public RateLimitInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        String subject = resolveSubject(rateLimit, request);
        RateLimitService.Decision decision = rateLimitService.tryAcquire(
                rateLimit.action(), subject, rateLimit.limit(),
                Duration.ofSeconds(rateLimit.windowSeconds()));

        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining",
                String.valueOf(Math.max(0, decision.limit() - decision.count())));

        if (decision.allowed()) {
            return true;
        }

        // 告诉客户端等多久再来，否则它立刻重试会形成雪崩
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        log.warn("限流触发 action={} subject={} count={} limit={}",
                rateLimit.action(), subject, decision.count(), decision.limit());
        throw new BizException(ErrorCode.TOO_MANY_REQUESTS,
                "请求过于频繁，请 " + decision.retryAfterSeconds() + " 秒后再试");
    }

    private String resolveSubject(RateLimit rateLimit, HttpServletRequest request) {
        if (rateLimit.by() == RateLimit.Subject.USER) {
            Long userId = UserContext.get();
            // 未登录时退回 IP，避免直接 401；真正的 401 由鉴权拦截器负责
            return userId != null ? "u" + userId : "ip" + clientIp(request);
        }
        return "ip" + clientIp(request);
    }

    /**
     * 默认用 remoteAddr。
     *
     * <p>不直接读 X-Forwarded-For：该头可被客户端伪造，用了等于限流可被绕过。
     * 只有在明确部署于可信代理之后（由代理覆盖该头）时才应启用。
     */
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
