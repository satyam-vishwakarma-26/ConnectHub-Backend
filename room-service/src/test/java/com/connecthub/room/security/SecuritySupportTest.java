package com.connecthub.room.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Security Support Tests")
class SecuritySupportTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedUser_exposesUserDetails() {
        AuthenticatedUser user = new AuthenticatedUser(10L, "me@example.com", "PLATFORM_ADMIN");

        assertThat(user.getUserId()).isEqualTo(10L);
        assertThat(user.getUsername()).isEqualTo("me@example.com");
        assertThat(user.getPassword()).isNull();
        assertThat(user.isAccountNonExpired()).isTrue();
        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.isCredentialsNonExpired()).isTrue();
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getAuthorities()).extracting(Object::toString)
                .containsExactly("ROLE_PLATFORM_ADMIN");
    }

    @Test
    void jwtService_extractsClaimsAndValidatesTokens() {
        SecretKey key = Keys.hmacShaKeyFor("01234567890123456789012345678901".getBytes());
        JwtService jwtService = jwtServiceWithKey(key);
        String token = token(key, "me@example.com", 10L, "USER",
                Date.from(Instant.now().plusSeconds(3600)));

        assertThat(jwtService.extractEmail(token)).isEqualTo("me@example.com");
        assertThat(jwtService.extractUserId(token)).isEqualTo(10L);
        assertThat(jwtService.extractRole(token)).isEqualTo("USER");
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.isTokenValid("not-a-jwt")).isFalse();
    }

    @Test
    void jwtService_rejectsExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor("01234567890123456789012345678901".getBytes());
        JwtService jwtService = jwtServiceWithKey(key);
        String token = token(key, "old@example.com", 11L, "USER",
                Date.from(Instant.now().minusSeconds(60)));

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void jwtAuthenticationFilter_skipsMissingBearerToken() throws ServletException, IOException {
        JwtService jwtService = mock(JwtService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void jwtAuthenticationFilter_setsAuthenticationForValidBearerToken() throws ServletException, IOException {
        JwtService jwtService = mock(JwtService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.isTokenValid("token-123")).thenReturn(true);
        when(jwtService.extractUserId("token-123")).thenReturn(10L);
        when(jwtService.extractEmail("token-123")).thenReturn("me@example.com");
        when(jwtService.extractRole("token-123")).thenReturn("ADMIN");

        filter.doFilter(request, response, new MockFilterChain());

        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        assertThat(principal.getUserId()).isEqualTo(10L);
        assertThat(authentication.getCredentials()).isEqualTo("token-123");
    }

    @Test
    void jwtAuthenticationFilter_defaultsNullRoleAndSwallowsProcessingFailure()
            throws ServletException, IOException {
        JwtService jwtService = mock(JwtService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-123");

        when(jwtService.isTokenValid("token-123")).thenReturn(true);
        when(jwtService.extractUserId("token-123")).thenReturn(10L);
        when(jwtService.extractEmail("token-123")).thenReturn("me@example.com");
        when(jwtService.extractRole("token-123")).thenReturn(null);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        AuthenticatedUser principal =
                (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");

        SecurityContextHolder.clearContext();
        when(jwtService.isTokenValid("token-123")).thenThrow(new IllegalStateException("boom"));
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private JwtService jwtServiceWithKey(SecretKey key) {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", Encoders.BASE64.encode(key.getEncoded()));
        return jwtService;
    }

    private String token(SecretKey key, String email, Long userId, String role, Date expiration) {
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", role)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }
}
