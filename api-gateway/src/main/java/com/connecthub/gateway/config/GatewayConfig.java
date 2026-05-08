package com.connecthub.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * GatewayConfig — programmatic routes that complement application.yml.
 *
 * CORS and main service routes are declared in application.yml for readability.
 * This class handles:
 *   - Gateway health check shortcut
 *   - OPTIONS preflight pass-through (CORS pre-flight)
 */
@Configuration
public class GatewayConfig {

    /**
     * Allow OPTIONS (CORS pre-flight) through without auth for ALL paths.
     * The global CORS config in application.yml handles the actual headers;
     * this just makes sure the filter chain doesn't block OPTIONS requests.
     */
    @Bean
    public RouteLocator optionsPassthrough(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("options-preflight", r -> r
                        .method(HttpMethod.OPTIONS)
                        .and()
                        .path("/**")
                        .uri("no://op")   // Netty will handle it via CORS config
                )
                .build();
    }
}
