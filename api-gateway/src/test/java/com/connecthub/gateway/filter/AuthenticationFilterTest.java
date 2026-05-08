package com.connecthub.gateway.filter;

import com.connecthub.gateway.config.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationFilter Tests")
class AuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private GatewayFilterChain filterChain;

    @InjectMocks
    private AuthenticationFilter authenticationFilter;

    private GatewayFilter filter;

    @BeforeEach
    void setUp() {
        filter = authenticationFilter.apply(new AuthenticationFilter.Config());
    }

    @Test
    @DisplayName("Missing Authorization header returns 401")
    void apply_missingAuthHeader_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        MockServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    @DisplayName("Empty Authorization header returns 401")
    void apply_emptyAuthHeader_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        MockServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    @DisplayName("Authorization header without Bearer prefix returns 401")
    void apply_noBearerPrefix_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Basic some-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        MockServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    @DisplayName("Invalid token returns 401")
    void apply_invalidToken_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.isValid("invalid-token")).thenReturn(false);

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        MockServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(jwtUtil).isValid("invalid-token");
    }

    @Test
    @DisplayName("Valid token forwards request with headers")
    void apply_validToken_forwardsRequestWithHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.isValid("valid-token")).thenReturn(true);
        when(jwtUtil.extractUserId("valid-token")).thenReturn(100L);
        when(jwtUtil.extractEmail("valid-token")).thenReturn("user@test.com");
        when(jwtUtil.extractRole("valid-token")).thenReturn("USER");
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        verify(filterChain).filter(argThat(ex -> {
            var headers = ex.getRequest().getHeaders();
            return "100".equals(headers.getFirst("X-Auth-User-Id")) &&
                   "user@test.com".equals(headers.getFirst("X-Auth-User-Email")) &&
                   "USER".equals(headers.getFirst("X-Auth-User-Role"));
        }));
    }

    @Test
    @DisplayName("Valid token with null userId forwards with empty userId header")
    void apply_validTokenWithNullUserId_forwardsWithEmptyHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.isValid("valid-token")).thenReturn(true);
        when(jwtUtil.extractUserId("valid-token")).thenReturn(null);
        when(jwtUtil.extractEmail("valid-token")).thenReturn("user@test.com");
        when(jwtUtil.extractRole("valid-token")).thenReturn("USER");
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        verify(filterChain).filter(argThat(ex -> {
            var headers = ex.getRequest().getHeaders();
            return "".equals(headers.getFirst("X-Auth-User-Id")) &&
                   "user@test.com".equals(headers.getFirst("X-Auth-User-Email")) &&
                   "USER".equals(headers.getFirst("X-Auth-User-Role"));
        }));
    }

    @Test
    @DisplayName("Valid token with null email forwards with empty email header")
    void apply_validTokenWithNullEmail_forwardsWithEmptyHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.isValid("valid-token")).thenReturn(true);
        when(jwtUtil.extractUserId("valid-token")).thenReturn(100L);
        when(jwtUtil.extractEmail("valid-token")).thenReturn(null);
        when(jwtUtil.extractRole("valid-token")).thenReturn("USER");
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        verify(filterChain).filter(argThat(ex -> {
            var headers = ex.getRequest().getHeaders();
            return "100".equals(headers.getFirst("X-Auth-User-Id")) &&
                   "".equals(headers.getFirst("X-Auth-User-Email")) &&
                   "USER".equals(headers.getFirst("X-Auth-User-Role"));
        }));
    }

    @Test
    @DisplayName("Valid token with null role forwards with default USER role")
    void apply_validTokenWithNullRole_forwardsWithDefaultRole() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.isValid("valid-token")).thenReturn(true);
        when(jwtUtil.extractUserId("valid-token")).thenReturn(100L);
        when(jwtUtil.extractEmail("valid-token")).thenReturn("user@test.com");
        when(jwtUtil.extractRole("valid-token")).thenReturn(null);
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        verify(filterChain).filter(argThat(ex -> {
            var headers = ex.getRequest().getHeaders();
            return "100".equals(headers.getFirst("X-Auth-User-Id")) &&
                   "user@test.com".equals(headers.getFirst("X-Auth-User-Email")) &&
                   "USER".equals(headers.getFirst("X-Auth-User-Role"));
        }));
    }

    @Test
    @DisplayName("Exception during claim extraction returns 401")
    void apply_claimExtractionThrowsException_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.isValid("valid-token")).thenReturn(true);
        when(jwtUtil.extractUserId("valid-token")).thenThrow(new RuntimeException("Token parsing failed"));

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        MockServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Bearer token with only Bearer prefix returns 401")
    void apply_onlyBearerPrefix_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        MockServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    @DisplayName("POST request with valid token forwards correctly")
    void apply_postRequest_validToken_forwardsRequest() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/rooms")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.isValid("valid-token")).thenReturn(true);
        when(jwtUtil.extractUserId("valid-token")).thenReturn(200L);
        when(jwtUtil.extractEmail("valid-token")).thenReturn("test@example.com");
        when(jwtUtil.extractRole("valid-token")).thenReturn("ADMIN");
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        verify(filterChain).filter(argThat(ex -> {
            var headers = ex.getRequest().getHeaders();
            return "200".equals(headers.getFirst("X-Auth-User-Id")) &&
                   "test@example.com".equals(headers.getFirst("X-Auth-User-Email")) &&
                   "ADMIN".equals(headers.getFirst("X-Auth-User-Role"));
        }));
    }

    @Test
    @DisplayName("PUT request with valid token forwards correctly")
    void apply_putRequest_validToken_forwardsRequest() {
        MockServerHttpRequest request = MockServerHttpRequest.put("/api/rooms/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.isValid("valid-token")).thenReturn(true);
        when(jwtUtil.extractUserId("valid-token")).thenReturn(300L);
        when(jwtUtil.extractEmail("valid-token")).thenReturn("put@test.com");
        when(jwtUtil.extractRole("valid-token")).thenReturn("USER");
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        verify(filterChain).filter(argThat(ex -> {
            var headers = ex.getRequest().getHeaders();
            return "300".equals(headers.getFirst("X-Auth-User-Id")) &&
                   "put@test.com".equals(headers.getFirst("X-Auth-User-Email")) &&
                   "USER".equals(headers.getFirst("X-Auth-User-Role"));
        }));
    }

    @Test
    @DisplayName("DELETE request with valid token forwards correctly")
    void apply_deleteRequest_validToken_forwardsRequest() {
        MockServerHttpRequest request = MockServerHttpRequest.delete("/api/rooms/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.isValid("valid-token")).thenReturn(true);
        when(jwtUtil.extractUserId("valid-token")).thenReturn(400L);
        when(jwtUtil.extractEmail("valid-token")).thenReturn("delete@test.com");
        when(jwtUtil.extractRole("valid-token")).thenReturn("ADMIN");
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        verify(filterChain).filter(argThat(ex -> {
            var headers = ex.getRequest().getHeaders();
            return "400".equals(headers.getFirst("X-Auth-User-Id")) &&
                   "delete@test.com".equals(headers.getFirst("X-Auth-User-Email")) &&
                   "ADMIN".equals(headers.getFirst("X-Auth-User-Role"));
        }));
    }
}