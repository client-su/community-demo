package com.community.comment.dto;

import java.time.LocalDateTime;

/**
 * 评论对外视图：白名单字段，不包含 deleted（已删的评论对外就是不存在）。
 */
public record CommentVO(Long id, Long contentId, Long userId, String nickname, String body, LocalDateTime createdAt) {
}
