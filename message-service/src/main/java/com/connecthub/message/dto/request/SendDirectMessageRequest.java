package com.connecthub.message.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendDirectMessageRequest {

    @NotNull(message = "recipientId is required")
    private Long recipientId;

    @Size(max = 4000, message = "Message content must not exceed 4000 characters")
    private String content;

    /**
     * Message type: TEXT | IMAGE | FILE | REACTION | SYSTEM.
     * Defaults to TEXT if omitted.
     */
    private String type;

    @Size(max = 1000, message = "mediaUrl must not exceed 1000 characters")
    private String mediaUrl;

    @Size(max = 300, message = "mediaFilename must not exceed 300 characters")
    private String mediaFilename;

    private Long mediaSizeKb;

    private Long replyToMessageId;
}
