package com.connecthub.presence.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * Tests for the package-private {@code JwtFilter} and {@code AuthUser} classes
 * defined in {@link SecurityConfig}.
 *
 * JwtFilter is package-private inside the config package — we test it here
 * directly since we are in the same package.
 */
@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    /** Minimal no-op JwtService stand-in that always returns invalid. */
    private JwtService jwtService;
    private JwtFilter  jwtFilter;

    @BeforeEach
    void setUp() throws Exception {
        // JwtService reads "app.jwt.secret" via @Value — create via reflection
        jwtService = new JwtService();
        // Inject a valid 32-byte base64 secret so the bean initialises without Spring context
        var field = JwtService.class.getDeclaredField("secret");
        field.setAccessible(true);
        // base64("12345678901234567890123456789012")
        field.set(jwtService, "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=");

        jwtFilter = new JwtFilter(jwtService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AuthUser
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void authUser_withExplicitRole_hasCorrectAuthority() {
        AuthUser user = new AuthUser(1L, "a@b.com", "PLATFORM_ADMIN");
        Collection<? extends GrantedAuthority> auths = user.getAuthorities();
        assertEquals(1, auths.size());
        assertEquals("ROLE_PLATFORM_ADMIN", auths.iterator().next().getAuthority());
    }

    @Test
    void authUser_withNullRole_defaultsToRoleUser() {
        AuthUser user = new AuthUser(1L, "a@b.com", null);
        assertEquals("ROLE_USER", user.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void authUser_helperMethods_returnExpectedValues() {
        AuthUser user = new AuthUser(42L, "me@here.com", "USER");
        assertEquals(42L,          user.getUserId());
        assertEquals("me@here.com",user.getEmail());
        assertEquals("USER",       user.getRole());
        assertEquals("me@here.com",user.getUsername());
        assertNull(user.getPassword());
        assertTrue(user.isAccountNonExpired());
        assertTrue(user.isAccountNonLocked());
        assertTrue(user.isCredentialsNonExpired());
        assertTrue(user.isEnabled());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JwtFilter — Gateway header path (X-Auth-User-Id)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void filter_withGatewayHeaders_authenticatesFromHeaders()
            throws ServletException, IOException {

        MockHttpServletRequest  req  = new MockHttpServletRequest();
        req.addHeader("X-Auth-User-Id",    "10");
        req.addHeader("X-Auth-User-Email", "gw@test.com");
        req.addHeader("X-Auth-User-Role",  "USER");
        MockHttpServletResponse res  = new MockHttpServletResponse();
        MockFilterChain         chain = new MockFilterChain();

        jwtFilter.doFilterInternal(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        AuthUser principal = (AuthUser) auth.getPrincipal();
        assertEquals(10L,           principal.getUserId());
        assertEquals("gw@test.com", principal.getEmail());
        assertEquals("USER",        principal.getRole());
    }

    @Test
    void filter_withInvalidGatewayUserId_doesNotAuthenticate()
            throws ServletException, IOException {

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Auth-User-Id", "not-a-number");   // triggers NumberFormatException
        MockHttpServletResponse res   = new MockHttpServletResponse();
        MockFilterChain         chain = new MockFilterChain();

        jwtFilter.doFilterInternal(req, res, chain);

        // NumberFormatException is ignored — no auth set
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void filter_withGatewayHeaders_nullEmailAndRole_stillAuthenticates()
            throws ServletException, IOException {

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Auth-User-Id", "5");
        // No email / role headers → both will be null
        MockHttpServletResponse res   = new MockHttpServletResponse();
        MockFilterChain         chain = new MockFilterChain();

        jwtFilter.doFilterInternal(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        AuthUser principal = (AuthUser) auth.getPrincipal();
        assertEquals(5L,   principal.getUserId());
        assertNull(        principal.getEmail());
        // null role → defaults to ROLE_USER in AuthUser
        assertEquals("ROLE_USER", auth.getAuthorities().iterator().next().getAuthority());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JwtFilter — JWT Bearer token fallback path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void filter_withNoHeadersAtAll_doesNotAuthenticate()
            throws ServletException, IOException {

        MockHttpServletRequest  req  = new MockHttpServletRequest();
        MockHttpServletResponse res  = new MockHttpServletResponse();
        MockFilterChain         chain = new MockFilterChain();

        jwtFilter.doFilterInternal(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void filter_withInvalidBearerToken_doesNotAuthenticate()
            throws ServletException, IOException {

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer totally.invalid.token");
        MockHttpServletResponse res   = new MockHttpServletResponse();
        MockFilterChain         chain = new MockFilterChain();

        jwtFilter.doFilterInternal(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void filter_withNonBearerAuthHeader_doesNotAuthenticate()
            throws ServletException, IOException {

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse res   = new MockHttpServletResponse();
        MockFilterChain         chain = new MockFilterChain();

        jwtFilter.doFilterInternal(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void filter_whenAlreadyAuthenticated_doesNotOverrideContext()
            throws ServletException, IOException {

        // Pre-populate the security context
        AuthUser existing = new AuthUser(99L, "existing@test.com", "ADMIN");
        var existingAuth = new org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken(existing, null, existing.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        // Even with a valid-looking gateway header the context must NOT change
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Auth-User-Id", "1");
        req.addHeader("X-Auth-User-Email", "new@test.com");
        MockHttpServletResponse res   = new MockHttpServletResponse();
        MockFilterChain         chain = new MockFilterChain();

        jwtFilter.doFilterInternal(req, res, chain);

        // Should still be the original auth
        AuthUser principal = (AuthUser) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        assertEquals(99L, principal.getUserId());
    }

    @Test
    void filter_chainIsAlwaysCalled() throws ServletException, IOException {
        MockHttpServletRequest  req  = new MockHttpServletRequest();
        MockHttpServletResponse res  = new MockHttpServletResponse();
        FilterChain chain = org.mockito.Mockito.mock(FilterChain.class);

        jwtFilter.doFilterInternal(req, res, chain);

        org.mockito.Mockito.verify(chain)
                .doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }
}
