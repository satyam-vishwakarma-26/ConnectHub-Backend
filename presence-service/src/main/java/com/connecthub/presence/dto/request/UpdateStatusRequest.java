package com.connecthub.presence.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateStatusRequest {
    @NotBlank(message = "status is required")
    private String status;        // ONLINE | AWAY | DND | INVISIBLE

    private String customMessage; // optional status message
}
