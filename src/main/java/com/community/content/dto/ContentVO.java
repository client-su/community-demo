package com.community.content.dto;

import java.time.LocalDateTime;

/**
 * 内容视图：白名单字段，不包含 idemKey（内部幂等机制，客户端无需知道）。
 */
public record ContentVO(Long id, Long userId, String body, LocalDateTime createdAt) {
}
