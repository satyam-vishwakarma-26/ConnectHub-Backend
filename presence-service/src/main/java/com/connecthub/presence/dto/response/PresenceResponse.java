package com.connecthub.presence.dto.response;

import com.connecthub.presence.entity.UserPresence;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceResponse {

    private Long          userId;
    private String        status;
    private String        customMessage;
    private String        deviceType;
    private LocalDateTime connectedAt;
    private LocalDateTime lastPingAt;
    private String        sessionId;
    private Boolean       isOnline;

    public static PresenceResponse from(UserPresence p) {
        return PresenceResponse.builder()
                .userId(p.getUserId())
                .status(p.getStatus())
                .customMessage(p.getCustomMessage())
                .deviceType(p.getDeviceType())
                .connectedAt(p.getConnectedAt())
                .lastPingAt(p.getLastPingAt())
                .sessionId(p.getSessionId())
                .isOnline("ONLINE".equals(p.getStatus()) || "AWAY".equals(p.getStatus()))
                .build();
    }

    /** Offline stub — returned when no active session exists for a user */
    public static PresenceResponse offline(Long userId) {
        return PresenceResponse.builder()
                .userId(userId)
                .status("INVISIBLE")
                .isOnline(false)
                .build();
    }
}
