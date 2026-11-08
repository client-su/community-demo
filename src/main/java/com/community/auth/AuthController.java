package com.community.auth;

import com.community.auth.dto.LoginRequest;
import com.community.auth.dto.LoginResponse;
import com.community.auth.dto.RegisterRequest;
import com.community.auth.dto.UserVO;
import com.community.common.RateLimit;
import com.community.common.Result;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 创建资源用 201，便于客户端/监控区分"查询成功"与"创建成功" */
    @PostMapping("/register")
    public ResponseEntity<Result<UserVO>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(authService.register(request)));
    }

    /** 登录：按 IP 限流，防密码爆破（未认证时还没有 userId） */
    @RateLimit(action = "login", limit = 5, windowSeconds = 60, by = RateLimit.Subject.IP)
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /**
     * 登出：把当前 token 的 jti 加入黑名单。
     * 该路径不在白名单里，所以必须先登录（否则任何人都能替别人注销）。
     */
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestAttribute(JwtAuthInterceptor.PAYLOAD_ATTRIBUTE) JwtPayload payload) {
        authService.logout(payload);
        return Result.ok();
    }
}
