package com.connecthub.media.dto.response;

import com.connecthub.media.entity.MediaFile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaFileResponse {

    private Long   id;
    private Long   uploaderId;
    private Long   roomId;
    private Long   messageId;
    private String filename;
    private String originalName;
    private String url;
    private String thumbnailUrl;
    private String mimeType;
    private Long   sizeKb;
    private Integer width;
    private Integer height;
    private String mediaType;
    private LocalDateTime uploadedAt;

    public static MediaFileResponse from(MediaFile m) {
        return MediaFileResponse.builder()
                .id(m.getId())
                .uploaderId(m.getUploaderId())
                .roomId(m.getRoomId())
                .messageId(m.getMessageId())
                .filename(m.getFilename())
                .originalName(m.getOriginalName())
                .url(m.getUrl())
                .thumbnailUrl(m.getThumbnailUrl())
                .mimeType(m.getMimeType())
                .sizeKb(m.getSizeKb())
                .width(m.getWidth())
                .height(m.getHeight())
                .mediaType(m.getMediaType().name())
                .uploadedAt(m.getUploadedAt())
                .build();
    }
}
