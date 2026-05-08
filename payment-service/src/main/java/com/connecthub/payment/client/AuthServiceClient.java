package com.connecthub.payment.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.auth-service.url}")
    private String authServiceUrl;

    /**
     * Fetches a user's profile from auth-service.
     * Returns null on any error (fire-and-forget style).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getUserById(Long userId, String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            return restTemplate.exchange(
                    authServiceUrl + "/api/auth/profile/" + userId,
                    HttpMethod.GET,
                    entity,
                    Map.class
            ).getBody();
        } catch (Exception e) {
            log.error("AuthServiceClient.getUserById failed for userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Notifies auth-service of the user's new plan (FREE or PRO).
     * Fire-and-forget — failure is logged but does not block the payment flow.
     */
    public void updateUserPlan(Long userId, String planName, String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", token);
            headers.set("Content-Type", "application/json");

            Map<String, String> body = Map.of("planName", planName);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            restTemplate.exchange(
                    authServiceUrl + "/api/users/plan/" + userId,
                    HttpMethod.PUT,
                    entity,
                    Void.class
            );
            log.info("Updated plan for userId={} to {}", userId, planName);
        } catch (Exception e) {
            log.error("AuthServiceClient.updateUserPlan failed for userId={}: {}", userId, e.getMessage());
        }
    }
}
