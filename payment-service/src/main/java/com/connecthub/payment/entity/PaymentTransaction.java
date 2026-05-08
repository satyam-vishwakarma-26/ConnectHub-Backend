package com.connecthub.payment.entity;

import com.connecthub.payment.entity.enums.PlanName;
import com.connecthub.payment.entity.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_txn_user_id",        columnList = "userId"),
        @Index(name = "idx_txn_order_id",        columnList = "razorpayOrderId"),
        @Index(name = "idx_txn_payment_id",      columnList = "razorpayPaymentId"),
        @Index(name = "idx_txn_status",          columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long transactionId;

    @Column(nullable = false)
    private Long userId;

    private Long subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanName planName;

    @Column(nullable = false)
    private String razorpayOrderId;

    private String razorpayPaymentId;
    private String razorpaySignature;

    @Column(nullable = false)
    private Double amount;

    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    private String paymentMethod;
    private String failureReason;
    private String refundId;
    private LocalDateTime refundedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
