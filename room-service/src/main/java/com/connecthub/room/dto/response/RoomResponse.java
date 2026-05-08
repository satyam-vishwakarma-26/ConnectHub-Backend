package com.connecthub.room.dto.response;

import com.connecthub.room.entity.Room;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomResponse {

    private Long id;
    private String name;
    private String description;
    private String type;
    private Long createdById;
    private String avatarUrl;
    private Boolean isPrivate;
    private Integer maxMembers;
    private String inviteCode;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private Integer memberCount;
    private Integer unreadCount;

    // Populated on room detail view
    private List<RoomMemberResponse> members;

    public static RoomResponse from(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .description(room.getDescription())
                .type(room.getType().name())
                .createdById(room.getCreatedById())
                .avatarUrl(room.getAvatarUrl())
                .isPrivate(room.getIsPrivate())
                .maxMembers(room.getMaxMembers())
                .inviteCode(room.getInviteCode())
                .lastMessageAt(room.getLastMessageAt())
                .createdAt(room.getCreatedAt())
                .memberCount(room.getMembers().size())
                .unreadCount(0)
                .build();
    }

    public static RoomResponse from(Room room, Long userId) {
        RoomResponse r = from(room);
        room.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId))
                .findFirst()
                .ifPresent(m -> r.setUnreadCount(m.getUnreadCount() != null ? m.getUnreadCount() : 0));
        return r;
    }

    public static RoomResponse fromWithMembers(Room room,
                                                List<RoomMemberResponse> members) {
        RoomResponse r = from(room);
        r.setMembers(members);
        return r;
    }
}
