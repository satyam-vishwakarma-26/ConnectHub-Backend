package com.connecthub.notification.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendEmailRequest {
    @NotBlank @Email private String to;
    @NotBlank private String subject;
    @NotBlank private String body;
    private boolean html = false;
}
