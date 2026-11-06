package com.community.user;

import com.community.common.BizException;
import com.community.common.ErrorCode;
import com.community.common.UserContext;
import com.community.user.dto.ProfileVO;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 当前登录用户资料。
     *
     * <p>身份来自 {@link UserContext}（由鉴权拦截器写入），不接受任何外部传入的 userId。
     * 每次查库而不是用 token 里的字段：昵称可能已被修改，token 只有 id 不会过期失真。
     */
    public ProfileVO currentProfile() {
        long userId = UserContext.require();

        User user = userMapper.findById(userId)
                // token 合法但用户已被物理删除：视为登录态失效
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "账号不存在，请重新登录"));

        if (!user.isActive()) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }

        return new ProfileVO(user.getId(), user.getUsername(), user.getNickname(), user.getCreatedAt());
    }
}
