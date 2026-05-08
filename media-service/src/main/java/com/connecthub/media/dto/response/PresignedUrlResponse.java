package com.connecthub.media.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlResponse {
    private Long   mediaId;
    private String presignedUrl;
    private long   expiryHours;
    private String filename;
}
