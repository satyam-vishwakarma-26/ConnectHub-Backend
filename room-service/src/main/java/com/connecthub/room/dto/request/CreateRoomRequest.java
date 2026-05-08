package com.connecthub.room.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRoomRequest {

    @NotBlank(message = "Room name is required")
    @Size(min = 2, max = 100, message = "Room name must be 2–100 characters")
    private String name;

    @Size(max = 300, message = "Description max 300 characters")
    private String description;

    // GROUP or DM — defaults to GROUP if null
    private String type;

    @Size(max = 500)
    private String avatarUrl;

    private Boolean isPrivate = false;

    @Min(value = 2, message = "Max members must be at least 2")
    private Integer maxMembers = 100;

    // For DM: the other user's ID
    private Long targetUserId;
}
