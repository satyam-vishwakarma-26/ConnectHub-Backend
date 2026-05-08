package com.connecthub.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Shared email event payload published by payment-service to RabbitMQ.
 * Must be deserialized by auth-service's EmailConsumer — field names must match
 * the auth-service EmailEvent exactly (Jackson maps by field name).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEmailEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Maps to EmailEvent.EmailType enum in auth-service consumer. */
    private String type;          // "SUBSCRIPTION_ACTIVATED" | "SUBSCRIPTION_CANCELLED"

    private String toEmail;
    private String username;

    // Payment-specific fields (used in receipt / cancellation templates)
    private Double  amount;
    private String  currency;
    private String  planName;
    private String  transactionId;
    private String  subscriptionId;
    private String  razorpayPaymentId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime cancelledAt;

    public static class Type {
        public static final String SUBSCRIPTION_ACTIVATED  = "SUBSCRIPTION_ACTIVATED";
        public static final String SUBSCRIPTION_CANCELLED  = "SUBSCRIPTION_CANCELLED";
    }
}
