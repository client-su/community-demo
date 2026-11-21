package com.community.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 封禁/解封请求：status 只能是 0（禁用）或 1（正常）。
 */
public record SetUserStatusRequest(

        @NotNull(message = "不能为空")
        @Min(value = 0, message = "只能是 0 或 1")
        @Max(value = 1, message = "只能是 0 或 1")
        Integer status
) {
}
