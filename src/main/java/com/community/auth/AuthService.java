package com.community.auth;

import com.community.auth.dto.LoginRequest;
import com.community.auth.dto.LoginResponse;
import com.community.auth.dto.RegisterRequest;
import com.community.auth.dto.UserVO;
import com.community.common.BizException;
import com.community.common.ErrorCode;
import com.community.user.User;
import com.community.user.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * 用户不存在时也跑一次 BCrypt，让两条失败路径耗时接近。
     * 否则"不存在"瞬时返回、"密码错"要 50ms，光看响应时间就能枚举用户名。
     */
    private static final String DUMMY_HASH =
            "$2a$10$AbGNcTD8igqeCal7AVECYu4AkyxvdBAxSvtvYvr0.kZihvhp6bh4q";

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserVO register(RegisterRequest request) {
        User user = new User();
        user.setUsername(request.username().trim());
        // 只存哈希；BCrypt 会自带随机盐
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(StringUtils.hasText(request.nickname())
                ? request.nickname().trim()
                : request.username().trim());
        user.setStatus(User.STATUS_ACTIVE);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            // 数据库的唯一键才是唯一性的最终真相；预查只能加速，不能替代
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }

        return new UserVO(user.getId(), user.getUsername(), user.getNickname());
    }

    /**
     * 登录：两种失败（用户不存在 / 密码错）返回完全一致的结果。
     * 禁用账号在密码验证通过后才提示，因为此时用户已证明身份，不构成枚举风险。
     */
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.findByUsername(request.username().trim()).orElse(null);

        // 无论用户是否存在，都执行一次 BCrypt，拉平两条路径的耗时
        String hash = (user == null) ? DUMMY_HASH : user.getPasswordHash();
        boolean passwordOk = passwordEncoder.matches(request.password(), hash);

        if (user == null || !passwordOk) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }

        JwtService.Issued issued = jwtService.issue(user.getId());
        return new LoginResponse(issued.token(), "Bearer", issued.expiresAt());
    }
}
