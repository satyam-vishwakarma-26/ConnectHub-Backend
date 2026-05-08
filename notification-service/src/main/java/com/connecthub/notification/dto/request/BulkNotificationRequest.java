package com.connecthub.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class BulkNotificationRequest {
    @NotEmpty
    private List<Long> recipientIds;
    private Long actorId;
    @NotBlank private String type;
    @NotBlank private String title;
    @NotBlank private String message;
    private Long roomId;
    private Long messageId;
}
