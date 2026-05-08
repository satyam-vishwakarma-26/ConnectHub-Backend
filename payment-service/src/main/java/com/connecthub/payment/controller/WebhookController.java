package com.connecthub.payment.controller;

import com.connecthub.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final PaymentService paymentService;

    /**
     * Razorpay webhook endpoint.
     * No JWT auth — verified instead via HMAC-SHA256 X-Razorpay-Signature header.
     */
    @PostMapping("/razorpay")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature) {

        log.info("Razorpay webhook received");
        try {
            paymentService.handleRazorpayWebhook(payload, signature);
            return ResponseEntity.ok(Map.of("status", "received"));
        } catch (Exception e) {
            log.error("Webhook processing error: {}", e.getMessage());
            return ResponseEntity.status(400)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
