package com.connecthub.media.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtService — Unit Tests")
class JwtServiceTest {

    private JwtService jwtService;
    private SecretKey  signingKey;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // 32-byte key — minimum for HMAC-SHA256
        byte[] keyBytes = "12345678901234567890123456789012".getBytes();
        String base64Key = Base64.getEncoder().encodeToString(keyBytes);
        ReflectionTestUtils.setField(jwtService, "jwtSecret", base64Key);
        signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private String buildToken(String subject, Long userId, String role, long expiryOffsetSec) {
        return Jwts.builder()
                .subject(subject)
                .claim("userId", userId)
                .claim("role", role)
                .expiration(Date.from(Instant.now().plusSeconds(expiryOffsetSec)))
                .signWith(signingKey)
                .compact();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // extractEmail / extractUserId / extractRole
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Claim extraction")
    class ClaimExtraction {

        @Test
        @DisplayName("extractEmail — returns subject")
        void extractEmail() {
            String token = buildToken("admin@test.com", 1L, "PLATFORM_ADMIN", 3600);
            assertThat(jwtService.extractEmail(token)).isEqualTo("admin@test.com");
        }

        @Test
        @DisplayName("extractUserId — returns Long userId claim")
        void extractUserId() {
            String token = buildToken("user@test.com", 99L, "USER", 3600);
            assertThat(jwtService.extractUserId(token)).isEqualTo(99L);
        }

        @Test
        @DisplayName("extractRole — returns role claim")
        void extractRole() {
            String token = buildToken("user@test.com", 1L, "PLATFORM_ADMIN", 3600);
            assertThat(jwtService.extractRole(token)).isEqualTo("PLATFORM_ADMIN");
        }

        @Test
        @DisplayName("extractRole — null when claim absent")
        void extractRole_absent_returnsNull() {
            // No "role" claim
            String token = Jwts.builder()
                    .subject("noRole@test.com")
                    .claim("userId", 5L)
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(signingKey)
                    .compact();
            assertThat(jwtService.extractRole(token)).isNull();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // isTokenValid
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isTokenValid()")
    class IsTokenValid {

        @Test
        @DisplayName("Valid, non-expired token — returns true")
        void validToken_returnsTrue() {
            String token = buildToken("user@test.com", 1L, "USER", 3600);
            assertThat(jwtService.isTokenValid(token)).isTrue();
        }

        @Test
        @DisplayName("Expired token — returns false")
        void expiredToken_returnsFalse() {
            String token = buildToken("user@test.com", 1L, "USER", -120);
            assertThat(jwtService.isTokenValid(token)).isFalse();
        }

        @Test
        @DisplayName("Malformed / random string — returns false")
        void malformedToken_returnsFalse() {
            assertThat(jwtService.isTokenValid("this.is.not.a.jwt")).isFalse();
        }

        @Test
        @DisplayName("Empty string — returns false")
        void emptyString_returnsFalse() {
            assertThat(jwtService.isTokenValid("")).isFalse();
        }

        @Test
        @DisplayName("Token signed with different key — returns false")
        void wrongKey_returnsFalse() {
            SecretKey otherKey = Keys.hmacShaKeyFor(
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZ123456".getBytes());
            String token = Jwts.builder()
                    .subject("hacker@test.com")
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(otherKey)
                    .compact();
            assertThat(jwtService.isTokenValid(token)).isFalse();
        }
    }
}
