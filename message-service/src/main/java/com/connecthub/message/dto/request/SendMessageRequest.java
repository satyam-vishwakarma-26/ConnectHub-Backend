package com.connecthub.message.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotNull(message = "roomId is required")
    private Long roomId;

    /**
     * Text content — required for TEXT / REACTION / SYSTEM messages.
     * Optional for IMAGE / FILE (mediaUrl is the primary payload).
     */
    @Size(max = 4000, message = "Message content must not exceed 4000 characters")
    private String content;

    /**
     * Message type: TEXT | IMAGE | FILE | REACTION | SYSTEM.
     * Defaults to TEXT if omitted.
     */
    private String type;

    // ── Media fields (set when type = IMAGE or FILE) ──────
    @Size(max = 1000, message = "mediaUrl must not exceed 1000 characters")
    private String mediaUrl;

    @Size(max = 300, message = "mediaFilename must not exceed 300 characters")
    private String mediaFilename;

    private Long mediaSizeKb;

    // ── Threading ──────────────────────────────────────────
    private Long replyToMessageId;
}
