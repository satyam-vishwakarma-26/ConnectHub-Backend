package com.connecthub.presence.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class BulkPresenceRequest {
    @NotEmpty(message = "userIds list cannot be empty")
    private List<Long> userIds;
}
