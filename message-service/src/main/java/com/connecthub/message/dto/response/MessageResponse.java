package com.connecthub.message.dto.response;

import com.connecthub.message.entity.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    private Long   id;
    private Long   roomId;
    private Long   senderId;
    private Long   recipientId;
    private String content;
    private String type;
    private String mediaUrl;
    private String mediaFilename;
    private Long   mediaSizeKb;
    private Long   replyToMessageId;
    private Boolean isEdited;
    private Boolean isDeleted;
    private Boolean isPinned;
    private String  deliveryStatus;
    private Map<String, Long> reactions;
    private LocalDateTime sentAt;
    private LocalDateTime editedAt;

    public static MessageResponse from(Message m) {
        return MessageResponse.builder()
                .id(m.getId())
                .roomId(m.getRoomId())
                .senderId(m.getSenderId())
                .recipientId(m.getRecipientId())
                .content(m.getContent())
                .type(m.getType().name())
                .mediaUrl(m.getMediaUrl())
                .mediaFilename(m.getMediaFilename())
                .mediaSizeKb(m.getMediaSizeKb())
                .replyToMessageId(m.getReplyToMessageId())
                .isEdited(m.getIsEdited())
                .isDeleted(m.getIsDeleted())
                .isPinned(m.getIsPinned())
                .deliveryStatus(m.getDeliveryStatus().name())
                .sentAt(m.getSentAt())
                .editedAt(m.getEditedAt())
                .build();
    }
}
