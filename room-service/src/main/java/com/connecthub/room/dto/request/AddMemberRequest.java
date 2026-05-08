package com.connecthub.room.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddMemberRequest {

    @NotNull(message = "userId is required")
    private Long userId;

    // Optional role override — defaults to MEMBER
    private String role;
}
