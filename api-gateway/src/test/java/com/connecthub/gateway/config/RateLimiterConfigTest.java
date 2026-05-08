package com.connecthub.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimiterConfig Tests")
class RateLimiterConfigTest {

    private RateLimiterConfig rateLimiterConfig;

    @BeforeEach
    void setUp() {
        rateLimiterConfig = new RateLimiterConfig();
    }

    @Test
    @DisplayName("userKeyResolver returns user prefixed key when userId header present")
    void userKeyResolver_withUserIdHeader_returnsUserKey() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Auth-User-Id", "123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        KeyResolver resolver = rateLimiterConfig.userKeyResolver();
        Mono<String> keyMono = resolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .assertNext(key -> assertThat(key).isEqualTo("user:123"))
                .verifyComplete();
    }

    @Test
    @DisplayName("userKeyResolver returns ip prefixed key when userId header absent")
    void userKeyResolver_withoutUserIdHeader_returnsIpKey() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .remoteAddress(new InetSocketAddress("192.168.1.100", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        KeyResolver resolver = rateLimiterConfig.userKeyResolver();
        Mono<String> keyMono = resolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .assertNext(key -> assertThat(key).isEqualTo("ip:192.168.1.100"))
                .verifyComplete();
    }

    @Test
    @DisplayName("userKeyResolver returns ip prefixed key when userId header is blank")
    void userKeyResolver_withBlankUserIdHeader_returnsIpKey() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Auth-User-Id", "   ")
                .remoteAddress(new InetSocketAddress("192.168.1.101", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        KeyResolver resolver = rateLimiterConfig.userKeyResolver();
        Mono<String> keyMono = resolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .assertNext(key -> assertThat(key).isEqualTo("ip:192.168.1.101"))
                .verifyComplete();
    }

    @Test
    @DisplayName("userKeyResolver handles X-Forwarded-For header")
    void userKeyResolver_withXForwardedFor_returnsForwardedIp() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Forwarded-For", "10.0.0.1, 192.168.1.1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        KeyResolver resolver = rateLimiterConfig.userKeyResolver();
        Mono<String> keyMono = resolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .assertNext(key -> assertThat(key).isEqualTo("ip:10.0.0.1"))
                .verifyComplete();
    }

    @Test
    @DisplayName("userKeyResolver returns unknown when no remote address")
    void userKeyResolver_withoutRemoteAddress_returnsUnknown() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        KeyResolver resolver = rateLimiterConfig.userKeyResolver();
        Mono<String> keyMono = resolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .assertNext(key -> assertThat(key).isEqualTo("ip:unknown"))
                .verifyComplete();
    }

    @Test
    @DisplayName("ipKeyResolver returns ip prefixed key")
    void ipKeyResolver_returnsIpKey() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .remoteAddress(new InetSocketAddress("192.168.1.200", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        KeyResolver resolver = rateLimiterConfig.ipKeyResolver();
        Mono<String> keyMono = resolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .assertNext(key -> assertThat(key).isEqualTo("ip:192.168.1.200"))
                .verifyComplete();
    }

    @Test
    @DisplayName("ipKeyResolver handles X-Forwarded-For header")
    void ipKeyResolver_withXForwardedFor_returnsForwardedIp() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Forwarded-For", "172.16.0.50")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        KeyResolver resolver = rateLimiterConfig.ipKeyResolver();
        Mono<String> keyMono = resolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .assertNext(key -> assertThat(key).isEqualTo("ip:172.16.0.50"))
                .verifyComplete();
    }

    @Test
    @DisplayName("ipKeyResolver returns unknown when no remote address")
    void ipKeyResolver_withoutRemoteAddress_returnsUnknown() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        KeyResolver resolver = rateLimiterConfig.ipKeyResolver();
        Mono<String> keyMono = resolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .assertNext(key -> assertThat(key).isEqualTo("ip:unknown"))
                .verifyComplete();
    }

    @Test
    @DisplayName("userKeyResolver handles multiple X-Forwarded-For IPs")
    void userKeyResolver_withMultipleXForwardedFor_returnsFirstIp() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Forwarded-For", "10.0.0.1, 10.0.0.2, 10.0.0.3")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        KeyResolver resolver = rateLimiterConfig.userKeyResolver();
        Mono<String> keyMono = resolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .assertNext(key -> assertThat(key).isEqualTo("ip:10.0.0.1"))
                .verifyComplete();
    }

    @Test
    @DisplayName("userKeyResolver handles blank X-Forwarded-For header")
    void userKeyResolver_withBlankXForwardedFor_returnsRemoteAddress() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Forwarded-For", "   ")
                .remoteAddress(new InetSocketAddress("192.168.1.50", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        KeyResolver resolver = rateLimiterConfig.userKeyResolver();
        Mono<String> keyMono = resolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .assertNext(key -> assertThat(key).isEqualTo("ip:192.168.1.50"))
                .verifyComplete();
    }
}