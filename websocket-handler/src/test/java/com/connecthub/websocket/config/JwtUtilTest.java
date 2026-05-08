package com.connecthub.websocket.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil    jwtUtil;
    private SecretKey  signingKey;

    private static final String RAW_KEY = "12345678901234567890123456789012"; // 32 bytes

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        byte[] keyBytes  = RAW_KEY.getBytes();
        String base64Key = Base64.getEncoder().encodeToString(keyBytes);
        ReflectionTestUtils.setField(jwtUtil, "secret", base64Key);
        signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String buildToken(String subject, Long userId, String role, Instant expiry) {
        return Jwts.builder()
                .subject(subject)
                .claim("userId", userId)
                .claim("role",   role)
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    private String validToken() {
        return buildToken("user@test.com", 42L, "USER", Instant.now().plusSeconds(3600));
    }

    // ── isValid ───────────────────────────────────────────────────────────────

    @Test
    void isValid_validToken_returnsTrue() {
        assertTrue(jwtUtil.isValid(validToken()));
    }

    @Test
    void isValid_expiredToken_returnsFalse() {
        String expired = buildToken("u@t.com", 1L, "USER", Instant.now().minusSeconds(60));
        assertFalse(jwtUtil.isValid(expired));
    }

    @Test
    void isValid_malformedToken_returnsFalse() {
        assertFalse(jwtUtil.isValid("not.a.valid.jwt.token"));
    }

    @Test
    void isValid_emptyString_returnsFalse() {
        assertFalse(jwtUtil.isValid(""));
    }

    @Test
    void isValid_wrongSigningKey_returnsFalse() {
        byte[]    otherBytes = "99999999999999999999999999999999".getBytes();
        SecretKey otherKey   = Keys.hmacShaKeyFor(otherBytes);
        String foreign = Jwts.builder()
                .subject("evil@test.com")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(otherKey)
                .compact();
        assertFalse(jwtUtil.isValid(foreign));
    }

    @Test
    void isValid_garbageString_returnsFalse() {
        assertFalse(jwtUtil.isValid("totally-random-garbage"));
    }

    // ── extractUserId ─────────────────────────────────────────────────────────

    @Test
    void extractUserId_returnsCorrectValue() {
        assertEquals(42L, jwtUtil.extractUserId(validToken()));
    }

    // ── extractEmail ──────────────────────────────────────────────────────────

    @Test
    void extractEmail_returnsSubject() {
        assertEquals("user@test.com", jwtUtil.extractEmail(validToken()));
    }

    // ── extractRole ───────────────────────────────────────────────────────────

    @Test
    void extractRole_returnsRoleClaim() {
        assertEquals("USER", jwtUtil.extractRole(validToken()));
    }

    @Test
    void extractRole_whenClaimAbsent_returnsNull() {
        String token = Jwts.builder()
                .subject("norole@test.com")
                .claim("userId", 1L)
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(signingKey)
                .compact();
        assertNull(jwtUtil.extractRole(token));
    }
}
