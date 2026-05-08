package com.connecthub.payment.controller;

import com.connecthub.payment.dto.PaymentAnalyticsDTO;
import com.connecthub.payment.entity.UserSubscription;
import com.connecthub.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments/admin")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminPaymentController {

    private final PaymentService paymentService;

    @GetMapping("/analytics")
    public ResponseEntity<PaymentAnalyticsDTO> getAnalytics() {
        return ResponseEntity.ok(paymentService.getPaymentAnalytics());
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<List<UserSubscription>> getAllSubscriptions() {
        return ResponseEntity.ok(paymentService.getAllSubscriptions());
    }
}
