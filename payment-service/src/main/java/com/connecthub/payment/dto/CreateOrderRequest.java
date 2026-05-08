package com.connecthub.payment.dto;

import com.connecthub.payment.entity.enums.PlanName;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateOrderRequest {

    @NotNull(message = "planName is required")
    private PlanName planName;

    private String billingCycle = "MONTHLY";
}
