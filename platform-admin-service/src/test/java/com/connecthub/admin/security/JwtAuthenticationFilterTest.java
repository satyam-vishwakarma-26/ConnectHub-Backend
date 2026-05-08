package com.connecthub.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtUtil);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── No Authorization header ───────────────────────────────────────────────

    @Test
    void noAuthorizationHeader_doesNotAuthenticate() throws ServletException, IOException {
        MockHttpServletRequest  request  = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtUtil);
    }

    // ── Authorization header present but does not start with "Bearer " ────────

    @Test
    void authHeaderWithoutBearerPrefix_doesNotAuthenticate() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtUtil);
    }

    // ── Valid token, no role → defaults to "USER" ─────────────────────────────

    @Test
    void validToken_noRole_defaultsToRoleUser() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        when(jwtUtil.isTokenValid("good-token")).thenReturn(true);
        when(jwtUtil.extractUserId("good-token")).thenReturn(10L);
        when(jwtUtil.extractEmail("good-token")).thenReturn("admin@connecthub.com");
        when(jwtUtil.extractRole("good-token")).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("ROLE_USER", auth.getAuthorities().iterator().next().getAuthority());
        AuthenticatedUser principal = (AuthenticatedUser) auth.getPrincipal();
        assertEquals("admin@connecthub.com", principal.getEmail());
        assertEquals(10L,                    principal.getUserId());
    }

    // ── Valid token, role explicitly provided ─────────────────────────────────

    @Test
    void validToken_withRole_setsCorrectAuthority() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer role-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        when(jwtUtil.isTokenValid("role-token")).thenReturn(true);
        when(jwtUtil.extractUserId("role-token")).thenReturn(99L);
        when(jwtUtil.extractEmail("role-token")).thenReturn("platformadmin@connecthub.com");
        when(jwtUtil.extractRole("role-token")).thenReturn("PLATFORM_ADMIN");

        filter.doFilterInternal(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("ROLE_PLATFORM_ADMIN", auth.getAuthorities().iterator().next().getAuthority());
    }

    // ── Token invalid (isTokenValid returns false) ────────────────────────────

    @Test
    void invalidToken_doesNotAuthenticate() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        when(jwtUtil.isTokenValid("expired-token")).thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // ── Already authenticated → existing context not overridden ──────────────

    @Test
    void existingAuthentication_isNotOverridden() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        UsernamePasswordAuthenticationToken existing =
                new UsernamePasswordAuthenticationToken("existing-user", null);
        SecurityContextHolder.getContext().setAuthentication(existing);

        when(jwtUtil.isTokenValid("good-token")).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        assertSame(existing, SecurityContextHolder.getContext().getAuthentication());
        verify(jwtUtil, never()).extractUserId(any());
    }

    // ── Exception during JWT processing ───────────────────────────────────────

    @Test
    void jwtProcessingException_isHandledGracefully() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        when(jwtUtil.isTokenValid("bad-token"))
                .thenThrow(new RuntimeException("broken token"));

        filter.doFilterInternal(request, response, chain);

        verify(jwtUtil).isTokenValid("bad-token");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // ── FilterChain is always called ──────────────────────────────────────────

    @Test
    void filterChainIsAlwaysCalled_evenWhenNoToken() throws ServletException, IOException {
        MockHttpServletRequest  request  = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain             chain    = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void filterChainIsAlwaysCalled_afterSuccessfulAuth() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer ok-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain             chain    = mock(FilterChain.class);

        when(jwtUtil.isTokenValid("ok-token")).thenReturn(true);
        when(jwtUtil.extractUserId("ok-token")).thenReturn(1L);
        when(jwtUtil.extractEmail("ok-token")).thenReturn("u@test.com");
        when(jwtUtil.extractRole("ok-token")).thenReturn("USER");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void filterChainIsAlwaysCalled_afterException() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer error-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain             chain    = mock(FilterChain.class);

        when(jwtUtil.isTokenValid("error-token"))
                .thenThrow(new RuntimeException("error"));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
