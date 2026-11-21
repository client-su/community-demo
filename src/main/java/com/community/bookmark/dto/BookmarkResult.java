package com.community.bookmark.dto;

/**
 * 收藏结果：客户端用 bookmarked 决定按钮状态。
 * 不返回 likeCount——收藏不改变公开计数，前端需要的数字从详情/列表里已有。
 */
public record BookmarkResult(Long contentId, boolean bookmarked) {
}
