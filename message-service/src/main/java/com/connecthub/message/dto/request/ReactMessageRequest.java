package com.connecthub.message.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReactMessageRequest {

    @NotBlank(message = "emoji is required")
    @Size(max = 50, message = "emoji must not exceed 50 characters")
    private String emoji;
}
