package com.connecthub.payment.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.notification-service.url}")
    private String notifServiceUrl;

    public void sendPaymentSuccessNotification(Long userId, Double amount, String token) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("userId",   userId);
            body.put("amount",   amount);
            body.put("planName", "PRO");
            body.put("message",  "Payment of ₹" + amount + " successful! You are now on PRO plan.");

            post("/api/notifications/payment/success", body, token);
            log.info("Payment success notification sent for userId={}", userId);
        } catch (Exception e) {
            log.error("sendPaymentSuccessNotification failed for userId={}: {}", userId, e.getMessage());
        }
    }

    public void sendPaymentFailureNotification(Long userId, String token) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("userId",   userId);
            body.put("planName", "PRO");
            body.put("message",  "Payment failed for PRO plan. Please try again.");

            post("/api/notifications/payment/failure", body, token);
            log.info("Payment failure notification sent for userId={}", userId);
        } catch (Exception e) {
            log.error("sendPaymentFailureNotification failed for userId={}: {}", userId, e.getMessage());
        }
    }

    public void sendSubscriptionExpiryNotification(Long userId, LocalDateTime expiryDate) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("userId",     userId);
            body.put("planName",   "PRO");
            body.put("expiryDate", expiryDate.toString());
            body.put("message",    "Your PRO subscription expires on " + expiryDate +
                                   ". Renew to keep PRO features.");

            post("/api/notifications/subscription/expiry", body, null);
            log.info("Expiry notification sent for userId={}", userId);
        } catch (Exception e) {
            log.error("sendSubscriptionExpiryNotification failed for userId={}: {}", userId, e.getMessage());
        }
    }

    // ── Private helper ─────────────────────────────────────

    private void post(String path, Map<String, Object> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        if (token != null) headers.set("Authorization", token);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(notifServiceUrl + path, entity, Void.class);
    }
}
