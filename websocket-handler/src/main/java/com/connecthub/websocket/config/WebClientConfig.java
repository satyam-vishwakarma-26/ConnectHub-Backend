package com.connecthub.websocket.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${app.services.message-service}")
    private String messageServiceUrl;

    @Value("${app.services.presence-service}")
    private String presenceServiceUrl;

    @Value("${app.services.notification-service}")
    private String notificationServiceUrl;

    @Value("${app.services.room-service}")
    private String roomServiceUrl;

    @Value("${app.services.auth-service}")
    private String authServiceUrl;

    @Bean
    public WebClient messageClient() {
        return WebClient.builder().baseUrl(messageServiceUrl).build();
    }

    @Bean
    public WebClient presenceClient() {
        return WebClient.builder().baseUrl(presenceServiceUrl).build();
    }

    @Bean
    public WebClient notificationClient() {
        return WebClient.builder().baseUrl(notificationServiceUrl).build();
    }

    @Bean
    public WebClient roomClient() {
        return WebClient.builder().baseUrl(roomServiceUrl).build();
    }

    @Bean
    public WebClient authClient() {
        return WebClient.builder().baseUrl(authServiceUrl).build();
    }
}
