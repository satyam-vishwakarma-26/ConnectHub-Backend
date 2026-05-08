package com.connecthub.room.service.impl;

import com.connecthub.room.dto.request.*;
import com.connecthub.room.dto.response.RoomMemberResponse;
import com.connecthub.room.dto.response.RoomResponse;
import com.connecthub.room.entity.Room;
import com.connecthub.room.entity.RoomMember;
import com.connecthub.room.exception.*;
import com.connecthub.room.repository.RoomMemberRepository;
import com.connecthub.room.repository.RoomRepository;
import com.connecthub.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RoomServiceImpl implements RoomService {

    private final RoomRepository     roomRepository;
    private final RoomMemberRepository memberRepository;

    // ── Create Room ────────────────────────────────────────

    @Override
    public RoomResponse createRoom(Long creatorId, CreateRoomRequest request) {
        Room.RoomType type = parseRoomType(request.getType());

        // DM rooms have special creation logic
        if (type == Room.RoomType.DM) {
            if (request.getTargetUserId() == null) {
                throw new BadRequestException("targetUserId is required for DM rooms.");
            }
            return getOrCreateDm(creatorId, request.getTargetUserId());
        }

        // GROUP room — name is required
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("Room name is required for GROUP rooms.");
        }

        Room room = Room.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(Room.RoomType.GROUP)
                .createdById(creatorId)
                .avatarUrl(request.getAvatarUrl())
                .isPrivate(request.getIsPrivate() != null && request.getIsPrivate())
                .maxMembers(request.getMaxMembers() != null ? request.getMaxMembers() : 100)
                .inviteCode(generateInviteCode())
                .build();

        room = roomRepository.save(room);

        // Creator becomes first ADMIN member
        addMemberInternal(room, creatorId, RoomMember.MemberRole.ADMIN);

        log.info("GROUP room created: id={} name={} by userId={}", room.getId(), room.getName(), creatorId);
        return RoomResponse.from(room);
    }

    // ── Get Room By Id ─────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public RoomResponse getRoomById(Long roomId, Long requesterId) {
        Room room = findRoom(roomId);
        assertMember(room, requesterId);

        List<RoomMemberResponse> members = memberRepository.findByRoomId(roomId)
                .stream()
                .map(RoomMemberResponse::from)
                .collect(Collectors.toList());

        RoomResponse r = RoomResponse.from(room, requesterId);
        r.setMembers(members);
        return r;
    }

    // ── Get Rooms By User ──────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> getRoomsByUser(Long userId) {
        return roomRepository.findRoomsByUserId(userId)
                .stream()
                .map(r -> RoomResponse.from(r, userId))
                .collect(Collectors.toList());
    }

    // ── Update Room ────────────────────────────────────────

    @Override
    public RoomResponse updateRoom(Long roomId, Long requesterId, UpdateRoomRequest request) {
        Room room = findRoom(roomId);
        assertAdmin(room, requesterId);

        if (request.getName()        != null) room.setName(request.getName());
        if (request.getDescription() != null) room.setDescription(request.getDescription());
        if (request.getAvatarUrl()   != null) room.setAvatarUrl(request.getAvatarUrl());
        if (request.getIsPrivate()   != null) room.setIsPrivate(request.getIsPrivate());
        if (request.getMaxMembers()  != null) {
            int currentCount = memberRepository.findByRoomId(roomId).size();
            if (request.getMaxMembers() < currentCount) {
                throw new BadRequestException(
                        "maxMembers cannot be less than current member count: " + currentCount);
            }
            room.setMaxMembers(request.getMaxMembers());
        }

        room = roomRepository.save(room);
        log.info("Room updated: id={}", roomId);
        return RoomResponse.from(room);
    }

    // ── Delete Room ────────────────────────────────────────

    @Override
    public void deleteRoom(Long roomId, Long requesterId) {
        Room room = findRoom(roomId);
        // Only the original creator or a platform admin (checked at controller level)
        if (!room.getCreatedById().equals(requesterId)) {
            throw new ForbiddenException("Only the room creator can delete this room.");
        }
        memberRepository.deleteByRoomId(roomId);
        roomRepository.delete(room);
        log.info("Room deleted: id={} by userId={}", roomId, requesterId);
    }

    // ── Join by Invite Code ────────────────────────────────

    @Override
    public RoomResponse joinRoomByInviteCode(String inviteCode, Long userId) {
        Room room = roomRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invalid invite code: " + inviteCode));

        if (room.getType() == Room.RoomType.DM) {
            throw new BadRequestException("Cannot join a DM room via invite code.");
        }

        // Already a member — just return the room
        if (memberRepository.existsByRoomIdAndUserId(room.getId(), userId)) {
            log.info("User {} already a member of room {}", userId, room.getId());
            return RoomResponse.from(room);
        }

        // Check capacity
        int count = memberRepository.findByRoomId(room.getId()).size();
        if (count >= room.getMaxMembers()) {
            throw new BadRequestException("Room has reached maximum member capacity.");
        }

        addMemberInternal(room, userId, RoomMember.MemberRole.MEMBER);
        log.info("User {} joined room {} via invite code", userId, room.getId());
        return RoomResponse.from(room, userId);
    }

    // ── Leave Room ─────────────────────────────────────────

    @Override
    public void leaveRoom(Long roomId, Long userId) {
        Room room = findRoom(roomId);

        if (room.getType() == Room.RoomType.DM) {
            throw new BadRequestException("You cannot leave a DM room.");
        }

        RoomMember member = memberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BadRequestException("You are not a member of this room."));

        // If the leaving user is the last admin, block unless they are the last member
        if (member.getRole() == RoomMember.MemberRole.ADMIN) {
            long adminCount = memberRepository.countAdmins(roomId);
            int totalCount  = memberRepository.findByRoomId(roomId).size();
            if (adminCount == 1 && totalCount > 1) {
                throw new BadRequestException(
                        "You are the last admin. Assign another admin before leaving.");
            }
        }

        memberRepository.delete(member);
        log.info("User {} left room {}", userId, roomId);
    }

    // ── Regenerate Invite Code ─────────────────────────────

    @Override
    public String regenerateInviteCode(Long roomId, Long requesterId) {
        Room room = findRoom(roomId);
        assertAdmin(room, requesterId);

        String newCode = generateInviteCode();
        room.setInviteCode(newCode);
        roomRepository.save(room);
        log.info("Invite code regenerated for room {}", roomId);
        return newCode;
    }

    // ── Add Member ─────────────────────────────────────────

    @Override
    public RoomMemberResponse addMember(Long roomId, Long requesterId, AddMemberRequest request) {
        Room room = findRoom(roomId);
        assertAdmin(room, requesterId);

        if (room.getType() == Room.RoomType.DM) {
            throw new BadRequestException("Cannot add members to a DM room.");
        }

        if (memberRepository.existsByRoomIdAndUserId(roomId, request.getUserId())) {
            throw new DuplicateResourceException("User is already a member of this room.");
        }

        int count = memberRepository.findByRoomId(roomId).size();
        if (count >= room.getMaxMembers()) {
            throw new BadRequestException("Room has reached maximum member capacity.");
        }

        RoomMember.MemberRole role = parseMemberRole(request.getRole());
        RoomMember member = addMemberInternal(room, request.getUserId(), role);
        log.info("User {} added to room {} by {}", request.getUserId(), roomId, requesterId);
        return RoomMemberResponse.from(member);
    }

    // ── Remove Member ──────────────────────────────────────

    @Override
    public void removeMember(Long roomId, Long targetUserId, Long requesterId) {
        Room room = findRoom(roomId);
        assertAdmin(room, requesterId);

        if (targetUserId.equals(requesterId)) {
            throw new BadRequestException("Use leaveRoom to remove yourself.");
        }

        RoomMember member = memberRepository.findByRoomIdAndUserId(roomId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User " + targetUserId + " is not a member of room " + roomId));

        memberRepository.delete(member);
        log.info("User {} removed from room {} by {}", targetUserId, roomId, requesterId);
    }

    // ── Update Member Role ─────────────────────────────────

    @Override
    public RoomMemberResponse updateMemberRole(Long roomId, Long targetUserId,
                                                Long requesterId,
                                                UpdateMemberRoleRequest request) {
        Room room = findRoom(roomId);
        assertAdmin(room, requesterId);

        RoomMember member = memberRepository.findByRoomIdAndUserId(roomId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User " + targetUserId + " is not a member of room " + roomId));

        RoomMember.MemberRole newRole = parseMemberRole(request.getRole());

        // Prevent removing the last admin
        if (member.getRole() == RoomMember.MemberRole.ADMIN &&
            newRole == RoomMember.MemberRole.MEMBER) {
            long adminCount = memberRepository.countAdmins(roomId);
            if (adminCount == 1) {
                throw new BadRequestException(
                        "Cannot demote the last admin. Assign another admin first.");
            }
        }

        member.setRole(newRole);
        member = memberRepository.save(member);
        log.info("Role of user {} in room {} changed to {} by {}",
                  targetUserId, roomId, newRole, requesterId);
        return RoomMemberResponse.from(member);
    }

    // ── Mute / Unmute Member ───────────────────────────────

    @Override
    public RoomMemberResponse muteMember(Long roomId, Long targetUserId,
                                          Long requesterId, boolean mute) {
        Room room = findRoom(roomId);
        assertAdmin(room, requesterId);

        RoomMember member = memberRepository.findByRoomIdAndUserId(roomId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User " + targetUserId + " is not a member of room " + roomId));

        member.setIsMuted(mute);
        member = memberRepository.save(member);
        log.info("User {} {}muted in room {} by {}", targetUserId,
                  mute ? "" : "un", roomId, requesterId);
        return RoomMemberResponse.from(member);
    }

    // ── Get Members ────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<RoomMemberResponse> getMembers(Long roomId, Long requesterId) {
        Room room = findRoom(roomId);
        assertMember(room, requesterId);

        return memberRepository.findByRoomId(roomId)
                .stream()
                .map(RoomMemberResponse::from)
                .collect(Collectors.toList());
    }

    // ── Update Last Read ───────────────────────────────────

    @Override
    public void updateLastRead(Long roomId, Long userId, UpdateLastReadRequest request) {
        if (!memberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ForbiddenException("You are not a member of room " + roomId);
        }
        memberRepository.updateLastReadAt(roomId, userId, request.getReadAt());
        RoomMember member = memberRepository.findByRoomIdAndUserId(roomId, userId).orElse(null);
        if (member != null) {
            member.setUnreadCount(0);
            memberRepository.save(member);
        }
        log.debug("lastReadAt updated for user {} in room {} → {}", userId, roomId, request.getReadAt());
    }

    // ── Get Unread Count ───────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public int getUnreadCount(Long roomId, Long userId, LocalDateTime lastMessageAt) {
        RoomMember member = memberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ForbiddenException(
                        "You are not a member of room " + roomId));

        if (member.getLastReadAt() == null || lastMessageAt == null) return 0;

        // message-service owns actual count; this returns -1 to signal
        // that message-service should be queried with lastReadAt
        // Returning lastReadAt so caller can query message-service
        return member.getLastReadAt().isBefore(lastMessageAt) ? -1 : 0;
    }

    // ── Update Last Message At (called by message-service) ─

    @Override
    public void updateLastMessageAt(Long roomId, LocalDateTime sentAt, Long senderId) {
        roomRepository.findById(roomId).ifPresent(room -> {
            room.setLastMessageAt(sentAt);
            roomRepository.save(room);
            
            // Increment unread count for all members except sender
            memberRepository.findByRoomId(roomId).forEach(m -> {
                if (senderId == null || !m.getUserId().equals(senderId)) {
                    m.setUnreadCount(m.getUnreadCount() == null ? 1 : m.getUnreadCount() + 1);
                    memberRepository.save(m);
                }
            });
        });
    }

    // ── Get or Create DM ───────────────────────────────────

    @Override
    public RoomResponse getOrCreateDm(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BadRequestException("Cannot create a DM with yourself.");
        }

        // Use the actual authenticated user id for DM creation instead of a hard-coded admin alias.
        final Long effectiveUserId = userId;

        // Return existing DM if it already exists
        java.util.Optional<Room> existingDm = roomRepository.findDmBetween(effectiveUserId, targetUserId);
        
        if (existingDm.isPresent()) {
            return RoomResponse.from(existingDm.get());
        }

        // Otherwise create a new DM room
        Room dm = Room.builder()
                .name("DM-" + effectiveUserId + "-" + targetUserId)
                .type(Room.RoomType.DM)
                .createdById(effectiveUserId)
                .isPrivate(true)
                .maxMembers(2)
                .build();

        dm = roomRepository.save(dm);
        addMemberInternal(dm, effectiveUserId, RoomMember.MemberRole.ADMIN);
        addMemberInternal(dm, targetUserId, RoomMember.MemberRole.ADMIN);

        log.info("DM created between users {} and {}", effectiveUserId, targetUserId);
        return RoomResponse.from(dm);
    }

    // ── Admin ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> getAllRooms() {
        return roomRepository.findAll()
                .stream()
                .map(RoomResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public void adminDeleteRoom(Long roomId) {
        Room room = findRoom(roomId);
        memberRepository.deleteByRoomId(roomId);
        roomRepository.delete(room);
        log.info("Room {} force-deleted by admin", roomId);
    }

    // ── Private Helpers ────────────────────────────────────

    private Room findRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Room not found: " + roomId));
    }

    private void assertMember(Room room, Long userId) {
        if (!memberRepository.existsByRoomIdAndUserId(room.getId(), userId)) {
            throw new ForbiddenException("You are not a member of room " + room.getId());
        }
    }

    private void assertAdmin(Room room, Long userId) {
        RoomMember member = memberRepository
                .findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> new ForbiddenException(
                        "You are not a member of room " + room.getId()));

        if (member.getRole() != RoomMember.MemberRole.ADMIN) {
            throw new ForbiddenException("Only room admins can perform this action.");
        }
    }

    private RoomMember addMemberInternal(Room room, Long userId,
                                          RoomMember.MemberRole role) {
        RoomMember member = RoomMember.builder()
                .room(room)
                .userId(userId)
                .role(role)
                .isMuted(false)
                .build();
        return memberRepository.save(member);
    }

    private String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private Room.RoomType parseRoomType(String type) {
        if (type == null) return Room.RoomType.GROUP;
        try {
            return Room.RoomType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid room type: " + type + ". Use GROUP or DM.");
        }
    }

    private RoomMember.MemberRole parseMemberRole(String role) {
        if (role == null) return RoomMember.MemberRole.MEMBER;
        try {
            return RoomMember.MemberRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role: " + role + ". Use ADMIN or MEMBER.");
        }
    }
}
