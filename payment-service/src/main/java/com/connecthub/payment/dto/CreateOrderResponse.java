package com.connecthub.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderResponse {
    private String razorpayOrderId;
    private String razorpayKeyId;
    private Double amount;
    private String currency;
    private String planName;
    private String receipt;
}
