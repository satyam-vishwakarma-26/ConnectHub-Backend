package com.connecthub.eureka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Secure the Eureka dashboard with HTTP Basic.
     * All microservices must include credentials in their eureka.client.service-url:
     *   http://admin:connecthub-eureka-secret@localhost:8761/eureka/
     *
     * CSRF is disabled for the /eureka/** endpoints so that client services
     * can POST their registration without a CSRF token.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for Eureka registration endpoints
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/eureka/**", "/actuator/**"))
            .authorizeHttpRequests(auth -> auth
                // Allow Eureka client registration + heartbeat without login
                .requestMatchers("/eureka/**").authenticated()
                // Actuator health check is public
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            // Protect the web dashboard with Basic Auth
            .httpBasic(org.springframework.security.config.Customizer.withDefaults());

        return http.build();
    }
}
