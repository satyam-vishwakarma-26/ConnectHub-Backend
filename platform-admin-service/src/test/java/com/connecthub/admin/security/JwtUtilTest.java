package com.connecthub.admin.security;

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

    /** 32-byte key required for HMAC-SHA256 */
    private static final String RAW_KEY = "12345678901234567890123456789012";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        byte[]  keyBytes  = RAW_KEY.getBytes();
        String  base64Key = Base64.getEncoder().encodeToString(keyBytes);
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", base64Key);
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
        return buildToken("admin@connecthub.com", 99L, "PLATFORM_ADMIN",
                Instant.now().plusSeconds(3600));
    }

    // ── extractEmail ──────────────────────────────────────────────────────────

    @Test
    void extractEmail_returnsSubjectFromToken() {
        String token = validToken();
        assertEquals("admin@connecthub.com", jwtUtil.extractEmail(token));
    }

    // ── extractUserId ─────────────────────────────────────────────────────────

    @Test
    void extractUserId_returnsUserIdClaim() {
        String token = validToken();
        assertEquals(99L, jwtUtil.extractUserId(token));
    }

    // ── extractRole ───────────────────────────────────────────────────────────

    @Test
    void extractRole_returnsRoleClaim() {
        String token = validToken();
        assertEquals("PLATFORM_ADMIN", jwtUtil.extractRole(token));
    }

    @Test
    void extractRole_returnsNullWhenRoleClaimAbsent() {
        String token = Jwts.builder()
                .subject("noRole@connecthub.com")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(signingKey)
                .compact();

        assertNull(jwtUtil.extractRole(token));
    }

    // ── isTokenValid ──────────────────────────────────────────────────────────

    @Test
    void isTokenValid_validToken_returnsTrue() {
        assertTrue(jwtUtil.isTokenValid(validToken()));
    }

    @Test
    void isTokenValid_expiredToken_returnsFalse() {
        String expired = buildToken("admin@connecthub.com", 99L, "PLATFORM_ADMIN",
                Instant.now().minusSeconds(120));
        assertFalse(jwtUtil.isTokenValid(expired));
    }

    @Test
    void isTokenValid_malformedToken_returnsFalse() {
        assertFalse(jwtUtil.isTokenValid("this.is.not.a.jwt"));
    }

    @Test
    void isTokenValid_emptyString_returnsFalse() {
        assertFalse(jwtUtil.isTokenValid(""));
    }

    @Test
    void isTokenValid_randomGarbage_returnsFalse() {
        assertFalse(jwtUtil.isTokenValid("totally-random-garbage-value"));
    }

    @Test
    void isTokenValid_signedWithDifferentKey_returnsFalse() {
        // Build a token signed with a DIFFERENT key
        byte[]    otherBytes = "99999999999999999999999999999999".getBytes();
        SecretKey otherKey   = Keys.hmacShaKeyFor(otherBytes);

        String foreignToken = Jwts.builder()
                .subject("attacker@evil.com")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(otherKey)
                .compact();

        assertFalse(jwtUtil.isTokenValid(foreignToken));
    }

    // ── extractClaim ─────────────────────────────────────────────────────────

    @Test
    void extractClaim_customResolver_works() {
        String token = validToken();
        // Use extractClaim directly with a custom resolver (covers the public method)
        String subject = jwtUtil.extractClaim(token, claims -> claims.getSubject() + "_custom");
        assertEquals("admin@connecthub.com_custom", subject);
    }
}
