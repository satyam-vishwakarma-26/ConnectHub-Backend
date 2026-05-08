package com.connecthub.payment.entity;

import com.connecthub.payment.entity.enums.PlanName;
import com.connecthub.payment.entity.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_subscriptions", indexes = {
        @Index(name = "idx_sub_user_id",   columnList = "userId"),
        @Index(name = "idx_sub_status",    columnList = "status"),
        @Index(name = "idx_sub_end_date",  columnList = "endDate")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long subscriptionId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanName planName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    private String razorpayOrderId;
    private String razorpayPaymentId;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    private LocalDateTime nextBillingDate;
    private LocalDateTime cancelledAt;

    @Builder.Default
    private Boolean autoRenew = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
