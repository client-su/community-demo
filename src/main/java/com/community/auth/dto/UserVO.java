package com.community.auth.dto;

/**
 * 用户对外视图：只暴露可以公开的字段。
 *
 * <p>为什么不直接返回 User：User 里有 passwordHash。
 * 用独立 VO 是"白名单"思路——新加字段不会意外泄漏。
 */
public record UserVO(Long id, String username, String nickname) {
}
