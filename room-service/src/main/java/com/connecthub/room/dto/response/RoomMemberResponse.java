package com.connecthub.room.dto.response;

import com.connecthub.room.entity.RoomMember;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomMemberResponse {

    private Long id;
    private Long roomId;
    private Long userId;
    private String role;
    private Boolean isMuted;
    private LocalDateTime lastReadAt;
    private LocalDateTime joinedAt;

    public static RoomMemberResponse from(RoomMember member) {
        return RoomMemberResponse.builder()
                .id(member.getId())
                .roomId(member.getRoom().getId())
                .userId(member.getUserId())
                .role(member.getRole().name())
                .isMuted(member.getIsMuted())
                .lastReadAt(member.getLastReadAt())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}
