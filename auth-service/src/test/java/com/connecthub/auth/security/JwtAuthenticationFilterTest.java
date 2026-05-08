package com.connecthub.auth.security;

import com.connecthub.auth.entity.User;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();

        User user = User.builder()
                .id(1L)
                .email("john@connecthub.com")
                .username("johndoe")
                .passwordHash("hashed")
                .role(User.UserRole.USER)
                .status(User.UserStatus.ONLINE)
                .isActive(true)
                .build();

        userDetails = new CustomUserDetails(user);
    }

    @Test
    void doFilter_WithNoAuthHeader_PassesThrough() throws ServletException, IOException {
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_WithNonBearerHeader_PassesThrough() throws ServletException, IOException {
        request.addHeader("Authorization", "Basic sometoken");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_WithValidToken_SetsAuthentication() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer valid.jwt.token");

        when(jwtService.extractUsername("valid.jwt.token"))
                .thenReturn("john@connecthub.com");
        when(userDetailsService.loadUserByUsername("john@connecthub.com"))
                .thenReturn(userDetails);
        when(jwtService.isTokenValid("valid.jwt.token", userDetails))
                .thenReturn(true);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("john@connecthub.com",
                SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void doFilter_WithInvalidToken_DoesNotSetAuthentication()
            throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer invalid.jwt.token");

        when(jwtService.extractUsername("invalid.jwt.token"))
                .thenReturn("john@connecthub.com");
        when(userDetailsService.loadUserByUsername("john@connecthub.com"))
                .thenReturn(userDetails);
        when(jwtService.isTokenValid("invalid.jwt.token", userDetails))
                .thenReturn(false);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_WhenJwtServiceThrows_ContinuesFilterChain()
            throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer bad.token");

        when(jwtService.extractUsername("bad.token"))
                .thenThrow(new RuntimeException("Invalid token"));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Filter chain must still continue even on exception
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_WithNullEmail_DoesNotSetAuthentication()
            throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer some.jwt.token");

        when(jwtService.extractUsername("some.jwt.token")).thenReturn(null);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}