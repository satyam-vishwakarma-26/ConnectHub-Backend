package com.connecthub.notification.dto.response;

import com.connecthub.notification.entity.Notification;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private Long recipientId;
    private Long actorId;
    private String type;
    private String title;
    private String message;
    private Long roomId;
    private Long messageId;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .recipientId(n.getRecipientId())
                .actorId(n.getActorId())
                .type(n.getType().name())
                .title(n.getTitle())
                .message(n.getMessage())
                .roomId(n.getRoomId())
                .messageId(n.getMessageId())
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
