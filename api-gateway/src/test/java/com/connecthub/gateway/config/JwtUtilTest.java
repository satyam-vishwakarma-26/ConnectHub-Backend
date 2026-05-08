package com.connecthub.gateway.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtUtil Tests")
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private String testSecret;
    private javax.crypto.SecretKey signingKey;

    @BeforeEach
    void setUp() throws Exception {
        jwtUtil = new JwtUtil();
        String secretString = "connecthub-super-secret-key-for-jwt-signing-must-be-at-least-256-bits";
        testSecret = Base64.getEncoder().encodeToString(secretString.getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(jwtUtil, "secret", testSecret);
        signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(testSecret));
    }

    private String createToken(Long userId, String email, String role, Date expiration) {
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", role)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    private String createExpiredToken() {
        Date expired = new Date(System.currentTimeMillis() - 3600000);
        return createToken(1L, "test@test.com", "USER", expired);
    }

    private String createValidToken() {
        Date future = new Date(System.currentTimeMillis() + 3600000);
        return createToken(100L, "user@example.com", "ADMIN", future);
    }

    private String createMalformedToken() {
        return "not.a.valid.jwt.token.at.all";
    }

    private String createTokenWithNullClaims() {
        Date future = new Date(System.currentTimeMillis() + 3600000);
        return Jwts.builder()
                .subject("nullclaims@test.com")
                .expiration(future)
                .signWith(signingKey)
                .compact();
    }

    @Test
    @DisplayName("isValid with valid token returns true")
    void isValid_validToken_returnsTrue() {
        String token = createValidToken();
        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("isValid with expired token returns false")
    void isValid_expiredToken_returnsFalse() {
        String token = createExpiredToken();
        assertThat(jwtUtil.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("isValid with malformed token returns false")
    void isValid_malformedToken_returnsFalse() {
        String token = createMalformedToken();
        assertThat(jwtUtil.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("isValid with null token returns false")
    void isValid_nullToken_returnsFalse() {
        assertThat(jwtUtil.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("isValid with empty token returns false")
    void isValid_emptyToken_returnsFalse() {
        assertThat(jwtUtil.isValid("")).isFalse();
    }

    @Test
    @DisplayName("extractEmail with valid token returns correct email")
    void extractEmail_validToken_returnsEmail() {
        String token = createValidToken();
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("extractEmail with token having no email claim returns subject")
    void extractEmail_tokenWithNoEmailClaim_returnsSubject() {
        String token = createTokenWithNullClaims();
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("nullclaims@test.com");
    }

    @Test
    @DisplayName("extractEmail with malformed token throws exception")
    void extractEmail_malformedToken_throwsException() {
        String token = createMalformedToken();
        try {
            jwtUtil.extractEmail(token);
            throw new AssertionError("Expected exception was not thrown");
        } catch (Exception e) {
            assertThat(e).isNotNull();
        }
    }

    @Test
    @DisplayName("extractUserId with valid token returns correct userId")
    void extractUserId_validToken_returnsUserId() {
        String token = createValidToken();
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(100L);
    }

    @Test
    @DisplayName("extractUserId with token having no userId claim returns null")
    void extractUserId_tokenWithNoUserIdClaim_returnsNull() {
        String token = createTokenWithNullClaims();
        assertThat(jwtUtil.extractUserId(token)).isNull();
    }

    @Test
    @DisplayName("extractUserId with malformed token throws exception")
    void extractUserId_malformedToken_throwsException() {
        String token = createMalformedToken();
        try {
            jwtUtil.extractUserId(token);
            throw new AssertionError("Expected exception was not thrown");
        } catch (Exception e) {
            assertThat(e).isNotNull();
        }
    }

    @Test
    @DisplayName("extractRole with valid token returns correct role")
    void extractRole_validToken_returnsRole() {
        String token = createValidToken();
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("extractRole with token having no role claim returns null")
    void extractRole_tokenWithNoRoleClaim_returnsNull() {
        String token = createTokenWithNullClaims();
        assertThat(jwtUtil.extractRole(token)).isNull();
    }

    @Test
    @DisplayName("extractRole with malformed token throws exception")
    void extractRole_malformedToken_throwsException() {
        String token = createMalformedToken();
        try {
            jwtUtil.extractRole(token);
            throw new AssertionError("Expected exception was not thrown");
        } catch (Exception e) {
            assertThat(e).isNotNull();
        }
    }

    @Test
    @DisplayName("isExpired with expired token returns true")
    void isExpired_expiredToken_returnsTrue() {
        String token = createExpiredToken();
        assertThat(jwtUtil.isExpired(token)).isTrue();
    }

    @Test
    @DisplayName("isExpired with valid token returns false")
    void isExpired_validToken_returnsFalse() {
        String token = createValidToken();
        assertThat(jwtUtil.isExpired(token)).isFalse();
    }

    @Test
    @DisplayName("isExpired with malformed token returns true")
    void isExpired_malformedToken_returnsTrue() {
        String token = createMalformedToken();
        assertThat(jwtUtil.isExpired(token)).isTrue();
    }

    @Test
    @DisplayName("isExpired with null token returns true")
    void isExpired_nullToken_returnsTrue() {
        assertThat(jwtUtil.isExpired(null)).isTrue();
    }
}