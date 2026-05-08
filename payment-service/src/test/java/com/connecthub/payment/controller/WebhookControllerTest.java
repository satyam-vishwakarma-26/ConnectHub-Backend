package com.connecthub.payment.controller;

import com.connecthub.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private WebhookController webhookController;

    @Test
    void handleWebhook_Success() throws Exception {
        doNothing().when(paymentService).handleRazorpayWebhook(anyString(), anyString());

        ResponseEntity<Map<String, Object>> response = webhookController.handleWebhook("payload", "signature");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("received", response.getBody().get("status"));
        verify(paymentService, times(1)).handleRazorpayWebhook("payload", "signature");
    }

    @Test
    void handleWebhook_Exception() throws Exception {
        doThrow(new RuntimeException("Invalid signature")).when(paymentService).handleRazorpayWebhook(anyString(), anyString());

        ResponseEntity<Map<String, Object>> response = webhookController.handleWebhook("payload", "signature");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid signature", response.getBody().get("error"));
    }
}
