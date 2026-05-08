package com.connecthub.message.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateDeliveryStatusRequest {

    /**
     * Target delivery status: DELIVERED or READ.
     * Transitions only go forward — READ cannot revert to DELIVERED.
     */
    @NotBlank(message = "status is required")
    private String status;
}
