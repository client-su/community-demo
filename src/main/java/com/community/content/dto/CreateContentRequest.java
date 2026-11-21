package com.community.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 发布请求。
 *
 * <p>没有 userId：作者身份来自 token，客户端无权指定。
 * 用 @NotBlank 而不是 @NotNull —— 后者放行 "   " 这类空白内容。
 */
public record CreateContentRequest(

        @NotBlank(message = "不能为空")
        @Size(max = 2000, message = "长度不能超过 2000")
        String body,

        /** 可选：绑定的预置标签 id 列表；null 或空表示不带标签 */
        List<Long> tagIds
) {
    /** 不带标签发布的便捷构造：保持既有调用方不破坏 */
    public CreateContentRequest(String body) {
        this(body, null);
    }
}
