package com.community.auth;

import com.community.common.BizException;
import com.community.common.ErrorCode;
import com.community.common.UserContext;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器：取票 → 验票 → 记身份。
 *
 * <p>不做的事：不查库判断用户是否被封禁（那是每个请求都访问数据库的成本，
 * 需要时用 Redis 缓存状态或把状态写进 token 并配合短 TTL）。
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 浏览器跨域预检不带 Authorization，拦了前端会全挂
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少 Bearer 令牌");
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        JwtPayload payload;
        try {
            payload = jwtService.parsePayload(token);
        } catch (ExpiredJwtException ex) {
            // 过期与无效分开提示：前者引导重新登录，后者说明令牌被改过
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无效的登录凭证");
        }

        UserContext.set(payload.userId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 自己 set 的值自己清，不依赖 Filter 的清理顺序
        UserContext.clear();
    }
}
