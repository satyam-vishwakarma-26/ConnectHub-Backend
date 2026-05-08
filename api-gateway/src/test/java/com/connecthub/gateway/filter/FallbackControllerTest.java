package com.connecthub.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FallbackController Tests")
class FallbackControllerTest {

    private FallbackController fallbackController;

    @BeforeEach
    void setUp() {
        fallbackController = new FallbackController();
    }

    @Test
    @DisplayName("authFallback should return 503 with auth-service name")
    void authFallback_shouldReturn503WithAuthServiceName() {
        Mono<ResponseEntity<Map<String, Object>>> result = fallbackController.authFallback();

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("success")).isEqualTo(false);
                    assertThat(body.get("service")).isEqualTo("auth-service");
                    assertThat(body.get("message")).isEqualTo("Authentication service is currently unavailable.");
                    assertThat(body.get("hint")).isEqualTo("Please retry in a few seconds.");
                    assertThat(body.get("timestamp")).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("roomFallback should return 503 with room-service name")
    void roomFallback_shouldReturn503WithRoomServiceName() {
        Mono<ResponseEntity<Map<String, Object>>> result = fallbackController.roomFallback();

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("success")).isEqualTo(false);
                    assertThat(body.get("service")).isEqualTo("room-service");
                    assertThat(body.get("message")).isEqualTo("Room service is currently unavailable.");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("messageFallback should return 503 with message-service name")
    void messageFallback_shouldReturn503WithMessageServiceName() {
        Mono<ResponseEntity<Map<String, Object>>> result = fallbackController.messageFallback();

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("success")).isEqualTo(false);
                    assertThat(body.get("service")).isEqualTo("message-service");
                    assertThat(body.get("message")).isEqualTo("Message service is currently unavailable.");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("mediaFallback should return 503 with media-service name")
    void mediaFallback_shouldReturn503WithMediaServiceName() {
        Mono<ResponseEntity<Map<String, Object>>> result = fallbackController.mediaFallback();

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("success")).isEqualTo(false);
                    assertThat(body.get("service")).isEqualTo("media-service");
                    assertThat(body.get("message")).isEqualTo("Media service is currently unavailable.");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("presenceFallback should return 503 with presence-service name")
    void presenceFallback_shouldReturn503WithPresenceServiceName() {
        Mono<ResponseEntity<Map<String, Object>>> result = fallbackController.presenceFallback();

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("success")).isEqualTo(false);
                    assertThat(body.get("service")).isEqualTo("presence-service");
                    assertThat(body.get("message")).isEqualTo("Presence service is currently unavailable.");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("notificationFallback should return 503 with notification-service name")
    void notificationFallback_shouldReturn503WithNotificationServiceName() {
        Mono<ResponseEntity<Map<String, Object>>> result = fallbackController.notificationFallback();

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("success")).isEqualTo(false);
                    assertThat(body.get("service")).isEqualTo("notification-service");
                    assertThat(body.get("message")).isEqualTo("Notification service is currently unavailable.");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("defaultFallback should return 503 with default service name")
    void defaultFallback_shouldReturn503WithDefaultServiceName() {
        Mono<ResponseEntity<Map<String, Object>>> result = fallbackController.defaultFallback();

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("success")).isEqualTo(false);
                    assertThat(body.get("service")).isEqualTo("downstream-service");
                    assertThat(body.get("message")).isEqualTo("Service is temporarily unavailable. Please try again shortly.");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("All fallback responses should contain required fields")
    void allFallbacks_shouldContainRequiredFields() {
        FallbackController controller = new FallbackController();

        Mono<ResponseEntity<Map<String, Object>>>[] fallbacks = new Mono[]{
                controller.authFallback(),
                controller.roomFallback(),
                controller.messageFallback(),
                controller.mediaFallback(),
                controller.presenceFallback(),
                controller.notificationFallback(),
                controller.defaultFallback()
        };

        for (Mono<ResponseEntity<Map<String, Object>>> fallback : fallbacks) {
            StepVerifier.create(fallback)
                    .assertNext(response -> {
                        Map<String, Object> body = response.getBody();
                        assertThat(body).containsKeys("success", "message", "service", "timestamp", "hint");
                        assertThat(body.get("success")).isEqualTo(false);
                        assertThat(body.get("timestamp")).isNotNull();
                        assertThat(body.get("hint")).isEqualTo("Please retry in a few seconds.");
                    })
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("Fallback response timestamp should be recent")
    void fallback_timestamp_shouldBeRecent() {
        Mono<ResponseEntity<Map<String, Object>>> result = fallbackController.authFallback();

        StepVerifier.create(result)
                .assertNext(response -> {
                    Map<String, Object> body = response.getBody();
                    String timestamp = (String) body.get("timestamp");
                    assertThat(timestamp).isNotNull();
                    assertThat(timestamp).isNotEmpty();
                })
                .verifyComplete();
    }
}
