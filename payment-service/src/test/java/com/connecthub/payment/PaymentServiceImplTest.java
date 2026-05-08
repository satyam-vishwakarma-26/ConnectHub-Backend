package com.connecthub.payment;

import com.connecthub.payment.client.AuthServiceClient;
import com.connecthub.payment.client.NotificationServiceClient;
import com.connecthub.payment.dto.*;
import com.connecthub.payment.entity.PaymentTransaction;
import com.connecthub.payment.entity.SubscriptionPlan;
import com.connecthub.payment.entity.UserSubscription;
import com.connecthub.payment.entity.enums.PlanName;
import com.connecthub.payment.entity.enums.SubscriptionStatus;
import com.connecthub.payment.entity.enums.TransactionStatus;
import com.connecthub.payment.exception.PaymentException;
import com.connecthub.payment.exception.SubscriptionNotFoundException;
import com.connecthub.payment.repository.*;
import com.connecthub.payment.service.impl.PaymentServiceImpl;
import com.razorpay.RazorpayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl Tests")
class PaymentServiceImplTest {

    @Mock private RazorpayClient                razorpayClient;
    @Mock private SubscriptionPlanRepository    planRepository;
    @Mock private UserSubscriptionRepository    subscriptionRepository;
    @Mock private PaymentTransactionRepository  transactionRepository;
    @Mock private WebhookLogRepository          webhookLogRepository;
    @Mock private AuthServiceClient             authClient;
    @Mock private NotificationServiceClient     notifClient;

    @InjectMocks private PaymentServiceImpl paymentService;

