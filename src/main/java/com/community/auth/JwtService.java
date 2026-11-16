package com.community.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
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

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final String issuer;
    private final Duration ttl;

    public JwtService(@Value("${app.jwt.secret:}") String base64Secret,
                      @Value("${app.jwt.issuer}") String issuer,
                      @Value("${app.jwt.ttl}") Duration ttl) {
        this.key = keyOf(base64Secret);
        this.issuer = issuer;
        this.ttl = ttl;
    }

    /**
     * 密钥来源：环境变量 JWT_SECRET 优先，没配就现场生成一次性随机密钥。
     *
     * <p>不往仓库里放任何可用的默认密钥：一旦默认值公开，任何人都能自己签出合法 token。
     * 随机密钥重启即失效，本地开发够用；公网部署必须显式配置 JWT_SECRET。
     */
    private static SecretKey keyOf(String base64Secret) {
        if (base64Secret == null || base64Secret.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            log.warn("未配置 JWT_SECRET，本次启动使用临时随机密钥：重启后已签发的 token 全部失效");
            return Keys.hmacShaKeyFor(random);
        }
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
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

    /** 验签并返回结构化载荷：鉴权拦截器用它拿到 userId 与 jti */
    public JwtPayload parsePayload(String token) {
        return JwtPayload.from(parse(token).getPayload());
    }
}
