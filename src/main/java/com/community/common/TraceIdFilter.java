package com.community.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 每个请求：确定 traceId → 写入 MDC → 回写响应头 → 请求结束后清理。
 *
 * <p>顺序设为最高优先级：后面的 filter / 拦截器 / 异常处理器都能拿到 traceId。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(TraceId.HEADER);
        String traceId = TraceId.isValid(incoming) ? incoming : TraceId.generate();

        TraceId.put(traceId);
        // 必须在 doFilter 之前设置：响应一旦提交，header 就改不了了
        response.setHeader(TraceId.HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Tomcat 线程会被复用：不清理就会串到下一个请求（traceId 串号、用户串号）
            TraceId.clear();
            UserContext.clear();
        }
    }
}
