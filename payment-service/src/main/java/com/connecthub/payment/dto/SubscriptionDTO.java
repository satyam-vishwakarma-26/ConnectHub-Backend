package com.connecthub.payment.dto;

import com.connecthub.payment.entity.SubscriptionPlan;
import com.connecthub.payment.entity.enums.PlanName;
import com.connecthub.payment.entity.enums.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDTO {
    private Long subscriptionId;
    private Long userId;
    private PlanName planName;
    private SubscriptionStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime nextBillingDate;
    private Boolean autoRenew;
    private SubscriptionPlan planDetails;

    /** True only when planName == PRO and status == ACTIVE */
    private boolean isProUser;
}
