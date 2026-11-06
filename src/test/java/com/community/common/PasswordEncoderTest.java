package com.community.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 BCrypt 的三个性质：加盐随机、可比对不可逆、成本可控。
 */
@SpringBootTest
class PasswordEncoderTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void samePasswordProducesDifferentHashes() {
        String raw = "P@ssw0rd-2026";

        String hash1 = passwordEncoder.encode(raw);
        String hash2 = passwordEncoder.encode(raw);

        System.out.println("hash1 = " + hash1);
        System.out.println("hash2 = " + hash2);
        System.out.println("两者相同? " + hash1.equals(hash2) + "（应为 false：每次盐不同）");
        System.out.println("hash 长度 = " + hash1.length());

        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(hash1).startsWith("$2a$10$");
        assertThat(hash1).hasSize(60);
        assertThat(passwordEncoder.matches(raw, hash1)).isTrue();
        assertThat(passwordEncoder.matches(raw, hash2)).isTrue();
    }

    @Test
    void wrongPasswordDoesNotMatch() {
        String hash = passwordEncoder.encode("correct-horse");

        System.out.println("错误密码比对结果 = " + passwordEncoder.matches("correct-hors", hash));
        System.out.println("正确密码比对结果 = " + passwordEncoder.matches("correct-horse", hash));

        assertThat(passwordEncoder.matches("correct-hors", hash)).isFalse();
        assertThat(passwordEncoder.matches("Correct-Horse", hash)).isFalse();
        assertThat(passwordEncoder.matches("correct-horse", hash)).isTrue();
    }

    @Test
    void hashIsNotReversibleAndContainsSalt() {
        String hash = passwordEncoder.encode("secret");
        // $2a$10$ <-- 22 位盐 --><-- 31 位摘要 -->，盐就存在串里，所以能自校验
        String[] parts = hash.split("\\$");
        System.out.println("算法 = " + parts[1] + ", 成本 = " + parts[2] + ", 盐长度 = " + parts[3].length());

        assertThat(parts[1]).isEqualTo("2a");
        assertThat(parts[2]).isEqualTo("10");
        assertThat(parts[3].length()).isEqualTo(53);
    }

    @Test
    void measureCostPerHash() {
        String raw = "benchmark-password";
        int rounds = 5;
        long start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            passwordEncoder.encode(raw);
        }
        long avgMs = (System.nanoTime() - start) / 1_000_000 / rounds;
        System.out.println("平均每次 encode 耗时 ≈ " + avgMs + " ms（strength=10）");

        assertThat(avgMs).isLessThan(1000);
    }
}
