package com.community.content.dto;

import java.time.LocalDateTime;

/**
 * 内容视图：白名单字段，不包含 idemKey（内部幂等机制，客户端无需知道）。
 *
 * @param nickname 作者昵称，由查询 JOIN user 表填充；可能为 null（用户被删时），前端需兜底。
 */
public record ContentVO(Long id, Long userId, String nickname, String body, Integer likeCount, LocalDateTime createdAt) {
}
