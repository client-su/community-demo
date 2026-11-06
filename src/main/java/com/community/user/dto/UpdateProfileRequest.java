package com.community.user.dto;

import jakarta.validation.constraints.Size;

/**
 * 部分更新请求：字段为 null 表示"不修改"。
 *
 * <p>所以这里不能用 @NotBlank —— 那会把"不修改"也判成非法。
 * 真正的"至少改一项"和"不能是空白"在 Service 里判断。
 */
public record UpdateProfileRequest(

        @Size(max = 32, message = "长度不能超过 32")
        String nickname
) {
    public boolean isEmpty() {
        return nickname == null;
    }
}
