package com.community.user.dto;

import java.time.LocalDateTime;

/**
 * 个人资料视图：白名单字段。
 *
 * <p>相比 User 少了两样东西：passwordHash（绝不能出），updatedAt（对客户端无用）。
 *
 * @param role 用户角色（0 普通 / 1 管理员）。只在「自己的资料」里返回真实值；
 *             公开资料恒为 null——不把别人的管理身份暴露给任意访客。
 */
public record ProfileVO(Long id, String username, String nickname, Integer role, LocalDateTime createdAt) {
}
