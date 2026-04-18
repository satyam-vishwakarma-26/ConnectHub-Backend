package com.connecthub.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateStatusRequest {

    @NotBlank(message = "Status is required")
    private String status;  // ONLINE | AWAY | DND | INVISIBLE
}
