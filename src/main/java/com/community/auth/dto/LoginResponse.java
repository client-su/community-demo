package com.community.auth.dto;

import java.time.Instant;

/**
 * 登录结果。
 *
 * <p>不返回用户详情：登录只负责发凭证，用户信息用独立接口查。
 * {@code expiresAt} 让前端能主动判断"快过期了"，而不是等 401 才发现。
 */
public record LoginResponse(String token, String tokenType, Instant expiresAt) {
}
