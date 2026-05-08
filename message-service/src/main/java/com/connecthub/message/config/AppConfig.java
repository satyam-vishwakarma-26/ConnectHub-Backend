package com.connecthub.message.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Two RestTemplate beans:
 *
 *  restTemplate          — used for fire-and-forget calls to room-service
 *                          (notifyRoomLastMessageAt). Short 1s/2s timeouts are
 *                          fine because failures are caught and ignored.
 *
 *  mediaRestTemplate     — used for media uploads to media-service, which
 *                          then uploads to Cloudinary. Cloudinary uploads can
 *                          take 5-15 seconds on a slow connection, so we need
 *                          a generous read timeout (30s). Connect timeout stays
 *                          short because media-service is on localhost.
 *
 * IMPORTANT: MediaStorageServiceImpl must @Qualifier("mediaRestTemplate")
 * to get the upload-safe bean.
 */
@Configuration
public class AppConfig {

    /** Short-timeout RestTemplate for internal fire-and-forget calls (room-service etc.) */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1_000);
        factory.setReadTimeout(2_000);
        return new RestTemplate(factory);
    }

    /** Long-timeout RestTemplate for media uploads (media-service → Cloudinary). */
    @Bean("mediaRestTemplate")
    public RestTemplate mediaRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);   // 3 seconds to connect to media-service
        factory.setReadTimeout(30_000);     // 30 seconds for Cloudinary upload to complete
        return new RestTemplate(factory);
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ConnectHub — Message Service API")
                        .description("Message persistence, pagination, edit/delete, " +
                                     "delivery status, search, and pinning.")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
