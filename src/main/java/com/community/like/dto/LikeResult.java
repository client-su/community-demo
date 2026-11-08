package com.community.like.dto;

/**
 * 点赞结果：客户端用 liked 决定按钮状态，用 likeCount 直接渲染，避免再查一次详情。
 */
public record LikeResult(Long contentId, boolean liked, long likeCount) {
}
