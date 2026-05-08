package com.connecthub.message.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    // A valid 256-bit base64-encoded key for testing
    private final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", SECRET);
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }

    private String generateToken(String email, Long userId, String role, long expirationMillis) {
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(getSigningKey())
                .compact();
    }

    @Test
    @DisplayName("Extract Email - Success")
    void extractEmail() {
        String token = generateToken("test@example.com", 1L, "USER", 10000);
        String email = jwtService.extractEmail(token);
        assertThat(email).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Extract User ID - Success")
    void extractUserId() {
        String token = generateToken("test@example.com", 1L, "USER", 10000);
        Long userId = jwtService.extractUserId(token);
        assertThat(userId).isEqualTo(1L);
    }

    @Test
    @DisplayName("Extract Role - Success")
    void extractRole() {
        String token = generateToken("test@example.com", 1L, "USER", 10000);
        String role = jwtService.extractRole(token);
        assertThat(role).isEqualTo("USER");
    }

    @Test
    @DisplayName("Validate Token - Valid Token")
    void isTokenValid_validToken() {
        String token = generateToken("test@example.com", 1L, "USER", 10000);
        boolean isValid = jwtService.isTokenValid(token);
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Validate Token - Expired Token")
    void isTokenValid_expiredToken() {
        String token = generateToken("test@example.com", 1L, "USER", -10000);
        boolean isValid = jwtService.isTokenValid(token);
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Validate Token - Invalid Token")
    void isTokenValid_invalidToken() {
        String token = "invalid.token.here";
        boolean isValid = jwtService.isTokenValid(token);
        assertThat(isValid).isFalse();
    }
}
