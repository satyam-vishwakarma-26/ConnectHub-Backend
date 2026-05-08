package com.connecthub.media.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter — Unit Tests")
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Gateway header path (X-Auth-User-Id)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Gateway forwarded headers (X-Auth-User-Id)")
    class GatewayHeaderPath {

        @Test
        @DisplayName("Valid headers — sets authentication with USER role")
        void validHeaders_setsAuthentication() throws ServletException, IOException {
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Auth-User-Id",    "42");
            request.addHeader("X-Auth-User-Email", "alice@test.com");
            request.addHeader("X-Auth-User-Role",  "USER");

            filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            AuthenticatedUser principal = (AuthenticatedUser) auth.getPrincipal();
            assertThat(principal.getUserId()).isEqualTo(42L);
            assertThat(principal.getEmail()).isEqualTo("alice@test.com");
            assertThat(principal.getRole()).isEqualTo("USER");
            assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
        }

        @Test
        @DisplayName("Valid headers, no role — defaults to USER")
        void validHeaders_noRole_defaultsToUser() throws ServletException, IOException {
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Auth-User-Id",    "7");
            request.addHeader("X-Auth-User-Email", "bob@test.com");
            // no X-Auth-User-Role header

            filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
        }

        @Test
        @DisplayName("Blank role — defaults to USER")
        void blankRole_defaultsToUser() throws ServletException, IOException {
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Auth-User-Id",   "7");
            request.addHeader("X-Auth-User-Role", "   ");

            filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
        }

        @Test
        @DisplayName("PLATFORM_ADMIN role forwarded — set correctly")
        void adminRole_setCorrectly() throws ServletException, IOException {
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Auth-User-Id",   "1");
            request.addHeader("X-Auth-User-Role", "PLATFORM_ADMIN");

            filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
        }

        @Test
        @DisplayName("Invalid X-Auth-User-Id (not a number) — logs warning, no authentication set")
        void invalidUserId_noAuth() throws ServletException, IOException {
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Auth-User-Id", "not-a-number");

            filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("Existing authentication — not overridden")
        void existingAuth_notOverridden() throws ServletException, IOException {
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

            // Pre-set authentication
            UsernamePasswordAuthenticationToken existing =
                    new UsernamePasswordAuthenticationToken("existing-user", null);
            SecurityContextHolder.getContext().setAuthentication(existing);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Auth-User-Id", "99");

            filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
            verifyNoInteractions(jwtService);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JWT Bearer token path
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Direct Bearer JWT path")
    class JwtBearerPath {

        @Test
        @DisplayName("Valid JWT — sets authentication")
        void validJwt_setsAuthentication() throws ServletException, IOException {
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer valid-token");

            when(jwtService.isTokenValid("valid-token")).thenReturn(true);
            when(jwtService.extractUserId("valid-token")).thenReturn(10L);
            when(jwtService.extractEmail("valid-token")).thenReturn("user@test.com");
            when(jwtService.extractRole("valid-token")).thenReturn("USER");

            filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            AuthenticatedUser principal = (AuthenticatedUser) auth.getPrincipal();
            assertThat(principal.getUserId()).isEqualTo(10L);
            assertThat(principal.getEmail()).isEqualTo("user@test.com");
        }

        @Test
        @DisplayName("Valid JWT with null role — defaults to USER")
        void validJwt_nullRole_defaultsToUser() throws ServletException, IOException {
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer valid-token");

            when(jwtService.isTokenValid("valid-token")).thenReturn(true);
            when(jwtService.extractUserId("valid-token")).thenReturn(10L);
            when(jwtService.extractEmail("valid-token")).thenReturn("user@test.com");
            when(jwtService.extractRole("valid-token")).thenReturn(null);

            filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
        }

        @Test
        @DisplayName("Invalid JWT — no authentication set")
        void invalidJwt_noAuth() throws ServletException, IOException {
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer bad-token");

            when(jwtService.isTokenValid("bad-token")).thenReturn(false);

            filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("JWT processing throws exception — swallowed, no auth set")
        void jwtThrows_noAuth() throws ServletException, IOException {
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer bad-token");

            when(jwtService.isTokenValid("bad-token")).thenThrow(new RuntimeException("parse error"));

            filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("No Authorization header — no auth set, no JWT interaction")
        void noAuthorizationHeader_noAuth() throws ServletException, IOException {
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
            MockHttpServletRequest request = new MockHttpServletRequest();

            filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("Authorization header doesn't start with 'Bearer ' — token not extracted")
        void nonBearerAuthorizationHeader_noAuth() throws ServletException, IOException {
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

            filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verifyNoInteractions(jwtService);
        }
    }
}
