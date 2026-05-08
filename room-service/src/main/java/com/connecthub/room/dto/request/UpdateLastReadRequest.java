package com.connecthub.room.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateLastReadRequest {

    // Timestamp of the latest message the user has read
    @NotNull(message = "readAt timestamp is required")
    private LocalDateTime readAt;
}
