package com.community.user;

import com.community.auth.JwtPayload;
import com.community.common.BizException;
import com.community.common.ErrorCode;
import com.community.common.SecurityRedisService;
import com.community.common.UserContext;
import com.community.user.dto.ChangePasswordRequest;
import com.community.user.dto.ProfileVO;
import com.community.user.dto.UpdateProfileRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SecurityRedisService securityRedisService;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       SecurityRedisService securityRedisService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.securityRedisService = securityRedisService;
    }

    /**
     * 公开资料（匿名可看）：用户主页用。
     *
     * <p>禁用账号与「不存在」返回同一个 404，防资源枚举。
     */
    public ProfileVO publicProfile(long id) {
        User user = userMapper.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (!user.isActive()) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return new ProfileVO(user.getId(), user.getUsername(), user.getNickname(), user.getCreatedAt());
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

    /**
     * 部分更新：null 表示不修改。
     *
     * <p>一个字段都没传时返回 400 而不是“假装成功”——
     * 否则客户端把 nickname 拼成 nickName 时会拿到 200，问题被隐藏。
     */
    @Transactional
    public ProfileVO updateCurrentProfile(UpdateProfileRequest request) {
        long userId = UserContext.require();

        if (request.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "至少提供一个要更新的字段");
        }

        String nickname = request.nickname().trim();
        if (!StringUtils.hasText(nickname)) {
            // @Size(min=1) 拦不住空白字符，必须 trim 后再判
            throw new BizException(ErrorCode.BAD_REQUEST, "nickname 不能为空白");
        }

        User user = userMapper.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "账号不存在，请重新登录"));

        userMapper.updateNickname(userId, nickname);
        user.setNickname(nickname);

        return new ProfileVO(user.getId(), user.getUsername(), user.getNickname(), user.getCreatedAt());
    }

    /**
     * 修改密码：旧密码校验通过后更新哈希，并把当前 token 的 jti 拉黑，
     * 强制本次登录态失效（客户端收到 401 后引导重新登录）。
     *
     * <p>只作废当前这张票：其他设备的 token 不感知，也不需要知道
     * （项目没有“全部设备下线”的需求）。
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request, JwtPayload payload) {
        long userId = UserContext.require();
        User user = userMapper.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "账号不存在，请重新登录"));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.OLD_PASSWORD_MISMATCH);
        }

        userMapper.updatePassword(userId, passwordEncoder.encode(request.newPassword()));
        securityRedisService.blacklistJti(payload.jti(), payload.expiresAt());
    }
}
