package com.connecthub.payment.dto;

import com.connecthub.payment.entity.enums.PlanName;
import com.connecthub.payment.entity.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistoryDTO {
    private Long transactionId;
    private PlanName planName;
    private Double amount;
    private String currency;
    private TransactionStatus status;
    private String paymentMethod;
    private String razorpayPaymentId;
    private LocalDateTime createdAt;
}
