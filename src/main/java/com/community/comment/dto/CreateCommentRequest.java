package com.community.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发评论请求。
 *
 * <p>没有 contentId 与 userId：目标内容来自路径，作者身份来自 token。
 */
public record CreateCommentRequest(

        @NotBlank(message = "不能为空")
        @Size(max = 1000, message = "长度不能超过 1000")
        String body
) {
}
