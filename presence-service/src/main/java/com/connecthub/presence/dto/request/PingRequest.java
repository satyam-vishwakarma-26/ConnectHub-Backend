package com.connecthub.presence.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PingRequest {
    @NotBlank(message = "sessionId is required")
    private String sessionId;
}
