package com.community.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与验签。
 *
 * <p>四个声明各自防一件事：
 * <ul>
 *   <li>sub：用户 id，身份载体</li>
 *   <li>iss：签发方，防止多服务共用密钥时令牌串用</li>
 *   <li>jti：令牌唯一 id，登出/踢人时可进黑名单</li>
 *   <li>exp：过期时间，限制令牌泄漏后的可用窗口</li>
 * </ul>
 *
 * <p>JWT 是"签名"不是"加密"：payload 任何人都能解出，不要放敏感信息。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final Duration ttl;

    public JwtService(@Value("${app.jwt.secret}") String base64Secret,
                      @Value("${app.jwt.issuer}") String issuer,
                      @Value("${app.jwt.ttl}") Duration ttl) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.issuer = issuer;
        this.ttl = ttl;
    }

    /** 签发结果：令牌 + 它的 jti 与到期时间（登出时要写黑名单） */
    public record Issued(String token, String jti, Instant expiresAt) {
    }

    public Issued issue(long userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(issuer)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        return new Issued(token, jti, expiresAt);
    }

    /**
     * 验签并解析。
     *
     * @throws JwtException 签名不对、格式不对、过期、issuer 不匹配
     */
    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
    }

    public long userIdOf(String token) {
        return Long.parseLong(parse(token).getPayload().getSubject());
    }
}
