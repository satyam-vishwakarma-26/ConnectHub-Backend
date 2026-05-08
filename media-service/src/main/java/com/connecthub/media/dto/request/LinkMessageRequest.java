package com.connecthub.media.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LinkMessageRequest {

    @NotNull(message = "messageId is required")
    private Long messageId;
}
