package com.connecthub.payment.service;

import com.connecthub.payment.dto.*;
import com.connecthub.payment.entity.SubscriptionPlan;
import com.connecthub.payment.entity.UserSubscription;
import com.connecthub.payment.entity.enums.PlanName;
import com.razorpay.RazorpayException;

import java.util.List;

public interface PaymentService {

    // ── Plans ──────────────────────────────────────────────
    List<SubscriptionPlan> getAllPlans();
    SubscriptionPlan getPlanByName(PlanName planName);

    // ── Order & Payment ────────────────────────────────────
    CreateOrderResponse createOrder(Long userId, CreateOrderRequest request, String token)
            throws RazorpayException;

    VerifyPaymentResponse verifyPayment(Long userId, VerifyPaymentRequest request, String token);

    // ── Subscription ───────────────────────────────────────
    SubscriptionDTO getCurrentSubscription(Long userId);
    PlanLimitsDTO   getUserPlanLimits(Long userId);
    void cancelSubscription(Long userId, String token);
    void toggleAutoRenew(Long userId, boolean autoRenew);

    // ── Payment History ────────────────────────────────────
    List<PaymentHistoryDTO> getPaymentHistory(Long userId);

    // ── Webhook ────────────────────────────────────────────
    void handleRazorpayWebhook(String payload, String razorpaySignature);

    // ── Scheduled Jobs ─────────────────────────────────────
    void processExpiredSubscriptions();
    void sendExpiryReminders();

    // ── Admin ──────────────────────────────────────────────
    PaymentAnalyticsDTO getPaymentAnalytics();
    List<UserSubscription> getAllSubscriptions();
}
