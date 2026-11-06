package com.community.auth;

import io.jsonwebtoken.Claims;

import java.time.Instant;

/**
 * 验签后的令牌载荷。
 *
 * <p>用 record 而不是散着传三个参数：调用方一眼看清"令牌里能拿到什么"，
 * 也避免以后加 claim 时改一堆方法签名。
 */
public record JwtPayload(long userId, String jti, Instant issuedAt, Instant expiresAt) {

    public static JwtPayload from(Claims claims) {
        return new JwtPayload(
                Long.parseLong(claims.getSubject()),
                claims.getId(),
                claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant(),
                claims.getExpiration() == null ? null : claims.getExpiration().toInstant());
    }
}
