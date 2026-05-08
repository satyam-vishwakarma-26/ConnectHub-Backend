package com.connecthub.room.service;

import com.connecthub.room.dto.request.*;
import com.connecthub.room.dto.response.RoomMemberResponse;
import com.connecthub.room.dto.response.RoomResponse;

import java.time.LocalDateTime;
import java.util.List;

public interface RoomService {

    // ── Room CRUD ─────────────────────────────────────────
    RoomResponse createRoom(Long creatorId, CreateRoomRequest request);

    RoomResponse getRoomById(Long roomId, Long requesterId);

    List<RoomResponse> getRoomsByUser(Long userId);

    RoomResponse updateRoom(Long roomId, Long requesterId, UpdateRoomRequest request);

    void deleteRoom(Long roomId, Long requesterId);

    // ── Invite / Join / Leave ─────────────────────────────
    RoomResponse joinRoomByInviteCode(String inviteCode, Long userId);

    void leaveRoom(Long roomId, Long userId);

    String regenerateInviteCode(Long roomId, Long requesterId);

    // ── Member Management (Room Admin) ────────────────────
    RoomMemberResponse addMember(Long roomId, Long requesterId, AddMemberRequest request);

    void removeMember(Long roomId, Long targetUserId, Long requesterId);

    RoomMemberResponse updateMemberRole(Long roomId, Long targetUserId,
                                        Long requesterId, UpdateMemberRoleRequest request);

    RoomMemberResponse muteMember(Long roomId, Long targetUserId,
                                   Long requesterId, boolean mute);

    List<RoomMemberResponse> getMembers(Long roomId, Long requesterId);

    // ── Unread Tracking ───────────────────────────────────
    void updateLastRead(Long roomId, Long userId, UpdateLastReadRequest request);

    int getUnreadCount(Long roomId, Long userId, LocalDateTime lastMessageAt);

    // ── Last Message Timestamp (called by message-service) ─
    void updateLastMessageAt(Long roomId, LocalDateTime sentAt, Long senderId);

    // ── DM ────────────────────────────────────────────────
    RoomResponse getOrCreateDm(Long userId, Long targetUserId);

    // ── Admin ─────────────────────────────────────────────
    List<RoomResponse> getAllRooms();

    void adminDeleteRoom(Long roomId);
}
