package com.connecthub.media.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {

            // ── Gateway-forwarded headers (preferred path) ──────────────
            String userIdHeader = request.getHeader("X-Auth-User-Id");
            if (StringUtils.hasText(userIdHeader)) {
                try {
                    Long   userId = Long.parseLong(userIdHeader);
                    String email  = request.getHeader("X-Auth-User-Email");
                    String role   = request.getHeader("X-Auth-User-Role");
                    if (role == null || role.isBlank()) role = "USER";

                    AuthenticatedUser principal = new AuthenticatedUser(userId, email, role);
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    principal, null, principal.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } catch (NumberFormatException e) {
                    log.warn("[Media] Invalid X-Auth-User-Id header: {}", userIdHeader);
                }
            } else {
                // ── Direct Bearer JWT (dev / test) ───────────────────────
                String jwt = extractToken(request);
                if (jwt != null) {
                    try {
                        if (jwtService.isTokenValid(jwt)) {
                            Long   userId = jwtService.extractUserId(jwt);
                            String email  = jwtService.extractEmail(jwt);
                            String role   = jwtService.extractRole(jwt);
                            if (role == null) role = "USER";

                            AuthenticatedUser principal = new AuthenticatedUser(userId, email, role);
                            UsernamePasswordAuthenticationToken authToken =
                                    new UsernamePasswordAuthenticationToken(
                                            principal, jwt, principal.getAuthorities());
                            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                        }
                    } catch (Exception e) {
                        log.warn("[Media] JWT processing failed: {}", e.getMessage());
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
