package com.connecthub.message.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Tests")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Filter Internal - Valid Token")
    void doFilterInternal_validToken() throws ServletException, IOException {
        String token = "valid.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(1L);
        when(jwtService.extractEmail(token)).thenReturn("test@example.com");
        when(jwtService.extractRole(token)).thenReturn("USER");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        assertThat(user.getUserId()).isEqualTo(1L);
        assertThat(user.getEmail()).isEqualTo("test@example.com");

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Filter Internal - Valid Token Null Role Falls Back To USER")
    void doFilterInternal_validToken_nullRole() throws ServletException, IOException {
        String token = "valid.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(1L);
        when(jwtService.extractEmail(token)).thenReturn("test@example.com");
        when(jwtService.extractRole(token)).thenReturn(null);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        assertThat(user.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_USER");

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Filter Internal - Invalid Token")
    void doFilterInternal_invalidToken() throws ServletException, IOException {
        String token = "invalid.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtService.isTokenValid(token)).thenReturn(false);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Filter Internal - No Token")
    void doFilterInternal_noToken() throws ServletException, IOException {
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Filter Internal - Exceptions Caught")
    void doFilterInternal_exceptionCaught() throws ServletException, IOException {
        String token = "valid.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtService.isTokenValid(token)).thenThrow(new RuntimeException("Test Exception"));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        verify(filterChain).doFilter(request, response);
    }
}
