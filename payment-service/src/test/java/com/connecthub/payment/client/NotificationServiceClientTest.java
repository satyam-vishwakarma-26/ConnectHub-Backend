package com.connecthub.payment.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private NotificationServiceClient notificationServiceClient;

    private final String notifServiceUrl = "http://localhost:8084";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationServiceClient, "notifServiceUrl", notifServiceUrl);
    }

    @Test
    void sendPaymentSuccessNotification_Success() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        assertDoesNotThrow(() -> notificationServiceClient.sendPaymentSuccessNotification(1L, 500.0, "Bearer token"));
        verify(restTemplate, times(1)).postForEntity(eq(notifServiceUrl + "/api/notifications/payment/success"), any(), eq(Void.class));
    }

    @Test
    void sendPaymentSuccessNotification_Exception() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new RuntimeException("Connection error"));

        assertDoesNotThrow(() -> notificationServiceClient.sendPaymentSuccessNotification(1L, 500.0, "Bearer token"));
    }

    @Test
    void sendPaymentFailureNotification_Success() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        assertDoesNotThrow(() -> notificationServiceClient.sendPaymentFailureNotification(1L, "Bearer token"));
        verify(restTemplate, times(1)).postForEntity(eq(notifServiceUrl + "/api/notifications/payment/failure"), any(), eq(Void.class));
    }

    @Test
    void sendPaymentFailureNotification_Exception() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new RuntimeException("Connection error"));

        assertDoesNotThrow(() -> notificationServiceClient.sendPaymentFailureNotification(1L, "Bearer token"));
    }

    @Test
    void sendSubscriptionExpiryNotification_Success() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        assertDoesNotThrow(() -> notificationServiceClient.sendSubscriptionExpiryNotification(1L, LocalDateTime.now()));
        verify(restTemplate, times(1)).postForEntity(eq(notifServiceUrl + "/api/notifications/subscription/expiry"), any(), eq(Void.class));
    }

    @Test
    void sendSubscriptionExpiryNotification_Exception() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new RuntimeException("Connection error"));

        assertDoesNotThrow(() -> notificationServiceClient.sendSubscriptionExpiryNotification(1L, LocalDateTime.now()));
    }
}
