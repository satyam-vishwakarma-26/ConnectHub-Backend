package com.connecthub.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAnalyticsDTO {
    private Long totalTransactions;
    private Long successfulTransactions;
    private Long failedTransactions;
    private Double totalRevenue;
    private Long freeUsers;
    private Long proUsers;
    private LocalDateTime generatedAt;
}
