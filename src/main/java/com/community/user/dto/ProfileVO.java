package com.community.user.dto;

import java.time.LocalDateTime;

/**
 * 个人资料视图：白名单字段。
 *
 * <p>相比 User 少了两样东西：passwordHash（绝不能出），updatedAt（对客户端无用）。
 */
public record ProfileVO(Long id, String username, String nickname, LocalDateTime createdAt) {
}
