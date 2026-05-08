package com.connecthub.payment.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    @InjectMocks
    private JwtUtil jwtUtil;

    private final String secret = "1234567890123456789012345678901234567890123456789012345678901234";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", secret);
    }

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String generateToken(Long userId, String username, String role, long expirationMs) {
        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void validateToken_Valid() {
        String token = generateToken(1L, "testuser", "USER", 1000 * 60 * 60);
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void validateToken_Invalid() {
        assertFalse(jwtUtil.validateToken("invalid_token"));
    }

    @Test
    void validateToken_Expired() {
        String token = generateToken(1L, "testuser", "USER", -1000); // Expired 1 second ago
        assertFalse(jwtUtil.validateToken(token));
    }

    @Test
    void extractClaims() {
        String token = generateToken(2L, "adminuser", "ADMIN", 1000 * 60 * 60);
        
        assertEquals(2L, jwtUtil.extractUserId(token));
        assertEquals("adminuser", jwtUtil.extractUsername(token));
        assertEquals("ADMIN", jwtUtil.extractRole(token));
    }
}
