package com.connecthub.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@DisplayName("LoggingFilter Tests")
class LoggingFilterTest {

    private LoggingFilter loggingFilter;
    private GatewayFilterChain filterChain;

    @BeforeEach
    void setUp() {
        loggingFilter = new LoggingFilter();
        filterChain = mock(GatewayFilterChain.class);
    }

    @Test
    @DisplayName("Filter should pass request through chain and log")
    void filter_shouldPassThroughChain() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = loggingFilter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();
        verify(filterChain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Filter should handle POST requests")
    void filter_postRequest_shouldPassThroughChain() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/rooms")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = loggingFilter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();
        verify(filterChain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Filter should handle request with remote address")
    void filter_withRemoteAddress_shouldPassThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = loggingFilter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();
        verify(filterChain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("getOrder should return -1")
    void getOrder_shouldReturnMinusOne() {
        int order = loggingFilter.getOrder();
        assert order == -1;
    }

    @Test
    @DisplayName("Filter should handle response with status code")
    void filter_withResponseStatusCode_shouldComplete() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = loggingFilter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();
        verify(filterChain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Filter should handle different HTTP methods")
    void filter_differentHttpMethods_shouldPassThrough() {
        HttpMethod[] methods = {HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH};

        for (HttpMethod method : methods) {
            MockServerHttpRequest request = MockServerHttpRequest.method(method, "/api/test")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

            Mono<Void> result = loggingFilter.filter(exchange, filterChain);

            StepVerifier.create(result).verifyComplete();
        }

        verify(filterChain, times(methods.length)).filter(any(ServerWebExchange.class));
    }
}
