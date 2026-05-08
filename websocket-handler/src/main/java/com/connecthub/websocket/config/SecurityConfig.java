package com.connecthub.websocket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SecurityConfig for the WebSocket handler.
 *
 * Spring Security must allow the /ws/** upgrade requests through —
 * actual JWT authentication is done inside the STOMP CONNECT interceptor
 * in WebSocketConfig, not at the HTTP layer.
 *
 * REST endpoints (/api/ws/**) use the same stateless JWT approach
 * as all other services.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // WebSocket SockJS endpoints — auth done at STOMP level
                .requestMatchers("/ws/**").permitAll()
                // Actuator health
                .requestMatchers("/actuator/health").permitAll()
                // Swagger
                .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                        "/swagger-ui/index.html", "/api/swagger-ui/**",
                        "/api/swagger-ui.html", "/api/swagger-ui/index.html").permitAll()
                // Admin stats endpoint requires auth
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
