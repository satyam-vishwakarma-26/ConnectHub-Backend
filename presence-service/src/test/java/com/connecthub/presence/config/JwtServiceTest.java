package com.connecthub.presence.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the package-private {@code JwtService} in {@link SecurityConfig}.
 */
class JwtServiceTest {

    private JwtService jwtService;
    private SecretKey  signingKey;

    private static final String RAW_KEY = "12345678901234567890123456789012"; // 32 bytes

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new JwtService();

        byte[] keyBytes  = RAW_KEY.getBytes();
        String base64Key = Base64.getEncoder().encodeToString(keyBytes);

        Field secretField = JwtService.class.getDeclaredField("secret");
        secretField.setAccessible(true);
        secretField.set(jwtService, base64Key);

        signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String buildToken(String subject, Long userId, String role, Instant expiry) {
        return Jwts.builder()
                .subject(subject)
                .claim("userId", userId)
                .claim("role", role)
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    private String validToken() {
        return buildToken("user@connecthub.com", 7L, "PLATFORM_ADMIN",
                Instant.now().plusSeconds(3600));
    }

    // ── isValid ───────────────────────────────────────────────────────────────

    @Test
    void isValid_validToken_returnsTrue() {
        assertTrue(jwtService.isValid(validToken()));
    }

    @Test
    void isValid_expiredToken_returnsFalse() {
        String expired = buildToken("u@t.com", 1L, "USER", Instant.now().minusSeconds(60));
        assertFalse(jwtService.isValid(expired));
    }

    @Test
    void isValid_malformedToken_returnsFalse() {
        assertFalse(jwtService.isValid("not.a.jwt"));
    }

    @Test
    void isValid_emptyString_returnsFalse() {
        assertFalse(jwtService.isValid(""));
    }

    @Test
    void isValid_wrongSigningKey_returnsFalse() {
        byte[]    otherBytes = "99999999999999999999999999999999".getBytes();
        SecretKey otherKey   = Keys.hmacShaKeyFor(otherBytes);
        String foreign = Jwts.builder()
                .subject("x@y.com")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(otherKey)
                .compact();
        assertFalse(jwtService.isValid(foreign));
    }

    // ── claim extraction ──────────────────────────────────────────────────────

    @Test
    void userId_extractsCorrectly() {
        assertEquals(7L, jwtService.userId(validToken()));
    }

    @Test
    void email_extractsCorrectly() {
        assertEquals("user@connecthub.com", jwtService.email(validToken()));
    }

    @Test
    void role_extractsCorrectly() {
        assertEquals("PLATFORM_ADMIN", jwtService.role(validToken()));
    }

    @Test
    void role_whenClaimAbsent_returnsNull() {
        String token = Jwts.builder()
                .subject("norole@test.com")
                .claim("userId", 1L)
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(signingKey)
                .compact();
        assertNull(jwtService.role(token));
    }
}
