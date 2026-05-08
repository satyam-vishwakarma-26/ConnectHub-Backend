package com.connecthub.gateway.filter;

import com.connecthub.gateway.config.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * AuthenticationFilter — named gateway filter applied to secured routes.
 *
 * What it does:
 *   1. Extracts the Bearer token from the Authorization header.
 *   2. Validates the JWT signature and expiry using the shared secret.
 *   3. If valid: forwards the request and injects three extra headers so
 *      downstream services know who the caller is without re-validating the JWT.
 *   4. If invalid / missing: returns 401 immediately.
 *
 * Downstream headers injected:
 *   X-Auth-User-Id    → userId claim from token
 *   X-Auth-User-Email → subject (email) from token
 *   X-Auth-User-Role  → role claim from token
 */
@Component
@Slf4j
public class AuthenticationFilter
        extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final JwtUtil jwtUtil;

    public AuthenticationFilter(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // ── Extract token ──────────────────────────────
            String token = extractToken(request);

            if (!StringUtils.hasText(token)) {
                log.warn("[Gateway] No token on {} {}", request.getMethod(), request.getURI());
                return unauthorised(exchange.getResponse(), "Missing Authorization header");
            }

            // ── Validate token ─────────────────────────────
            if (!jwtUtil.isValid(token)) {
                log.warn("[Gateway] Invalid token on {} {}", request.getMethod(), request.getURI());
                return unauthorised(exchange.getResponse(), "Invalid or expired token");
            }

            // ── Extract claims & forward ───────────────────
            try {
                Long   userId = jwtUtil.extractUserId(token);
                String email  = jwtUtil.extractEmail(token);
                String role   = jwtUtil.extractRole(token);

                ServerHttpRequest mutated = request.mutate()
                        .header("X-Auth-User-Id",    userId != null ? String.valueOf(userId) : "")
                        .header("X-Auth-User-Email", email  != null ? email  : "")
                        .header("X-Auth-User-Role",  role   != null ? role   : "USER")
                        .build();

                log.debug("[Gateway] Authenticated userId={} role={} → {} {}",
                          userId, role, request.getMethod(), request.getURI());

                return chain.filter(exchange.mutate().request(mutated).build());

            } catch (Exception e) {
                log.error("[Gateway] Token claim extraction failed: {}", e.getMessage());
                return unauthorised(exchange.getResponse(), "Token processing error");
            }
        };
    }

    // ── Helpers ────────────────────────────────────────────

    private String extractToken(ServerHttpRequest request) {
        String bearer = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    private Mono<Void> unauthorised(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"success":false,"message":"%s"}
                """.formatted(message).strip();

        var buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    // ── Config (no properties needed — filter name is enough) ─
    public static class Config {}
}
