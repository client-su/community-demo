package com.community.auth;

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

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
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
}
