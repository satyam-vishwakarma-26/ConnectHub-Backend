package com.connecthub.payment.controller;

import com.connecthub.payment.dto.*;
import com.connecthub.payment.security.JwtUtil;
import com.connecthub.payment.service.PaymentService;
import com.razorpay.RazorpayException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService     paymentService;
    private final JwtUtil            jwtUtil;
    private final HttpServletRequest request;

    // ── Helpers ────────────────────────────────────────────

    private String getToken() {
        return request.getHeader("Authorization");
    }

    private Long extractUserId() {
        String token = getToken();
        if (token == null || !token.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or malformed Authorization header");
        }
        return jwtUtil.extractUserId(token.replace("Bearer ", ""));
    }

    // ── Plans (public) ─────────────────────────────────────

    @GetMapping("/plans")
    public ResponseEntity<?> getAllPlans() {
        return ResponseEntity.ok(paymentService.getAllPlans());
    }

    // ── Create Order ───────────────────────────────────────

    @PostMapping("/create-order")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CreateOrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest req) throws RazorpayException {
        Long userId = extractUserId();
        String token = getToken();
        return ResponseEntity.ok(paymentService.createOrder(userId, req, token));
    }

    // ── Verify Payment ─────────────────────────────────────

    @PostMapping("/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<VerifyPaymentResponse> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest req) {
        Long userId = extractUserId();
        String token = getToken();
        return ResponseEntity.ok(paymentService.verifyPayment(userId, req, token));
    }

    // ── Subscription ───────────────────────────────────────

    @GetMapping("/subscription/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubscriptionDTO> getMySubscription() {
        return ResponseEntity.ok(paymentService.getCurrentSubscription(extractUserId()));
    }

    @GetMapping("/subscription/limits")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PlanLimitsDTO> getMyLimits() {
        return ResponseEntity.ok(paymentService.getUserPlanLimits(extractUserId()));
    }

    /**
     * Internal endpoint — called by other services (room-service, message-service).
     * No JWT required (permitted in SecurityConfig).
     */
    @GetMapping("/subscription/limits/{userId}")
    public ResponseEntity<PlanLimitsDTO> getUserLimits(@PathVariable Long userId) {
        return ResponseEntity.ok(paymentService.getUserPlanLimits(userId));
    }

    @PutMapping("/subscription/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> cancelSubscription() {
        Long userId = extractUserId();
        String token = getToken();
        paymentService.cancelSubscription(userId, token);
        return ResponseEntity.ok(Map.of(
                "message",   "Subscription cancelled. PRO access continues until end of billing period.",
                "timestamp", LocalDateTime.now()
        ));
    }

    @PutMapping("/subscription/auto-renew")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> toggleAutoRenew(
            @RequestParam boolean autoRenew) {
        paymentService.toggleAutoRenew(extractUserId(), autoRenew);
        return ResponseEntity.ok(Map.of(
                "autoRenew", autoRenew,
                "message",   autoRenew ? "Auto-renewal enabled" : "Auto-renewal disabled"
        ));
    }

    // ── Payment History ────────────────────────────────────

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getPaymentHistory() {
        return ResponseEntity.ok(paymentService.getPaymentHistory(extractUserId()));
    }

    // ── Health (public) ────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        return ResponseEntity.ok(Map.of(
                "status",  "UP",
                "service", "payment-service",
                "port",    8089,
                "plans",   "FREE, PRO"
        ));
    }
}
