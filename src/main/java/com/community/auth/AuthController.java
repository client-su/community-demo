package com.community.auth;

import com.community.auth.dto.LoginRequest;
import com.community.auth.dto.LoginResponse;
import com.community.auth.dto.RegisterRequest;
import com.community.auth.dto.UserVO;
import com.community.common.Result;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }
}
