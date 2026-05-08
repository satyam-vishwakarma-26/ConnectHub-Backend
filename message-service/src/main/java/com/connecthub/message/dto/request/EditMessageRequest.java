package com.connecthub.message.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EditMessageRequest {

    @NotBlank(message = "Content is required")
    @Size(max = 4000, message = "Message content must not exceed 4000 characters")
    private String content;
}
