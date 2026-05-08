package com.connecthub.room.controller;

import com.connecthub.room.dto.request.*;
import com.connecthub.room.dto.response.ApiResponse;
import com.connecthub.room.dto.response.RoomMemberResponse;
import com.connecthub.room.dto.response.RoomResponse;
import com.connecthub.room.security.AuthenticatedUser;
import com.connecthub.room.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
@Tag(name = "Rooms", description = "Room CRUD, membership, roles, mute, invite, unread")
@SecurityRequirement(name = "bearerAuth")
public class RoomController {

    private final RoomService roomService;

    // ────────────────────────────────────────────────────────
    // ROOM CRUD
    // ────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Create a GROUP room or start a DM")
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(
            @AuthenticationPrincipal AuthenticatedUser me,
            @Valid @RequestBody CreateRoomRequest request) {

        RoomResponse room = roomService.createRoom(me.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Room created", room));
    }

    @GetMapping("/{roomId}")
    @Operation(summary = "Get room details with member list")
    public ResponseEntity<ApiResponse<RoomResponse>> getRoomById(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId) {

        RoomResponse room = roomService.getRoomById(roomId, me.getUserId());
        return ResponseEntity.ok(ApiResponse.success(room));
    }

    @GetMapping("/my")
    @Operation(summary = "Get all rooms the current user belongs to")
    public ResponseEntity<ApiResponse<List<RoomResponse>>> getMyRooms(
            @AuthenticationPrincipal AuthenticatedUser me) {

        List<RoomResponse> rooms = roomService.getRoomsByUser(me.getUserId());
        return ResponseEntity.ok(ApiResponse.success(rooms));
    }

    @PutMapping("/{roomId}")
    @Operation(summary = "Update room settings — Room Admin only")
    public ResponseEntity<ApiResponse<RoomResponse>> updateRoom(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId,
            @Valid @RequestBody UpdateRoomRequest request) {

        RoomResponse room = roomService.updateRoom(roomId, me.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Room updated", room));
    }

    @DeleteMapping("/{roomId}")
    @Operation(summary = "Delete room — creator only")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId) {

        roomService.deleteRoom(roomId, me.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Room deleted", null));
    }

    // ────────────────────────────────────────────────────────
    // JOIN / LEAVE / INVITE
    // ────────────────────────────────────────────────────────

    @PostMapping("/join/{inviteCode}")
    @Operation(summary = "Join a GROUP room using an invite code")
    public ResponseEntity<ApiResponse<RoomResponse>> joinByInviteCode(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable String inviteCode) {

        RoomResponse room = roomService.joinRoomByInviteCode(inviteCode, me.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Joined room", room));
    }

    @PostMapping("/{roomId}/leave")
    @Operation(summary = "Leave a room")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId) {

        roomService.leaveRoom(roomId, me.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Left room", null));
    }

    @PostMapping("/{roomId}/invite/regenerate")
    @Operation(summary = "Regenerate invite code — Room Admin only")
    public ResponseEntity<ApiResponse<Map<String, String>>> regenerateInviteCode(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId) {

        String newCode = roomService.regenerateInviteCode(roomId, me.getUserId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("inviteCode", newCode)));
    }

    // ────────────────────────────────────────────────────────
    // MEMBER MANAGEMENT
    // ────────────────────────────────────────────────────────

    @GetMapping("/{roomId}/members")
    @Operation(summary = "Get all members of a room")
    public ResponseEntity<ApiResponse<List<RoomMemberResponse>>> getMembers(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId) {

        List<RoomMemberResponse> members = roomService.getMembers(roomId, me.getUserId());
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @PostMapping("/{roomId}/members")
    @Operation(summary = "Add a member to a room — Room Admin only")
    public ResponseEntity<ApiResponse<RoomMemberResponse>> addMember(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId,
            @Valid @RequestBody AddMemberRequest request) {

        RoomMemberResponse member = roomService.addMember(roomId, me.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Member added", member));
    }

    @DeleteMapping("/{roomId}/members/{userId}")
    @Operation(summary = "Remove a member from a room — Room Admin only")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId,
            @PathVariable Long userId) {

        roomService.removeMember(roomId, userId, me.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Member removed", null));
    }

    @PutMapping("/{roomId}/members/{userId}/role")
    @Operation(summary = "Change member role — Room Admin only")
    public ResponseEntity<ApiResponse<RoomMemberResponse>> updateMemberRole(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {

        RoomMemberResponse member = roomService.updateMemberRole(
                roomId, userId, me.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Role updated", member));
    }

    @PutMapping("/{roomId}/members/{userId}/mute")
    @Operation(summary = "Mute a member — Room Admin only")
    public ResponseEntity<ApiResponse<RoomMemberResponse>> muteMember(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId,
            @PathVariable Long userId) {

        RoomMemberResponse member = roomService.muteMember(roomId, userId, me.getUserId(), true);
        return ResponseEntity.ok(ApiResponse.success("Member muted", member));
    }

    @PutMapping("/{roomId}/members/{userId}/unmute")
    @Operation(summary = "Unmute a member — Room Admin only")
    public ResponseEntity<ApiResponse<RoomMemberResponse>> unmuteMember(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId,
            @PathVariable Long userId) {

        RoomMemberResponse member = roomService.muteMember(roomId, userId, me.getUserId(), false);
        return ResponseEntity.ok(ApiResponse.success("Member unmuted", member));
    }

    // ────────────────────────────────────────────────────────
    // UNREAD TRACKING
    // ────────────────────────────────────────────────────────

    @PutMapping("/{roomId}/read")
    @Operation(summary = "Update last-read timestamp — triggers READ_RECEIPT in websocket-handler")
    public ResponseEntity<ApiResponse<Void>> updateLastRead(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId,
            @Valid @RequestBody UpdateLastReadRequest request) {

        roomService.updateLastRead(roomId, me.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Last read updated", null));
    }

    @GetMapping("/{roomId}/unread")
    @Operation(summary = "Get unread message count for current user in a room")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUnreadCount(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId,
            @RequestParam(required = false) String lastMessageAt) {

        LocalDateTime lma = lastMessageAt != null
                ? LocalDateTime.parse(lastMessageAt) : null;

        int count = roomService.getUnreadCount(roomId, me.getUserId(), lma);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("roomId", roomId, "unreadCount", count)));
    }

    // ────────────────────────────────────────────────────────
    // DM
    // ────────────────────────────────────────────────────────

    @PostMapping("/dm/{targetUserId}")
    @Operation(summary = "Get existing DM or create a new one with a user")
    public ResponseEntity<ApiResponse<RoomResponse>> getOrCreateDm(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long targetUserId) {

        RoomResponse room = roomService.getOrCreateDm(me.getUserId(), targetUserId);
        return ResponseEntity.ok(ApiResponse.success(room));
    }

    // ────────────────────────────────────────────────────────
    // INTERNAL — called by message-service and websocket-handler
    // ────────────────────────────────────────────────────────

    @PutMapping("/{roomId}/last-message")
    @Operation(summary = "Internal — update lastMessageAt timestamp (called by message-service)")
    public ResponseEntity<ApiResponse<Void>> updateLastMessageAt(
            @PathVariable Long roomId,
            @RequestParam String sentAt,
            @RequestParam(required = false) Long senderId) {

        roomService.updateLastMessageAt(roomId, LocalDateTime.parse(sentAt), senderId);
        return ResponseEntity.ok(ApiResponse.success("lastMessageAt updated", null));
    }

    // ────────────────────────────────────────────────────────
    // PLATFORM ADMIN
    // ────────────────────────────────────────────────────────

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Admin — list all rooms")
    public ResponseEntity<ApiResponse<List<RoomResponse>>> getAllRooms() {
        return ResponseEntity.ok(ApiResponse.success(roomService.getAllRooms()));
    }

    @DeleteMapping("/admin/{roomId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Admin — force delete any room")
    public ResponseEntity<ApiResponse<Void>> adminDeleteRoom(
            @PathVariable Long roomId) {

        roomService.adminDeleteRoom(roomId);
        return ResponseEntity.ok(ApiResponse.success("Room deleted by admin", null));
    }
}
