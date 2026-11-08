package com.community.common;

import com.community.auth.JwtAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
/**
 * MVC 配置：集中声明"哪些路径需要登录"。
 *
 * <p>用 excludePathPatterns 而不是在拦截器里写 if：
 * 白名单只在一处可见，审计成本最低；拦截器保持"进来就必须有合法 token"的纯语义。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(JwtAuthInterceptor jwtAuthInterceptor, RateLimitInterceptor rateLimitInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/register",   // 注册不需要登录
                        "/api/auth/login",      // 登录当然不需要登录
                        "/error"                // 容器错误转发，拦了会把 401 变成 500
                );

        // 顺序很重要：先鉴权填 UserContext，再限流（by=USER 才能拿到 userId）
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/error");
    }
}
