package com.connecthub.payment.entity;

import com.connecthub.payment.entity.enums.PlanName;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private PlanName planName;

    @Column(nullable = false)
    private Double price;

    @Builder.Default
    private String currency = "INR";

    @Builder.Default
    private String billingCycle = "MONTHLY";

    // Feature Limits — -1 means unlimited
    private Integer maxRooms;
    private Integer maxMembersPerRoom;
    private Integer maxFileSizeMb;
    private Integer messageHistoryDays;
    private Integer maxDevices;

    // Feature Flags
    @Builder.Default
    private Boolean customRoomAvatar = false;

    @Builder.Default
    private Boolean priorityNotifications = false;

    @Builder.Default
    private Boolean readReceipts = false;

    @Builder.Default
    private Boolean messageReactions = false;

    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
