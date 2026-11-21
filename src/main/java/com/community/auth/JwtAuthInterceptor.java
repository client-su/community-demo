package com.community.auth;

import com.community.common.BizException;
import com.community.common.ErrorCode;
import com.community.common.OptionalAuth;
import com.community.common.SecurityRedisService;
import com.community.common.UserContext;
import com.community.user.User;
import com.community.user.UserMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器：取票 → 验票 → 查黑名单 → 查封禁 → 记身份。
 *
 * <p>封禁校验按主键点查一次 user.status（DB 是唯一真相）。
 * 这是对早期「不查库判断封禁」取舍的有意反转：当时没有封禁管理功能，
 * 如今正确性优先，代价是每个已登录请求多一次点查，演示规模可接受。
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /** Controller 需要 jti/exp 时从这里取（如登出） */
    public static final String PAYLOAD_ATTRIBUTE = "jwtPayload";

    private final JwtService jwtService;
    private final SecurityRedisService securityRedisService;
    private final UserMapper userMapper;

    public JwtAuthInterceptor(JwtService jwtService, SecurityRedisService securityRedisService,
                              UserMapper userMapper) {
        this.jwtService = jwtService;
        this.securityRedisService = securityRedisService;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 浏览器跨域预检不带 Authorization，拦了前端会全挂
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            // 标了 @OptionalAuth 的只读接口允许匿名：不填 UserContext，下游按"未登录"处理。
            // 只有"没带票"能放行；"带了坏票"在下面照样 401，静默降级成匿名会让前端
            // 出现"已登录却没有身份"的状态，反而没法判断该不该重新登录。
            if (isOptionalAuth(handler)) {
                return true;
            }
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

        // 验签通过后再查黑名单：先挡掉伪造请求，避免拿垃圾 token 打 Redis
        if (securityRedisService.isBlacklisted(payload.jti())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }

        // 封禁校验（DB 唯一真相）：账号被删 → 登录态失效；被禁用 → 403 统一提示
        Integer status = userMapper.findStatusById(payload.userId()).orElse(null);
        if (status == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号不存在，请重新登录");
        }
        if (status != User.STATUS_ACTIVE) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }

        UserContext.set(payload.userId());
        // 把整张票交给后面：登出需要 jti 与 exp，光有 userId 表达不了"注销哪张票"
        request.setAttribute(PAYLOAD_ATTRIBUTE, payload);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 自己 set 的值自己清，不依赖 Filter 的清理顺序
        UserContext.clear();
    }

    /**
     * 注解写在接口方法上，白名单跟着接口走。
     *
     * <p>不用 WebConfig 的 excludePathPatterns：那条路是"按路径排除"，只认路径不认方法，
     * 而 /api/contents 同一路径上既有匿名可读的 GET，也有必须登录的 POST，按路径排除会误伤写接口。
     */
    private boolean isOptionalAuth(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return false;
        }
        return handlerMethod.hasMethodAnnotation(OptionalAuth.class);
    }
}
