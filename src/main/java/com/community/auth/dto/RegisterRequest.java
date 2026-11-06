package com.community.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 *
 * <p>校验只做"格式与长度"，不做"是否已存在"——那需要查库，属于 Service 职责。
 */
public record RegisterRequest(

        @NotBlank(message = "不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$", message = "只能包含字母、数字、下划线，长度 4-32")
        String username,

        @NotBlank(message = "不能为空")
        @Size(min = 8, max = 64, message = "长度需 8-64 位")
        String password,

        @Size(max = 32, message = "长度不能超过 32")
        String nickname
) {
}
