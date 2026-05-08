package com.connecthub.payment.service.impl;

import com.connecthub.payment.client.AuthServiceClient;
import com.connecthub.payment.client.NotificationServiceClient;
import com.connecthub.payment.dto.*;
import com.connecthub.payment.entity.PaymentTransaction;
import com.connecthub.payment.entity.SubscriptionPlan;
import com.connecthub.payment.entity.UserSubscription;
import com.connecthub.payment.entity.WebhookLog;
import com.connecthub.payment.entity.enums.PlanName;
import com.connecthub.payment.entity.enums.SubscriptionStatus;
import com.connecthub.payment.entity.enums.TransactionStatus;
import com.connecthub.payment.exception.PaymentException;
import com.connecthub.payment.exception.SubscriptionNotFoundException;
import com.connecthub.payment.repository.*;
import com.connecthub.payment.dto.PaymentEmailEvent;
import com.connecthub.payment.service.PaymentEmailProducer;
import com.connecthub.payment.service.PaymentService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final RazorpayClient                razorpayClient;
    private final SubscriptionPlanRepository    planRepository;
    private final UserSubscriptionRepository    subscriptionRepository;
    private final PaymentTransactionRepository  transactionRepository;
    private final WebhookLogRepository          webhookLogRepository;
    private final AuthServiceClient             authClient;
    private final NotificationServiceClient     notifClient;
    private final PaymentEmailProducer          paymentEmailProducer;

    @Value("${razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret}")
    private String razorpayKeySecret;

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    // ── Plans ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPlan> getAllPlans() {
        return planRepository.findByIsActiveTrue();
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPlan getPlanByName(PlanName planName) {
        return planRepository.findByPlanName(planName)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + planName));
    }

    // ── Create Order ───────────────────────────────────────

    @Override
    public CreateOrderResponse createOrder(Long userId,
                                            CreateOrderRequest request,
                                            String token) throws RazorpayException {
        // Only PRO plan requires payment
        if (request.getPlanName() == PlanName.FREE) {
            throw new PaymentException("FREE plan requires no payment. You are already on it.");
        }

        // ₹199 = 19900 paise
        int amountInPaise = (int) (199.00 * 100);

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount",   amountInPaise);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt",  "rcpt_" + userId + "_" + System.currentTimeMillis());

        Order order = razorpayClient.orders.create(orderRequest);

        // Persist CREATED transaction
        PaymentTransaction transaction = PaymentTransaction.builder()
                .userId(userId)
                .planName(PlanName.PRO)
                .razorpayOrderId(order.get("id"))
                .amount(199.00)
                .currency("INR")
                .status(TransactionStatus.CREATED)
                .build();

        transactionRepository.save(transaction);
        log.info("Order created: userId={} orderId={}", userId, order.get("id").toString());

        return CreateOrderResponse.builder()
                .razorpayOrderId(order.get("id"))
                .razorpayKeyId(razorpayKeyId)
                .amount(199.00)
                .currency("INR")
                .planName("PRO")
                .receipt(order.get("receipt"))
                .build();
    }

    // ── Verify Payment ─────────────────────────────────────

    @Override
    public VerifyPaymentResponse verifyPayment(Long userId,
                                                VerifyPaymentRequest request,
                                                String token) {
        // 1. Verify HMAC-SHA256 signature
        String generated = hmacSHA256(
                request.getRazorpayOrderId() + "|" + request.getRazorpayPaymentId(),
                razorpayKeySecret);

        if (!generated.equals(request.getRazorpaySignature())) {
            throw new PaymentException("Invalid payment signature");
        }

        // 2. Find and update transaction
        PaymentTransaction transaction = transactionRepository
                .findByRazorpayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new PaymentException(
                        "Transaction not found for orderId: " + request.getRazorpayOrderId()));

        transaction.setRazorpayPaymentId(request.getRazorpayPaymentId());
        transaction.setRazorpaySignature(request.getRazorpaySignature());
        transaction.setStatus(TransactionStatus.SUCCESS);
        transactionRepository.save(transaction);

        // 3. Cancel any existing active subscription
        subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .ifPresent(existing -> {
                    existing.setStatus(SubscriptionStatus.CANCELLED);
                    existing.setCancelledAt(LocalDateTime.now());
                    subscriptionRepository.save(existing);
                    log.info("Previous subscription cancelled for userId={}", userId);
                });

        // 4. Create new PRO subscription (1 month)
        LocalDateTime now      = LocalDateTime.now();
        LocalDateTime endDate  = now.plusMonths(1);

        UserSubscription subscription = UserSubscription.builder()
                .userId(userId)
                .planName(PlanName.PRO)
                .status(SubscriptionStatus.ACTIVE)
                .razorpayOrderId(request.getRazorpayOrderId())
                .razorpayPaymentId(request.getRazorpayPaymentId())
                .startDate(now)
                .endDate(endDate)
                .nextBillingDate(endDate)
                .autoRenew(true)
                .build();

        subscription = subscriptionRepository.save(subscription);

        // 5. Link subscription to transaction
        transaction.setSubscriptionId(subscription.getSubscriptionId());
        transactionRepository.save(transaction);

        // 6. Notify success (fire-and-forget)
        notifClient.sendPaymentSuccessNotification(userId, 199.00, token);

        // 7. Send subscription receipt email via RabbitMQ
        try {
            java.util.Map<String, Object> userProfile = authClient.getUserById(userId, token);
            if (userProfile != null) {
                Object data = userProfile.get("data");
                String toEmail = null;
                String username = null;
                if (data instanceof java.util.Map<?, ?> dataMap) {
                    Object emailObj    = dataMap.get("email");
                    Object usernameObj = dataMap.get("username");
                    toEmail  = emailObj    != null ? emailObj.toString()    : null;
                    username = usernameObj != null ? usernameObj.toString() : null;
                }
                if (toEmail != null && !toEmail.isBlank()) {
                    PaymentEmailEvent emailEvent = PaymentEmailEvent.builder()
                            .type(PaymentEmailEvent.Type.SUBSCRIPTION_ACTIVATED)
                            .toEmail(toEmail)
                            .username(username)
                            .amount(199.00)
                            .currency("INR")
                            .planName("PRO")
                            .transactionId(String.valueOf(transaction.getTransactionId()))
                            .subscriptionId(String.valueOf(subscription.getSubscriptionId()))
                            .razorpayPaymentId(request.getRazorpayPaymentId())
                            .startDate(now)
                            .endDate(endDate)
                            .build();
                    paymentEmailProducer.publishEmailEvent(emailEvent);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to queue subscription receipt email for userId={}: {}", userId, e.getMessage());
        }

        log.info("Payment verified and subscription activated: userId={} subscriptionId={}",
                  userId, subscription.getSubscriptionId());

        return VerifyPaymentResponse.builder()
                .success(true)
                .message("Payment successful! Welcome to PRO plan.")
                .planName(PlanName.PRO)
                .startDate(now)
                .endDate(endDate)
                .transactionId(String.valueOf(transaction.getTransactionId()))
                .subscriptionId(String.valueOf(subscription.getSubscriptionId()))
                .build();
    }

    // ── Current Subscription ───────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public SubscriptionDTO getCurrentSubscription(Long userId) {
        return subscriptionRepository
                .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .map(sub -> {
                    SubscriptionPlan plan = getPlanByName(PlanName.PRO);
                    return SubscriptionDTO.builder()
                            .subscriptionId(sub.getSubscriptionId())
                            .userId(sub.getUserId())
                            .planName(PlanName.PRO)
                            .status(SubscriptionStatus.ACTIVE)
                            .startDate(sub.getStartDate())
                            .endDate(sub.getEndDate())
                            .nextBillingDate(sub.getNextBillingDate())
                            .autoRenew(sub.getAutoRenew())
                            .planDetails(plan)
                            .isProUser(true)
                            .build();
                })
                .orElseGet(() -> {
                    SubscriptionPlan freePlan = getPlanByName(PlanName.FREE);
                    return SubscriptionDTO.builder()
                            .userId(userId)
                            .planName(PlanName.FREE)
                            .status(SubscriptionStatus.EXPIRED)
                            .isProUser(false)
                            .planDetails(freePlan)
                            .build();
                });
    }

    // ── Plan Limits ────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PlanLimitsDTO getUserPlanLimits(Long userId) {
        boolean isProUser = subscriptionRepository
                .existsByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);

        SubscriptionPlan plan = getPlanByName(isProUser ? PlanName.PRO : PlanName.FREE);

        return PlanLimitsDTO.builder()
                .planName(plan.getPlanName())
                .maxRooms(plan.getMaxRooms())
                .maxMembersPerRoom(plan.getMaxMembersPerRoom())
                .maxFileSizeMb(plan.getMaxFileSizeMb())
                .messageHistoryDays(plan.getMessageHistoryDays())
                .maxDevices(plan.getMaxDevices())
                .readReceipts(plan.getReadReceipts())
                .messageReactions(plan.getMessageReactions())
                .customRoomAvatar(plan.getCustomRoomAvatar())
                .priorityNotifications(plan.getPriorityNotifications())
                .isProUser(isProUser)
                .build();
    }

    // ── Cancel Subscription ────────────────────────────────

    @Override
    public void cancelSubscription(Long userId, String token) {
        UserSubscription subscription = subscriptionRepository
                .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new SubscriptionNotFoundException(userId));

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(LocalDateTime.now());
        subscription.setAutoRenew(false);
        subscriptionRepository.save(subscription);

        // NOTE: User retains PRO access until endDate
        log.info("Subscription cancelled for userId={} — PRO access until {}", userId,
                  subscription.getEndDate());

        // Send cancellation email via RabbitMQ
        try {
            java.util.Map<String, Object> userProfile = authClient.getUserById(userId, token);
            if (userProfile != null) {
                Object data = userProfile.get("data");
                String toEmail = null;
                String username = null;
                if (data instanceof java.util.Map<?, ?> dataMap) {
                    Object emailObj    = dataMap.get("email");
                    Object usernameObj = dataMap.get("username");
                    toEmail  = emailObj    != null ? emailObj.toString()    : null;
                    username = usernameObj != null ? usernameObj.toString() : null;
                }
                if (toEmail != null && !toEmail.isBlank()) {
                    PaymentEmailEvent emailEvent = PaymentEmailEvent.builder()
                            .type(PaymentEmailEvent.Type.SUBSCRIPTION_CANCELLED)
                            .toEmail(toEmail)
                            .username(username)
                            .planName("PRO")
                            .subscriptionId(String.valueOf(subscription.getSubscriptionId()))
                            .endDate(subscription.getEndDate())
                            .cancelledAt(subscription.getCancelledAt())
                            .build();
                    paymentEmailProducer.publishEmailEvent(emailEvent);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to queue cancellation email for userId={}: {}", userId, e.getMessage());
        }
    }

    // ── Toggle Auto-Renew ──────────────────────────────────

    @Override
    public void toggleAutoRenew(Long userId, boolean autoRenew) {
        UserSubscription subscription = subscriptionRepository
                .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new SubscriptionNotFoundException(userId));

        subscription.setAutoRenew(autoRenew);
        subscriptionRepository.save(subscription);
        log.info("AutoRenew set to {} for userId={}", autoRenew, userId);
    }

    // ── Payment History ────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<PaymentHistoryDTO> getPaymentHistory(Long userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(t -> PaymentHistoryDTO.builder()
                        .transactionId(t.getTransactionId())
                        .planName(t.getPlanName())
                        .amount(t.getAmount())
                        .currency(t.getCurrency())
                        .status(t.getStatus())
                        .paymentMethod(t.getPaymentMethod())
                        .razorpayPaymentId(t.getRazorpayPaymentId())
                        .createdAt(t.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ── Webhook ────────────────────────────────────────────

    @Override
    public void handleRazorpayWebhook(String payload, String signature) {
        // 1. Verify webhook signature
        String generated = hmacSHA256(payload, webhookSecret);
        if (!generated.equals(signature)) {
            throw new PaymentException("Invalid webhook signature");
        }

        JSONObject json      = new JSONObject(payload);
        String     eventType = json.getString("event");
        String     eventId   = json.optString("id", null);

        // 2. Idempotency — skip if already processed
        if (eventId != null && webhookLogRepository.findByRazorpayEventId(eventId).isPresent()) {
            log.info("Webhook already processed: eventId={}", eventId);
            return;
        }

        // 3. Save webhook log
        WebhookLog webhookLog = WebhookLog.builder()
                .eventType(eventType)
                .razorpayEventId(eventId)
                .payload(payload)
                .status("RECEIVED")
                .build();
        webhookLog = webhookLogRepository.save(webhookLog);

        try {
            JSONObject payloadData = json.getJSONObject("payload");

            switch (eventType) {

                case "payment.captured" -> {
                    String paymentId = payloadData
                            .getJSONObject("payment").getJSONObject("entity")
                            .getString("id");
                    transactionRepository.findByRazorpayPaymentId(paymentId)
                            .ifPresent(t -> {
                                t.setStatus(TransactionStatus.SUCCESS);
                                transactionRepository.save(t);
                                log.info("Webhook: payment.captured paymentId={}", paymentId);
                            });
                }

                case "payment.failed" -> {
                    String orderId = payloadData
                            .getJSONObject("payment").getJSONObject("entity")
                            .getString("order_id");
                    String failReason = payloadData
                            .getJSONObject("payment").getJSONObject("entity")
                            .optString("error_description", "Unknown error");
                    transactionRepository.findByRazorpayOrderId(orderId)
                            .ifPresent(t -> {
                                t.setStatus(TransactionStatus.FAILED);
                                t.setFailureReason(failReason);
                                transactionRepository.save(t);
                                notifClient.sendPaymentFailureNotification(t.getUserId(), null);
                                log.info("Webhook: payment.failed orderId={}", orderId);
                            });
                }

                case "refund.created" -> {
                    String paymentId = payloadData
                            .getJSONObject("refund").getJSONObject("entity")
                            .getString("payment_id");
                    String refundId = payloadData
                            .getJSONObject("refund").getJSONObject("entity")
                            .getString("id");
                    transactionRepository.findByRazorpayPaymentId(paymentId)
                            .ifPresent(t -> {
                                t.setStatus(TransactionStatus.REFUNDED);
                                t.setRefundId(refundId);
                                t.setRefundedAt(LocalDateTime.now());
                                transactionRepository.save(t);
                                log.info("Webhook: refund.created refundId={}", refundId);
                            });
                }

                default -> log.warn("Unhandled Razorpay webhook event: {}", eventType);
            }

            // 4. Mark processed
            webhookLog.setStatus("PROCESSED");
            webhookLog.setProcessedAt(LocalDateTime.now());

        } catch (Exception e) {
            webhookLog.setStatus("FAILED");
            log.error("Webhook processing error for event {}: {}", eventType, e.getMessage());
        }

        webhookLogRepository.save(webhookLog);
    }

    // ── Scheduled: Expire Subscriptions ───────────────────

    @Override
    @Scheduled(cron = "0 0 0 * * *")   // midnight every day
    public void processExpiredSubscriptions() {
        List<UserSubscription> expired = subscriptionRepository
                .findByStatusAndEndDateBefore(SubscriptionStatus.ACTIVE, LocalDateTime.now());

        int count = 0;
        for (UserSubscription sub : expired) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(sub);
            log.info("Subscription expired for userId={}", sub.getUserId());
            count++;
        }
        log.info("Processed {} expired subscriptions", count);
    }

    // ── Scheduled: Expiry Reminders ────────────────────────

    @Override
    @Scheduled(cron = "0 0 10 * * *")  // 10am every day
    public void sendExpiryReminders() {
        LocalDateTime now      = LocalDateTime.now();
        LocalDateTime threeDays = now.plusDays(3);

        List<UserSubscription> expiringSoon = subscriptionRepository
                .findByStatusAndEndDateBetween(SubscriptionStatus.ACTIVE, now, threeDays);

        for (UserSubscription sub : expiringSoon) {
            notifClient.sendSubscriptionExpiryNotification(sub.getUserId(), sub.getEndDate());
            log.info("Expiry reminder sent for userId={} expiry={}", sub.getUserId(), sub.getEndDate());
        }
    }

    // ── Admin: Analytics ───────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PaymentAnalyticsDTO getPaymentAnalytics() {
        long totalTransactions       = transactionRepository.count();
        long successfulTransactions  = transactionRepository.countByStatus(TransactionStatus.SUCCESS);
        long failedTransactions      = transactionRepository.countByStatus(TransactionStatus.FAILED);
        Double totalRevenue          = transactionRepository.sumAmountByStatus(TransactionStatus.SUCCESS);
        long proUsers                = subscriptionRepository
                .countByStatusAndPlanName(SubscriptionStatus.ACTIVE, PlanName.PRO);
        long totalUsers              = totalTransactions > 0 ? totalTransactions : 0;
        long freeUsers               = Math.max(0, totalUsers - proUsers);

        return PaymentAnalyticsDTO.builder()
                .totalTransactions(totalTransactions)
                .successfulTransactions(successfulTransactions)
                .failedTransactions(failedTransactions)
                .totalRevenue(totalRevenue != null ? totalRevenue : 0.0)
                .freeUsers(freeUsers)
                .proUsers(proUsers)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSubscription> getAllSubscriptions() {
        return subscriptionRepository.findAll();
    }

    // ── Private: HMAC-SHA256 ───────────────────────────────

    private String hmacSHA256(String data, String secret) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new PaymentException("Signature generation failed: " + e.getMessage());
        }
    }
}
