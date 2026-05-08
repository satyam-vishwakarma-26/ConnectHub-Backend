package com.connecthub.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * LoggingFilter — runs on EVERY request (global filter, order = -1).
 * Records method, path, downstream service, status code, and latency.
 */
@Component
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        long start = System.currentTimeMillis();

        log.info("[Gateway] → {} {} from {}",
                req.getMethod(),
                req.getURI().getPath(),
                req.getRemoteAddress() != null
                    ? req.getRemoteAddress().getAddress().getHostAddress()
                    : "unknown");

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long elapsed = System.currentTimeMillis() - start;
            int  status  = exchange.getResponse().getStatusCode() != null
                           ? exchange.getResponse().getStatusCode().value()
                           : 0;

            log.info("[Gateway] ← {} {} {} {}ms",
                    status,
                    req.getMethod(),
                    req.getURI().getPath(),
                    elapsed);
        }));
    }

    @Override
    public int getOrder() {
        return -1;  // Run before all other filters
    }
}
