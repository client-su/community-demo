package com.community.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求。
 *
 * <p>newPassword 复用注册时的密码规则（8-64 位）；
 * oldPassword 只要求非空——格式错误由「原密码不正确」统一回答，不暴露更多信息。
 */
public record ChangePasswordRequest(

        @NotBlank(message = "不能为空")
        String oldPassword,

        @NotBlank(message = "不能为空")
        @Size(min = 8, max = 64, message = "长度需 8-64 位")
        String newPassword
) {
}
