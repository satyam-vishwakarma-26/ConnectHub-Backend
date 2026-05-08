package com.connecthub.auth.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Message payload published to RabbitMQ for async email processing.
 * Also used to deserialize PaymentEmailEvent JSON from payment-service
 * (field names are aligned intentionally).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private EmailType type;
    private String toEmail;
    private String username;

    // Password reset
    private String otp;

    // Admin action
    private String reason;

    // Payment / Subscription
    private Double        amount;
    private String        currency;
    private String        planName;
    private String        transactionId;
    private String        subscriptionId;
    private String        razorpayPaymentId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime cancelledAt;

    public enum EmailType {
        OTP_RESET,
        REGISTRATION_OTP,
        WELCOME,
        ACCOUNT_SUSPENDED,
        ACCOUNT_DELETED,
        ACCOUNT_DELETION_OTP,
        ACCOUNT_SELF_DELETED,
        SUBSCRIPTION_ACTIVATED,
        SUBSCRIPTION_CANCELLED
    }
}

