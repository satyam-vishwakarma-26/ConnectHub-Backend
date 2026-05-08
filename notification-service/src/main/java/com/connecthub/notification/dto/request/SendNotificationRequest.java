package com.connecthub.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendNotificationRequest {

    @NotNull(message = "recipientId is required")
    private Long recipientId;

    private Long actorId;

    @NotBlank(message = "type is required")
    private String type;    // NEW_MESSAGE | MENTION | ROOM_INVITE | SYSTEM

    @NotBlank(message = "title is required")
    private String title;

    @NotBlank(message = "message is required")
    private String message;

    private Long roomId;
    private Long messageId;
}
