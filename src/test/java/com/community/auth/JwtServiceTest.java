package com.community.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    @Test
    void issueThenParse() {
        JwtService.Issued issued = jwtService.issue(42L);

        Jws<Claims> parsed = jwtService.parse(issued.token());
        Claims claims = parsed.getPayload();

        System.out.println("token = " + issued.token());
        System.out.println("header.alg = " + parsed.getHeader().getAlgorithm());
        System.out.println("sub = " + claims.getSubject()
                + ", iss = " + claims.getIssuer()
                + ", jti = " + claims.getId());
        System.out.println("iat = " + claims.getIssuedAt() + ", exp = " + claims.getExpiration());

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getIssuer()).isEqualTo("community");
        assertThat(claims.getId()).isEqualTo(issued.jti());
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        assertThat(jwtService.userIdOf(issued.token())).isEqualTo(42L);
        assertThat(issued.token().split("\\.")).hasSize(3);
    }

    @Test
    void tamperedPayloadIsRejected() {
        String token = jwtService.issue(42L).token();
        String[] parts = token.split("\\.");
        // 篡改 payload 的最后一个字符（同长度，仅改内容）
        char last = parts[1].charAt(parts[1].length() - 1);
        parts[1] = parts[1].substring(0, parts[1].length() - 1) + (last == 'A' ? 'B' : 'A');
        String tampered = String.join(".", parts);

        assertThatThrownBy(() -> jwtService.parse(tampered))
                .isInstanceOf(JwtException.class);
        System.out.println("篡改 payload 后验签失败: " + JwtException.class.getSimpleName());
    }

    @Test
    void expiredTokenIsRejected() {
        // 用同一个密钥构造一个 -1 秒过期的 service
        String secret = "HqqXRUygiLhMHLgFj2WxSIq18ZXQBkXHqKKf9MAmnn8=";
        JwtService shortLived = new JwtService(secret, "community", Duration.ofSeconds(-1));
        String token = shortLived.issue(42L).token();

        assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOf(ExpiredJwtException.class);
        System.out.println("过期 token 被拒绝: ExpiredJwtException");
    }

    @Test
    void wrongIssuerIsRejected() {
        String secret = "HqqXRUygiLhMHLgFj2WxSIq18ZXQBkXHqKKf9MAmnn8=";
        JwtService otherIssuer = new JwtService(secret, "another-service", Duration.ofHours(1));
        String token = otherIssuer.issue(42L).token();

        assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOfAny(IncorrectClaimException.class, JwtException.class);
        System.out.println("issuer 不匹配被拒绝（防令牌串用）");
    }

    @Test
    void weakSecretIsRejectedByLibrary() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes());
        assertThatThrownBy(() -> new JwtService(shortSecret, "community", Duration.ofHours(1)))
                .isInstanceOf(Exception.class);
        System.out.println("弱密钥被 jjwt 直接拒绝（HS256 要求 >= 256 bit）");
    }

    @Test
    void payloadIsReadableWithoutSecret() {
        String token = jwtService.issue(42L).token();
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        System.out.println("无需密钥即可读出 payload = " + payload);
        assertThat(payload).contains("\"sub\":\"42\"").contains("\"iss\":\"community\"");
    }
}
