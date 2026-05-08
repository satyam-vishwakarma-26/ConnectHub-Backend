package com.connecthub.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterFcmTokenRequest {
    @NotBlank private String token;
    private String deviceType = "MOBILE";
}
