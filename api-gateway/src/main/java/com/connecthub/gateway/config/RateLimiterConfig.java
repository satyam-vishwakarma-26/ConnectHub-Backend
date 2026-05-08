package com.connecthub.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * RateLimiterConfig — provides KeyResolver beans used by the
 * RequestRateLimiter filter in application.yml.
 *
 * Two strategies:
 *   userKeyResolver   — rate-limits per authenticated userId (from X-Auth-User-Id header).
 *                       Best for secured routes — each user gets their own bucket.
 *   ipKeyResolver     — rate-limits per client IP address.
 *                       Used for public routes (register, login) where no userId exists.
 *
 * NOTE: RequestRateLimiter requires Redis. If Redis is not available the filter
 * falls back to allowing all requests (fail-open). Configure Redis in
 * application.yml when deploying to production.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class RateLimiterConfig {

    /**
     * Primary resolver — uses the userId injected by AuthenticationFilter.
     * Falls back to IP if the header is absent (public endpoints).
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-Auth-User-Id");

            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }

            // Fallback to IP
            return Mono.just("ip:" + getClientIp(exchange));
        };
    }

    /**
     * IP-only resolver — for public / unauthenticated routes.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just("ip:" + getClientIp(exchange));
    }

    // ── Helper ─────────────────────────────────────────────

    private String getClientIp(org.springframework.web.server.ServerWebExchange exchange) {
        // Respect X-Forwarded-For when behind a load balancer / nginx
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }

        var remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null
                ? remoteAddress.getAddress().getHostAddress()
                : "unknown";
    }
}
