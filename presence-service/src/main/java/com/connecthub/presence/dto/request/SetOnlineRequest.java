package com.connecthub.presence.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetOnlineRequest {

    @NotNull(message = "userId is required")
    private Long userId;

    @NotBlank(message = "sessionId is required")
    private String sessionId;

    // WEB | MOBILE | DESKTOP — defaults to WEB
    private String deviceType = "WEB";

    private String ipAddress;

    // Optional initial status — defaults to ONLINE
    private String status = "ONLINE";
}
