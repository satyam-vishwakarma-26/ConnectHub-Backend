package com.connecthub.payment.dto;

import com.connecthub.payment.entity.enums.PlanName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPaymentResponse {
    private boolean success;
    private String message;
    private String subscriptionId;
    private PlanName planName;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String transactionId;
}