    private SubscriptionPlan freePlan;
    private SubscriptionPlan proPlan;
    private UserSubscription  activeSubscription;
    private PaymentTransaction transaction;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "razorpayKeyId",    "rzp_test_key");
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "test_secret");
        ReflectionTestUtils.setField(paymentService, "webhookSecret",     "webhook_secret");

        freePlan = SubscriptionPlan.builder()
                .planId(1).planName(PlanName.FREE).price(0.00)
                .maxRooms(5).maxMembersPerRoom(50).maxFileSizeMb(5)
                .messageHistoryDays(30).maxDevices(1)
                .customRoomAvatar(false).priorityNotifications(false)
                .readReceipts(false).messageReactions(false).isActive(true).build();

        proPlan = SubscriptionPlan.builder()
                .planId(2).planName(PlanName.PRO).price(199.00)
                .maxRooms(-1).maxMembersPerRoom(-1).maxFileSizeMb(100)
                .messageHistoryDays(-1).maxDevices(-1)
                .customRoomAvatar(true).priorityNotifications(true)
                .readReceipts(true).messageReactions(true).isActive(true).build();

        activeSubscription = UserSubscription.builder()
                .subscriptionId(10L).userId(100L)
                .planName(PlanName.PRO).status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(5))
                .endDate(LocalDateTime.now().plusDays(25))
                .nextBillingDate(LocalDateTime.now().plusDays(25))
                .autoRenew(true).build();

        transaction = PaymentTransaction.builder()
                .transactionId(1L).userId(100L).planName(PlanName.PRO)
                .razorpayOrderId("order_test123")
                .amount(199.00).currency("INR")
                .status(TransactionStatus.CREATED).build();
    }

    // ── getAllPlans ────────────────────────────────────────

    @Test
    @DisplayName("getAllPlans() — returns FREE and PRO plans")
    void getAllPlans_returnsBothPlans() {
        when(planRepository.findByIsActiveTrue()).thenReturn(List.of(freePlan, proPlan));

        List<SubscriptionPlan> plans = paymentService.getAllPlans();

        assertThat(plans).hasSize(2);
        assertThat(plans).extracting(SubscriptionPlan::getPlanName)
                .containsExactlyInAnyOrder(PlanName.FREE, PlanName.PRO);
    }

    // ── getPlanByName ──────────────────────────────────────

    @Test
    @DisplayName("getPlanByName() — returns correct plan")
    void getPlanByName_found() {
        when(planRepository.findByPlanName(PlanName.PRO)).thenReturn(Optional.of(proPlan));

        SubscriptionPlan plan = paymentService.getPlanByName(PlanName.PRO);

        assertThat(plan.getPlanName()).isEqualTo(PlanName.PRO);
        assertThat(plan.getPrice()).isEqualTo(199.00);
    }

    @Test
    @DisplayName("getPlanByName() — throws RuntimeException when not found")
    void getPlanByName_notFound_throws() {
        when(planRepository.findByPlanName(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPlanByName(PlanName.PRO))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Plan not found");
    }

    // ── createOrder — FREE plan guard ─────────────────────

    @Test
    @DisplayName("createOrder() — FREE plan throws PaymentException")
    void createOrder_freePlan_throwsPaymentException() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanName(PlanName.FREE);

        assertThatThrownBy(() -> paymentService.createOrder(100L, req, "Bearer token"))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("FREE plan requires no payment");
    }

    // ── getCurrentSubscription ─────────────────────────────

    @Test
    @DisplayName("getCurrentSubscription() — returns PRO subscription when active")
    void getCurrentSubscription_active_returnsPro() {
        when(subscriptionRepository.findByUserIdAndStatus(100L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSubscription));
        when(planRepository.findByPlanName(PlanName.PRO)).thenReturn(Optional.of(proPlan));

        SubscriptionDTO result = paymentService.getCurrentSubscription(100L);

        assertThat(result.isProUser()).isTrue();
        assertThat(result.getPlanName()).isEqualTo(PlanName.PRO);
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("getCurrentSubscription() — returns FREE plan when no active subscription")
    void getCurrentSubscription_noActive_returnsFree() {
        when(subscriptionRepository.findByUserIdAndStatus(100L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(planRepository.findByPlanName(PlanName.FREE)).thenReturn(Optional.of(freePlan));

        SubscriptionDTO result = paymentService.getCurrentSubscription(100L);

        assertThat(result.isProUser()).isFalse();
        assertThat(result.getPlanName()).isEqualTo(PlanName.FREE);
    }

    // ── getUserPlanLimits ──────────────────────────────────

    @Test
    @DisplayName("getUserPlanLimits() — PRO user gets unlimited limits")
    void getUserPlanLimits_proUser_returnsUnlimited() {
        when(subscriptionRepository.existsByUserIdAndStatus(100L, SubscriptionStatus.ACTIVE))
                .thenReturn(true);
        when(planRepository.findByPlanName(PlanName.PRO)).thenReturn(Optional.of(proPlan));

        PlanLimitsDTO limits = paymentService.getUserPlanLimits(100L);

        assertThat(limits.isProUser()).isTrue();
        assertThat(limits.getMaxRooms()).isEqualTo(-1);
        assertThat(limits.getReadReceipts()).isTrue();
        assertThat(limits.getMessageReactions()).isTrue();
    }

    @Test
    @DisplayName("getUserPlanLimits() — FREE user gets restricted limits")
    void getUserPlanLimits_freeUser_returnsLimited() {
        when(subscriptionRepository.existsByUserIdAndStatus(100L, SubscriptionStatus.ACTIVE))
                .thenReturn(false);
        when(planRepository.findByPlanName(PlanName.FREE)).thenReturn(Optional.of(freePlan));

        PlanLimitsDTO limits = paymentService.getUserPlanLimits(100L);

        assertThat(limits.isProUser()).isFalse();
        assertThat(limits.getMaxRooms()).isEqualTo(5);
        assertThat(limits.getReadReceipts()).isFalse();
    }

    // ── cancelSubscription ─────────────────────────────────

    @Test
    @DisplayName("cancelSubscription() — cancels active subscription")
    void cancelSubscription_success() {
        when(subscriptionRepository.findByUserIdAndStatus(100L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSubscription));
        when(subscriptionRepository.save(any())).thenReturn(activeSubscription);

        assertThatCode(() -> paymentService.cancelSubscription(100L, "token"))
                .doesNotThrowAnyException();

        assertThat(activeSubscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(activeSubscription.getAutoRenew()).isFalse();
        assertThat(activeSubscription.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("cancelSubscription() — throws SubscriptionNotFoundException when none active")
    void cancelSubscription_noActive_throws() {
        when(subscriptionRepository.findByUserIdAndStatus(100L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.cancelSubscription(100L, "token"))
                .isInstanceOf(SubscriptionNotFoundException.class)
                .hasMessageContaining("No active subscription");
    }

    // ── toggleAutoRenew ────────────────────────────────────

    @Test
    @DisplayName("toggleAutoRenew() — disables auto-renewal")
    void toggleAutoRenew_disable_success() {
        when(subscriptionRepository.findByUserIdAndStatus(100L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSubscription));
        when(subscriptionRepository.save(any())).thenReturn(activeSubscription);

        paymentService.toggleAutoRenew(100L, false);

        assertThat(activeSubscription.getAutoRenew()).isFalse();
    }

    // ── getPaymentHistory ──────────────────────────────────

    @Test
    @DisplayName("getPaymentHistory() — returns mapped payment history")
    void getPaymentHistory_returnsList() {
        when(transactionRepository.findByUserIdOrderByCreatedAtDesc(100L))
                .thenReturn(List.of(transaction));

        List<PaymentHistoryDTO> history = paymentService.getPaymentHistory(100L);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getPlanName()).isEqualTo(PlanName.PRO);
        assertThat(history.get(0).getAmount()).isEqualTo(199.00);
    }

    // ── processExpiredSubscriptions ────────────────────────

    @Test
    @DisplayName("processExpiredSubscriptions() — marks expired subscriptions")
    void processExpiredSubscriptions_marksExpired() {
        UserSubscription expiredSub = UserSubscription.builder()
                .subscriptionId(20L).userId(200L)
                .planName(PlanName.PRO).status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(35))
                .endDate(LocalDateTime.now().minusDays(5))
                .build();

        when(subscriptionRepository.findByStatusAndEndDateBefore(
                eq(SubscriptionStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredSub));
        when(subscriptionRepository.save(any())).thenReturn(expiredSub);

        paymentService.processExpiredSubscriptions();

        assertThat(expiredSub.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        verify(subscriptionRepository).save(expiredSub);
    }

    // ── getPaymentAnalytics ────────────────────────────────

    @Test
    @DisplayName("getPaymentAnalytics() — returns aggregated analytics")
    void getPaymentAnalytics_returnsData() {
        when(transactionRepository.count()).thenReturn(50L);
        when(transactionRepository.countByStatus(TransactionStatus.SUCCESS)).thenReturn(40L);
        when(transactionRepository.countByStatus(TransactionStatus.FAILED)).thenReturn(10L);
        when(transactionRepository.sumAmountByStatus(TransactionStatus.SUCCESS)).thenReturn(7960.00);
        when(subscriptionRepository.countByStatusAndPlanName(SubscriptionStatus.ACTIVE, PlanName.PRO))
                .thenReturn(38L);

        PaymentAnalyticsDTO analytics = paymentService.getPaymentAnalytics();

        assertThat(analytics.getTotalTransactions()).isEqualTo(50L);
        assertThat(analytics.getSuccessfulTransactions()).isEqualTo(40L);
        assertThat(analytics.getFailedTransactions()).isEqualTo(10L);
        assertThat(analytics.getTotalRevenue()).isEqualTo(7960.00);
        assertThat(analytics.getProUsers()).isEqualTo(38L);
        assertThat(analytics.getGeneratedAt()).isNotNull();
    }

    // ── handleWebhook — invalid signature ─────────────────

    @Test
    @DisplayName("handleRazorpayWebhook() — invalid signature throws PaymentException")
    void handleWebhook_invalidSignature_throws() {
        assertThatThrownBy(() ->
                paymentService.handleRazorpayWebhook("{\"event\":\"test\"}", "bad_signature"))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("Invalid webhook signature");
    }

    // ── createOrder ────────────────────────────────────────

    @Test
    @DisplayName("createOrder() — success")
    void createOrder_success() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanName(PlanName.PRO);

        com.razorpay.OrderClient orderClient = mock(com.razorpay.OrderClient.class);
        ReflectionTestUtils.setField(razorpayClient, "orders", orderClient);

        com.razorpay.Order order = mock(com.razorpay.Order.class);
        when(order.get("id")).thenReturn("order_test_123");
        when(order.get("receipt")).thenReturn("rcpt_100_123");
        when(orderClient.create(any(org.json.JSONObject.class))).thenReturn(order);

        when(transactionRepository.save(any(PaymentTransaction.class))).thenReturn(transaction);

        CreateOrderResponse res = paymentService.createOrder(100L, req, "Bearer token");

        assertThat(res.getRazorpayOrderId()).isEqualTo("order_test_123");
        verify(transactionRepository).save(any(PaymentTransaction.class));
    }

    // ── verifyPayment ──────────────────────────────────────

    @Test
    @DisplayName("verifyPayment() — invalid signature")
    void verifyPayment_invalidSignature() {
        VerifyPaymentRequest req = new VerifyPaymentRequest();
        req.setRazorpayOrderId("order_test123");
        req.setRazorpayPaymentId("pay_test123");
        req.setRazorpaySignature("invalid");

        assertThatThrownBy(() -> paymentService.verifyPayment(100L, req, "token"))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("Invalid payment signature");
    }

    @Test
    @DisplayName("verifyPayment() — success with email")
    void verifyPayment_success() throws Exception {
        VerifyPaymentRequest req = new VerifyPaymentRequest();
        req.setRazorpayOrderId("order_test123");
        req.setRazorpayPaymentId("pay_test123");
        
        // Generate valid signature
        String data = "order_test123|pay_test123";
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("test_secret".getBytes(), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        req.setRazorpaySignature(sb.toString());

        when(transactionRepository.findByRazorpayOrderId("order_test123")).thenReturn(Optional.of(transaction));
        when(subscriptionRepository.findByUserIdAndStatus(100L, SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(UserSubscription.class))).thenReturn(activeSubscription);

        java.util.Map<String, Object> mockProfile = new java.util.HashMap<>();
        java.util.Map<String, String> dataMap = new java.util.HashMap<>();
        dataMap.put("email", "test@test.com");
        dataMap.put("username", "tester");
        mockProfile.put("data", dataMap);
        
        when(authClient.getUserById(eq(100L), anyString())).thenReturn(mockProfile);

        VerifyPaymentResponse res = paymentService.verifyPayment(100L, req, "token");

        assertThat(res.isSuccess()).isTrue();
        verify(transactionRepository, times(2)).save(any(PaymentTransaction.class));
        verify(notifClient).sendPaymentSuccessNotification(100L, 199.00, "token");
    }

    // ── cancelSubscription email ───────────────────────────

    @Test
    @DisplayName("cancelSubscription() — triggers email via auth client")
    void cancelSubscription_sendsEmail() {
        when(subscriptionRepository.findByUserIdAndStatus(100L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSubscription));
        
        java.util.Map<String, Object> mockProfile = new java.util.HashMap<>();
        java.util.Map<String, String> dataMap = new java.util.HashMap<>();
        dataMap.put("email", "test@test.com");
        mockProfile.put("data", dataMap);
        
        when(authClient.getUserById(eq(100L), anyString())).thenReturn(mockProfile);

        paymentService.cancelSubscription(100L, "token");
        
        verify(subscriptionRepository).save(activeSubscription);
        assertThat(activeSubscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    // ── webhook payment.captured ───────────────────────────

    @Test
    @DisplayName("handleRazorpayWebhook() — payment.captured")
    void handleRazorpayWebhook_paymentCaptured() throws Exception {
        String payload = "{\"event\":\"payment.captured\",\"id\":\"evt_1\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_123\"}}}}";
        
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("webhook_secret".getBytes(), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        String sig = sb.toString();

        when(webhookLogRepository.findByRazorpayEventId("evt_1")).thenReturn(Optional.empty());
        when(webhookLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.findByRazorpayPaymentId("pay_123")).thenReturn(Optional.of(transaction));

        paymentService.handleRazorpayWebhook(payload, sig);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
    }

    @Test
    @DisplayName("handleRazorpayWebhook() — payment.failed")
    void handleRazorpayWebhook_paymentFailed() throws Exception {
        String payload = "{\"event\":\"payment.failed\",\"id\":\"evt_2\",\"payload\":{\"payment\":{\"entity\":{\"order_id\":\"order_123\", \"error_description\":\"failed\"}}}}";
        
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("webhook_secret".getBytes(), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        String sig = sb.toString();

        when(webhookLogRepository.findByRazorpayEventId("evt_2")).thenReturn(Optional.empty());
        when(webhookLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.findByRazorpayOrderId("order_123")).thenReturn(Optional.of(transaction));

        paymentService.handleRazorpayWebhook(payload, sig);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        verify(notifClient).sendPaymentFailureNotification(eq(100L), isNull());
    }

    @Test
    @DisplayName("handleRazorpayWebhook() — refund.created")
    void handleRazorpayWebhook_refundCreated() throws Exception {
        String payload = "{\"event\":\"refund.created\",\"id\":\"evt_3\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"ref_123\", \"payment_id\":\"pay_123\"}}}}";
        
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("webhook_secret".getBytes(), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        String sig = sb.toString();

        when(webhookLogRepository.findByRazorpayEventId("evt_3")).thenReturn(Optional.empty());
        when(webhookLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.findByRazorpayPaymentId("pay_123")).thenReturn(Optional.of(transaction));

        paymentService.handleRazorpayWebhook(payload, sig);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.REFUNDED);
        assertThat(transaction.getRefundId()).isEqualTo("ref_123");
    }
    
    @Test
    @DisplayName("handleRazorpayWebhook() — duplicate event")
    void handleRazorpayWebhook_duplicateEvent() throws Exception {
        String payload = "{\"event\":\"payment.captured\",\"id\":\"evt_4\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_123\"}}}}";
        
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("webhook_secret".getBytes(), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        String sig = sb.toString();

        when(webhookLogRepository.findByRazorpayEventId("evt_4")).thenReturn(Optional.of(new com.connecthub.payment.entity.WebhookLog()));

        paymentService.handleRazorpayWebhook(payload, sig);

        verify(webhookLogRepository, never()).save(any());
    }

    // ── sendExpiryReminders ────────────────────────────────

    @Test
    @DisplayName("sendExpiryReminders() — sends reminders")
    void sendExpiryReminders() {
        when(subscriptionRepository.findByStatusAndEndDateBetween(eq(SubscriptionStatus.ACTIVE), any(), any()))
                .thenReturn(List.of(activeSubscription));
        
        paymentService.sendExpiryReminders();
        
        verify(notifClient).sendSubscriptionExpiryNotification(100L, activeSubscription.getEndDate());
    }

    // ── getAllSubscriptions ────────────────────────────────

    @Test
    @DisplayName("getAllSubscriptions() — returns list")
    void getAllSubscriptions() {
        when(subscriptionRepository.findAll()).thenReturn(List.of(activeSubscription));
        List<UserSubscription> res = paymentService.getAllSubscriptions();
        assertThat(res).hasSize(1);
    }
    // ── Missing Coverage Tests ─────────────────────────────

    @Test
    @DisplayName("verifyPayment() — email sending exception")
    void verifyPayment_emailException() throws Exception {
        VerifyPaymentRequest req = new VerifyPaymentRequest();
        req.setRazorpayOrderId("order_test123");
        req.setRazorpayPaymentId("pay_test123");
        
        String data = "order_test123|pay_test123";
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("test_secret".getBytes(), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        req.setRazorpaySignature(sb.toString());

        when(transactionRepository.findByRazorpayOrderId("order_test123")).thenReturn(Optional.of(transaction));
        when(subscriptionRepository.findByUserIdAndStatus(100L, SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(UserSubscription.class))).thenReturn(activeSubscription);

        when(authClient.getUserById(eq(100L), anyString())).thenThrow(new RuntimeException("Auth service down"));

        VerifyPaymentResponse res = paymentService.verifyPayment(100L, req, "token");
        assertThat(res.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("cancelSubscription() — email sending exception")
    void cancelSubscription_emailException() {
        when(subscriptionRepository.findByUserIdAndStatus(100L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSubscription));
        
        when(authClient.getUserById(eq(100L), anyString())).thenThrow(new RuntimeException("Auth service down"));

        paymentService.cancelSubscription(100L, "token");
        assertThat(activeSubscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancelSubscription() — missing email in profile")
    void cancelSubscription_missingEmail() {
        when(subscriptionRepository.findByUserIdAndStatus(100L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSubscription));
        
        java.util.Map<String, Object> mockProfile = new java.util.HashMap<>();
        java.util.Map<String, String> dataMap = new java.util.HashMap<>();
        mockProfile.put("data", dataMap); // No email
        
        when(authClient.getUserById(eq(100L), anyString())).thenReturn(mockProfile);

        paymentService.cancelSubscription(100L, "token");
        assertThat(activeSubscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }
    
    @Test
    @DisplayName("handleRazorpayWebhook() — unhandled event")
    void handleRazorpayWebhook_unhandledEvent() throws Exception {
        String payload = "{\"event\":\"unknown.event\",\"id\":\"evt_5\",\"payload\":{}}";
        
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("webhook_secret".getBytes(), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        String sig = sb.toString();

        when(webhookLogRepository.findByRazorpayEventId("evt_5")).thenReturn(Optional.empty());
        when(webhookLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        paymentService.handleRazorpayWebhook(payload, sig);

        verify(webhookLogRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("handleRazorpayWebhook() — payload parsing exception")
    void handleRazorpayWebhook_payloadException() throws Exception {
        String payload = "invalid_json";
        
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("webhook_secret".getBytes(), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        String sig = sb.toString();

        assertThatThrownBy(() ->
                paymentService.handleRazorpayWebhook(payload, sig))
                .isInstanceOf(org.json.JSONException.class);
    }

    @Test
    @DisplayName("hmacSHA256() — exception on null secret")
    void hmacSHA256_exception() {
        ReflectionTestUtils.setField(paymentService, "webhookSecret", null);
        
        assertThatThrownBy(() ->
                paymentService.handleRazorpayWebhook("{\"event\":\"test\"}", "signature"))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("Signature generation failed");
    }
}
