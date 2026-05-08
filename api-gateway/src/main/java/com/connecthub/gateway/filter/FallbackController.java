package com.connecthub.gateway.filter;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * FallbackController — returns a structured JSON error when a downstream
 * service is unavailable (circuit breaker / timeout).
 *
 * Each route in application.yml can add:
 *   filters:
 *     - name: CircuitBreaker
 *       args:
 *         name: <service>CB
 *         fallbackUri: forward:/fallback/<service>
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/auth")
    public Mono<ResponseEntity<Map<String, Object>>> authFallback() {
        return fallback("auth-service", "Authentication service is currently unavailable.");
    }

    @RequestMapping("/fallback/room")
    public Mono<ResponseEntity<Map<String, Object>>> roomFallback() {
        return fallback("room-service", "Room service is currently unavailable.");
    }

    @RequestMapping("/fallback/message")
    public Mono<ResponseEntity<Map<String, Object>>> messageFallback() {
        return fallback("message-service", "Message service is currently unavailable.");
    }

    @RequestMapping("/fallback/media")
    public Mono<ResponseEntity<Map<String, Object>>> mediaFallback() {
        return fallback("media-service", "Media service is currently unavailable.");
    }

    @RequestMapping("/fallback/presence")
    public Mono<ResponseEntity<Map<String, Object>>> presenceFallback() {
        return fallback("presence-service", "Presence service is currently unavailable.");
    }

    @RequestMapping("/fallback/notification")
    public Mono<ResponseEntity<Map<String, Object>>> notificationFallback() {
        return fallback("notification-service", "Notification service is currently unavailable.");
    }

    @RequestMapping("/fallback/default")
    public Mono<ResponseEntity<Map<String, Object>>> defaultFallback() {
        return fallback("downstream-service", "Service is temporarily unavailable. Please try again shortly.");
    }

    // ── Helper ─────────────────────────────────────────────
    private Mono<ResponseEntity<Map<String, Object>>> fallback(String service, String message) {
        Map<String, Object> body = Map.of(
                "success",   false,
                "message",   message,
                "service",   service,
                "timestamp", LocalDateTime.now().toString(),
                "hint",      "Please retry in a few seconds."
        );
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(body));
    }
}
