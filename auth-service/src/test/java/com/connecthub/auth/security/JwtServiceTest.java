package com.connecthub.auth.security;

import com.connecthub.auth.entity.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private CustomUserDetails userDetails;

    // A valid 256-bit Base64 secret for testing
    private static final String SECRET =
        "dGVzdFNlY3JldEtleUZvckp3dFRlc3RpbmcxMjM0NTY3ODk=";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 3600000L);      // 1 hour
        ReflectionTestUtils.setField(jwtService, "refreshExpirationMs", 86400000L); // 1 day

        User user = User.builder()
                .id(1L)
                .email("test@connecthub.com")
                .username("testuser")
                .passwordHash("hashedPassword")
                .role(User.UserRole.USER)
                .status(User.UserStatus.ONLINE)
                .isActive(true)
                .build();

        userDetails = new CustomUserDetails(user);
    }

    @Test
    void generateAccessToken_ShouldReturnNonNullToken() {
        String token = jwtService.generateAccessToken(userDetails);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void generateRefreshToken_ShouldReturnNonNullToken() {
        String token = jwtService.generateRefreshToken(userDetails);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void extractUsername_ShouldReturnCorrectEmail() {
        String token = jwtService.generateAccessToken(userDetails);
        String extractedEmail = jwtService.extractUsername(token);
        assertEquals("test@connecthub.com", extractedEmail);
    }

    @Test
    void extractUserId_ShouldReturnCorrectId() {
        String token = jwtService.generateAccessToken(userDetails);
        Long userId = jwtService.extractUserId(token);
        assertEquals(1L, userId);
    }

    @Test
    void isTokenValid_WithUserDetails_ShouldReturnTrue() {
        String token = jwtService.generateAccessToken(userDetails);
        assertTrue(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    void isTokenValid_WithoutUserDetails_ShouldReturnTrue() {
        String token = jwtService.generateAccessToken(userDetails);
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void isTokenValid_WithInvalidToken_ShouldReturnFalse() {
        assertFalse(jwtService.isTokenValid("this.is.not.valid"));
    }

    @Test
    void isTokenValid_WithWrongUser_ShouldReturnFalse() {
        String token = jwtService.generateAccessToken(userDetails);

        User otherUser = User.builder()
                .id(2L)
                .email("other@connecthub.com")
                .username("other")
                .passwordHash("hash")
                .role(User.UserRole.USER)
                .isActive(true)
                .build();
        CustomUserDetails otherDetails = new CustomUserDetails(otherUser);

        assertFalse(jwtService.isTokenValid(token, otherDetails));
    }

    @Test
    void getExpirationSeconds_ShouldReturn3600() {
        assertEquals(3600L, jwtService.getExpirationSeconds());
    }

    @Test
    void extractExpiration_ShouldReturnFutureDate() {
        String token = jwtService.generateAccessToken(userDetails);
        assertTrue(jwtService.extractExpiration(token).after(new java.util.Date()));
    }
}