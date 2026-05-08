package com.connecthub.payment.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AuthServiceClient authServiceClient;

    private final String authServiceUrl = "http://localhost:8080";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authServiceClient, "authServiceUrl", authServiceUrl);
    }

    @Test
    void getUserById_Success() {
        Map<String, Object> mockBody = Map.of("id", 1L, "username", "testuser");
        ResponseEntity<Map> responseEntity = ResponseEntity.ok(mockBody);

        when(restTemplate.exchange(
                eq(authServiceUrl + "/api/auth/profile/1"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(responseEntity);

        Map<String, Object> result = authServiceClient.getUserById(1L, "Bearer token");
        assertNotNull(result);
        assertEquals(1L, result.get("id"));
    }

    @Test
    void getUserById_Exception() {
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenThrow(new RuntimeException("Connection error"));

        Map<String, Object> result = authServiceClient.getUserById(1L, "Bearer token");
        assertNull(result);
    }

    @Test
    void updateUserPlan_Success() {
        ResponseEntity<Void> responseEntity = ResponseEntity.ok().build();
        when(restTemplate.exchange(
                eq(authServiceUrl + "/api/users/plan/1"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenReturn(responseEntity);

        assertDoesNotThrow(() -> authServiceClient.updateUserPlan(1L, "PRO", "Bearer token"));
        verify(restTemplate, times(1)).exchange(anyString(), any(), any(), eq(Void.class));
    }

    @Test
    void updateUserPlan_Exception() {
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenThrow(new RuntimeException("Error updating plan"));

        assertDoesNotThrow(() -> authServiceClient.updateUserPlan(1L, "PRO", "Bearer token"));
    }
}
