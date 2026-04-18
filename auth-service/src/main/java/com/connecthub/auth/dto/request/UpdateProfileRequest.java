package com.connecthub.auth.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 3, max = 50, message = "Username must be 3–50 characters")
    private String username;

    @Size(max = 100, message = "Full name max 100 characters")
    private String fullName;

    @Size(max = 300, message = "Bio max 300 characters")
    private String bio;

    @Size(max = 500, message = "Avatar URL max 500 characters")
    private String avatarUrl;
}
