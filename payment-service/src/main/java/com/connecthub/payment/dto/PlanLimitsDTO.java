package com.connecthub.payment.dto;

import com.connecthub.payment.entity.enums.PlanName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanLimitsDTO {
    private PlanName planName;
    private Integer maxRooms;
    private Integer maxMembersPerRoom;
    private Integer maxFileSizeMb;
    private Integer messageHistoryDays;
    private Integer maxDevices;
    private Boolean readReceipts;
    private Boolean messageReactions;
    private Boolean customRoomAvatar;
    private Boolean priorityNotifications;
    private boolean isProUser;
}
