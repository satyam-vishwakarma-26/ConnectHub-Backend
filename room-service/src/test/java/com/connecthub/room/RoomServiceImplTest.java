package com.connecthub.room;

import com.connecthub.room.dto.request.*;
import com.connecthub.room.dto.response.RoomMemberResponse;
import com.connecthub.room.dto.response.RoomResponse;
import com.connecthub.room.entity.Room;
import com.connecthub.room.entity.RoomMember;
import com.connecthub.room.exception.*;
import com.connecthub.room.repository.RoomMemberRepository;
import com.connecthub.room.repository.RoomRepository;
import com.connecthub.room.service.impl.RoomServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomServiceImpl Tests")
class RoomServiceImplTest {

    @Mock private RoomRepository     roomRepository;
    @Mock private RoomMemberRepository memberRepository;

    @InjectMocks private RoomServiceImpl roomService;

    private Room       testRoom;
    private RoomMember adminMember;
    private RoomMember regularMember;

    @BeforeEach
    void setUp() {
        testRoom = Room.builder()
                .id(1L)
                .name("Engineering")
                .type(Room.RoomType.GROUP)
                .createdById(10L)
                .isPrivate(false)
                .maxMembers(100)
                .inviteCode("ABC123XYZ000")
                .build();

        adminMember = RoomMember.builder()
                .id(1L)
                .room(testRoom)
                .userId(10L)
                .role(RoomMember.MemberRole.ADMIN)
                .isMuted(false)
                .build();

        regularMember = RoomMember.builder()
                .id(2L)
                .room(testRoom)
                .userId(20L)
                .role(RoomMember.MemberRole.MEMBER)
                .isMuted(false)
                .build();

        testRoom.getMembers().add(adminMember);
        testRoom.getMembers().add(regularMember);
    }

    // ── createRoom ─────────────────────────────────────────

    @Test
    @DisplayName("createRoom() — GROUP room saves room and adds creator as ADMIN")
    void createRoom_group_success() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("Engineering");
        req.setType("GROUP");

        when(roomRepository.save(any(Room.class))).thenReturn(testRoom);
        when(memberRepository.save(any(RoomMember.class))).thenReturn(adminMember);

        RoomResponse response = roomService.createRoom(10L, req);

        assertThat(response.getName()).isEqualTo("Engineering");
        verify(roomRepository).save(any(Room.class));
        verify(memberRepository).save(any(RoomMember.class)); // creator added as ADMIN
    }

    @Test
    @DisplayName("createRoom() — DM without targetUserId throws BadRequestException")
    void createRoom_dm_missingTarget_throws() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setType("DM");

        assertThatThrownBy(() -> roomService.createRoom(10L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("targetUserId is required");
    }

    @Test
    @DisplayName("createRoom() — DM with self throws BadRequestException")
    void createRoom_dm_withSelf_throws() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setType("DM");
        req.setTargetUserId(10L); // same as creator

        assertThatThrownBy(() -> roomService.createRoom(10L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot create a DM with yourself");
    }

    @Test
    @DisplayName("createRoom() defaults null type to GROUP and validates name")
    void createRoom_groupWithoutName_throws() {
        CreateRoomRequest req = new CreateRoomRequest();

        assertThatThrownBy(() -> roomService.createRoom(10L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Room name is required");
    }

    @Test
    @DisplayName("createRoom() rejects unknown room type")
    void createRoom_invalidType_throws() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setType("CHANNEL");

        assertThatThrownBy(() -> roomService.createRoom(10L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid room type");
    }

    // ── getRoomById ────────────────────────────────────────

    @Test
    @DisplayName("getRoomById() — returns room with members for member user")
    void getRoomById_memberCanView() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.existsByRoomIdAndUserId(1L, 20L)).thenReturn(true);
        when(memberRepository.findByRoomId(1L)).thenReturn(List.of(adminMember, regularMember));

        RoomResponse response = roomService.getRoomById(1L, 20L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getMembers()).hasSize(2);
    }

    @Test
    @DisplayName("getRoomById() — non-member gets ForbiddenException")
    void getRoomById_nonMember_throws() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.existsByRoomIdAndUserId(1L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> roomService.getRoomById(1L, 99L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("getRoomById() throws ResourceNotFoundException when room is missing")
    void getRoomById_missing_throws() {
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getRoomById(99L, 20L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Room not found");
    }

    @Test
    @DisplayName("getRoomsByUser() maps unread count for memberships")
    void getRoomsByUser_success() {
        regularMember.setUnreadCount(7);
        when(roomRepository.findRoomsByUserId(20L)).thenReturn(List.of(testRoom));

        List<RoomResponse> response = roomService.getRoomsByUser(20L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getUnreadCount()).isEqualTo(7);
    }

    // ── updateRoom ─────────────────────────────────────────

    @Test
    @DisplayName("updateRoom() — admin can update room name")
    void updateRoom_adminSuccess() {
        UpdateRoomRequest req = new UpdateRoomRequest();
        req.setName("Engineering v2");

        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 10L)).thenReturn(Optional.of(adminMember));
        when(roomRepository.save(any(Room.class))).thenReturn(testRoom);

        RoomResponse response = roomService.updateRoom(1L, 10L, req);

        verify(roomRepository).save(any(Room.class));
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("updateRoom() — non-admin gets ForbiddenException")
    void updateRoom_nonAdmin_throws() {
        UpdateRoomRequest req = new UpdateRoomRequest();
        req.setName("Hacked Name");

        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 20L)).thenReturn(Optional.of(regularMember));

        assertThatThrownBy(() -> roomService.updateRoom(1L, 20L, req))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only room admins");
    }

    @Test
    @DisplayName("updateRoom() updates all optional fields when provided")
    void updateRoom_allFields_success() {
        UpdateRoomRequest req = new UpdateRoomRequest();
        req.setName("Engineering v2");
        req.setDescription("Updated");
        req.setAvatarUrl("https://cdn.example/avatar.png");
        req.setIsPrivate(true);
        req.setMaxMembers(50);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 10L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.findByRoomId(1L)).thenReturn(List.of(adminMember, regularMember));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomResponse response = roomService.updateRoom(1L, 10L, req);

        assertThat(response.getName()).isEqualTo("Engineering v2");
        assertThat(response.getDescription()).isEqualTo("Updated");
        assertThat(response.getIsPrivate()).isTrue();
        assertThat(response.getMaxMembers()).isEqualTo(50);
    }

    @Test
    @DisplayName("updateRoom() rejects maxMembers below current member count")
    void updateRoom_maxMembersTooSmall_throws() {
        UpdateRoomRequest req = new UpdateRoomRequest();
        req.setMaxMembers(1);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 10L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.findByRoomId(1L)).thenReturn(List.of(adminMember, regularMember));

        assertThatThrownBy(() -> roomService.updateRoom(1L, 10L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maxMembers cannot be less");
    }

    @Test
    @DisplayName("deleteRoom() allows creator and removes room members first")
    void deleteRoom_creator_success() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));

        roomService.deleteRoom(1L, 10L);

        verify(memberRepository).deleteByRoomId(1L);
        verify(roomRepository).delete(testRoom);
    }

    @Test
    @DisplayName("deleteRoom() rejects non creator")
    void deleteRoom_nonCreator_throws() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));

        assertThatThrownBy(() -> roomService.deleteRoom(1L, 20L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only the room creator");
    }

    // ── addMember ──────────────────────────────────────────

    @Test
    @DisplayName("addMember() — admin can add new member")
    void addMember_success() {
        AddMemberRequest req = new AddMemberRequest();
        req.setUserId(30L);

        RoomMember newMember = RoomMember.builder()
                .id(3L).room(testRoom).userId(30L)
                .role(RoomMember.MemberRole.MEMBER).isMuted(false).build();

        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 10L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.existsByRoomIdAndUserId(1L, 30L)).thenReturn(false);
        when(memberRepository.findByRoomId(1L)).thenReturn(List.of(adminMember, regularMember));
        when(memberRepository.save(any(RoomMember.class))).thenReturn(newMember);

        RoomMemberResponse response = roomService.addMember(1L, 10L, req);

        assertThat(response.getUserId()).isEqualTo(30L);
    }

    @Test
    @DisplayName("addMember() — throws DuplicateResourceException if already member")
    void addMember_duplicate_throws() {
        AddMemberRequest req = new AddMemberRequest();
        req.setUserId(20L); // already a member

        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 10L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.existsByRoomIdAndUserId(1L, 20L)).thenReturn(true);

        assertThatThrownBy(() -> roomService.addMember(1L, 10L, req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already a member");
    }

    @Test
    @DisplayName("addMember() can add an ADMIN role")
    void addMember_adminRole_success() {
        AddMemberRequest req = new AddMemberRequest();
        req.setUserId(30L);
        req.setRole("ADMIN");
        RoomMember newAdmin = RoomMember.builder()
                .id(3L).room(testRoom).userId(30L)
                .role(RoomMember.MemberRole.ADMIN).isMuted(false).build();

        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 10L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.existsByRoomIdAndUserId(1L, 30L)).thenReturn(false);
        when(memberRepository.findByRoomId(1L)).thenReturn(List.of(adminMember, regularMember));
        when(memberRepository.save(any(RoomMember.class))).thenReturn(newAdmin);

        RoomMemberResponse response = roomService.addMember(1L, 10L, req);

        assertThat(response.getRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("addMember() rejects invalid role")
    void addMember_invalidRole_throws() {
        AddMemberRequest req = new AddMemberRequest();
        req.setUserId(30L);
        req.setRole("OWNER");

        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 10L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.existsByRoomIdAndUserId(1L, 30L)).thenReturn(false);
        when(memberRepository.findByRoomId(1L)).thenReturn(List.of(adminMember, regularMember));

        assertThatThrownBy(() -> roomService.addMember(1L, 10L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid role");
    }

    @Test
    @DisplayName("addMember() rejects DM rooms")
    void addMember_dm_throws() {
        Room dm = dmRoom();
        AddMemberRequest req = new AddMemberRequest();
        req.setUserId(30L);

        when(roomRepository.findById(2L)).thenReturn(Optional.of(dm));
        when(memberRepository.findByRoomIdAndUserId(2L, 10L)).thenReturn(Optional.of(adminMember));

        assertThatThrownBy(() -> roomService.addMember(2L, 10L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot add members to a DM");
    }

    @Test
    @DisplayName("addMember() rejects rooms at capacity")
    void addMember_capacity_throws() {
        testRoom.setMaxMembers(2);
        AddMemberRequest req = new AddMemberRequest();
        req.setUserId(30L);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 10L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.existsByRoomIdAndUserId(1L, 30L)).thenReturn(false);
        when(memberRepository.findByRoomId(1L)).thenReturn(List.of(adminMember, regularMember));

        assertThatThrownBy(() -> roomService.addMember(1L, 10L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maximum member capacity");
    }

    // ── muteMember ─────────────────────────────────────────

    @Test
    @DisplayName("muteMember() — admin can mute a member")
    void muteMember_success() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 10L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.findByRoomIdAndUserId(1L, 20L)).thenReturn(Optional.of(regularMember));
        when(memberRepository.save(any(RoomMember.class))).thenReturn(regularMember);

        RoomMemberResponse response = roomService.muteMember(1L, 20L, 10L, true);

        verify(memberRepository).save(argThat(m -> m.getIsMuted()));
    }

    @Test
    @DisplayName("muteMember() throws when target is not a member")
    void muteMember_missingTarget_throws() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 10L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.findByRoomIdAndUserId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.muteMember(1L, 99L, 10L, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not a member");
    }

    // ── leaveRoom ──────────────────────────────────────────

    @Test
    @DisplayName("leaveRoom() — last admin with other members throws BadRequestException")
    void leaveRoom_lastAdmin_throws() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 10L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.countAdmins(1L)).thenReturn(1L);
        when(memberRepository.findByRoomId(1L)).thenReturn(List.of(adminMember, regularMember));

        assertThatThrownBy(() -> roomService.leaveRoom(1L, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("last admin");
    }

    @Test
    @DisplayName("leaveRoom() allows regular member to leave")
    void leaveRoom_member_success() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 20L)).thenReturn(Optional.of(regularMember));

        roomService.leaveRoom(1L, 20L);

        verify(memberRepository).delete(regularMember);
    }

    @Test
    @DisplayName("leaveRoom() rejects DM rooms")
    void leaveRoom_dm_throws() {
        Room dm = dmRoom();
        when(roomRepository.findById(2L)).thenReturn(Optional.of(dm));

        assertThatThrownBy(() -> roomService.leaveRoom(2L, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot leave a DM");
    }

    @Test
    @DisplayName("leaveRoom() rejects non member")
    void leaveRoom_nonMember_throws() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.leaveRoom(1L, 99L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not a member");
    }

    // ── joinRoomByInviteCode ───────────────────────────────

    @Test
    @DisplayName("joinRoomByInviteCode() — valid code adds user as MEMBER")
    void joinByInviteCode_success() {
        when(roomRepository.findByInviteCode("ABC123XYZ000")).thenReturn(Optional.of(testRoom));
        when(memberRepository.existsByRoomIdAndUserId(1L, 30L)).thenReturn(false);
        when(memberRepository.findByRoomId(1L)).thenReturn(List.of(adminMember));
        when(memberRepository.save(any(RoomMember.class))).thenReturn(regularMember);

        RoomResponse response = roomService.joinRoomByInviteCode("ABC123XYZ000", 30L);

        assertThat(response.getName()).isEqualTo("Engineering");
        verify(memberRepository).save(any(RoomMember.class));
    }

    @Test
    @DisplayName("joinRoomByInviteCode() — invalid code throws ResourceNotFoundException")
    void joinByInviteCode_invalid_throws() {
        when(roomRepository.findByInviteCode("BADCODE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.joinRoomByInviteCode("BADCODE", 30L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("joinRoomByInviteCode() returns room when user is already a member")
    void joinByInviteCode_alreadyMember_returnsRoom() {
        when(roomRepository.findByInviteCode("ABC123XYZ000")).thenReturn(Optional.of(testRoom));
        when(memberRepository.existsByRoomIdAndUserId(1L, 20L)).thenReturn(true);

        RoomResponse response = roomService.joinRoomByInviteCode("ABC123XYZ000", 20L);

        assertThat(response.getId()).isEqualTo(1L);
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("joinRoomByInviteCode() rejects DM rooms")
    void joinByInviteCode_dm_throws() {
        Room dm = dmRoom();
        when(roomRepository.findByInviteCode("DMCODE")).thenReturn(Optional.of(dm));

        assertThatThrownBy(() -> roomService.joinRoomByInviteCode("DMCODE", 30L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot join a DM");
    }

    @Test
    @DisplayName("joinRoomByInviteCode() rejects room at capacity")
    void joinByInviteCode_capacity_throws() {
        testRoom.setMaxMembers(2);
        when(roomRepository.findByInviteCode("ABC123XYZ000")).thenReturn(Optional.of(testRoom));
        when(memberRepository.existsByRoomIdAndUserId(1L, 30L)).thenReturn(false);
        when(memberRepository.findByRoomId(1L)).thenReturn(List.of(adminMember, regularMember));

        assertThatThrownBy(() -> roomService.joinRoomByInviteCode("ABC123XYZ000", 30L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maximum member capacity");
    }

    @Test
    @DisplayName("regenerateInviteCode() saves a new invite code")
    void regenerateInviteCode_success() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 10L)).thenReturn(Optional.of(adminMember));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        String newCode = roomService.regenerateInviteCode(1L, 10L);

        assertThat(newCode).hasSize(12);
        assertThat(testRoom.getInviteCode()).isEqualTo(newCode);
    }

    @Test
    @DisplayName("removeMember() removes target member")
    void removeMember_success() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 10L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.findByRoomIdAndUserId(1L, 20L)).thenReturn(Optional.of(regularMember));

        roomService.removeMember(1L, 20L, 10L);

        verify(memberRepository).delete(regularMember);
    }

    @Test
    @DisplayName("removeMember() rejects removing self")
    void removeMember_self_throws() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 10L)).thenReturn(Optional.of(adminMember));

        assertThatThrownBy(() -> roomService.removeMember(1L, 10L, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Use leaveRoom");
    }

    @Test
    @DisplayName("removeMember() throws when target is missing")
    void removeMember_missingTarget_throws() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 10L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.findByRoomIdAndUserId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.removeMember(1L, 99L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    @DisplayName("updateMemberRole() promotes a member")
    void updateMemberRole_success() {
        UpdateMemberRoleRequest req = new UpdateMemberRoleRequest();
        req.setRole("ADMIN");

        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 10L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.findByRoomIdAndUserId(1L, 20L)).thenReturn(Optional.of(regularMember));
        when(memberRepository.save(any(RoomMember.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomMemberResponse response = roomService.updateMemberRole(1L, 20L, 10L, req);

        assertThat(response.getRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("updateMemberRole() blocks demoting the last admin")
    void updateMemberRole_lastAdmin_throws() {
        UpdateMemberRoleRequest req = new UpdateMemberRoleRequest();
        req.setRole("MEMBER");

        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.findByRoomIdAndUserId(1L, 10L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.countAdmins(1L)).thenReturn(1L);

        assertThatThrownBy(() -> roomService.updateMemberRole(1L, 10L, 10L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot demote the last admin");
    }

    // ── updateLastRead ─────────────────────────────────────

    @Test
    @DisplayName("updateLastRead() — updates timestamp for member")
    void updateLastRead_success() {
        UpdateLastReadRequest req = new UpdateLastReadRequest();
        req.setReadAt(LocalDateTime.now());

        when(memberRepository.existsByRoomIdAndUserId(1L, 20L)).thenReturn(true);
        when(memberRepository.updateLastReadAt(eq(1L), eq(20L), any())).thenReturn(1);

        assertThatNoException().isThrownBy(
                () -> roomService.updateLastRead(1L, 20L, req));
        verify(memberRepository).updateLastReadAt(eq(1L), eq(20L), any());
    }

    @Test
    @DisplayName("updateLastRead() resets unread count when member row is found")
    void updateLastRead_resetsUnreadCount_success() {
        UpdateLastReadRequest req = new UpdateLastReadRequest();
        req.setReadAt(LocalDateTime.now());
        regularMember.setUnreadCount(4);

        when(memberRepository.existsByRoomIdAndUserId(1L, 20L)).thenReturn(true);
        when(memberRepository.updateLastReadAt(eq(1L), eq(20L), any())).thenReturn(1);
        when(memberRepository.findByRoomIdAndUserId(1L, 20L)).thenReturn(Optional.of(regularMember));

        roomService.updateLastRead(1L, 20L, req);

        assertThat(regularMember.getUnreadCount()).isZero();
        verify(memberRepository).save(regularMember);
    }

    @Test
    @DisplayName("updateLastRead() rejects non member")
    void updateLastRead_nonMember_throws() {
        UpdateLastReadRequest req = new UpdateLastReadRequest();
        req.setReadAt(LocalDateTime.now());
        when(memberRepository.existsByRoomIdAndUserId(1L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> roomService.updateLastRead(1L, 99L, req))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    @DisplayName("getUnreadCount() returns -1 when last read predates last message")
    void getUnreadCount_unreadSignal() {
        regularMember.setLastReadAt(LocalDateTime.parse("2026-05-08T10:00:00"));
        when(memberRepository.findByRoomIdAndUserId(1L, 20L)).thenReturn(Optional.of(regularMember));

        int count = roomService.getUnreadCount(1L, 20L, LocalDateTime.parse("2026-05-08T11:00:00"));

        assertThat(count).isEqualTo(-1);
    }

    @Test
    @DisplayName("getUnreadCount() returns zero for null timestamps and throws for non member")
    void getUnreadCount_zeroAndForbidden() {
        regularMember.setLastReadAt(null);
        when(memberRepository.findByRoomIdAndUserId(1L, 20L)).thenReturn(Optional.of(regularMember));
        when(memberRepository.findByRoomIdAndUserId(1L, 99L)).thenReturn(Optional.empty());

        assertThat(roomService.getUnreadCount(1L, 20L, LocalDateTime.now())).isZero();
        assertThatThrownBy(() -> roomService.getUnreadCount(1L, 99L, LocalDateTime.now()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("updateLastMessageAt() updates room and increments unread counts except sender")
    void updateLastMessageAt_success() {
        regularMember.setUnreadCount(null);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByRoomId(1L)).thenReturn(List.of(adminMember, regularMember));

        LocalDateTime sentAt = LocalDateTime.now();
        roomService.updateLastMessageAt(1L, sentAt, 10L);

        assertThat(testRoom.getLastMessageAt()).isEqualTo(sentAt);
        assertThat(regularMember.getUnreadCount()).isEqualTo(1);
        assertThat(adminMember.getUnreadCount()).isZero();
        verify(memberRepository).save(regularMember);
    }

    @Test
    @DisplayName("getOrCreateDm() returns existing DM")
    void getOrCreateDm_existing_success() {
        Room dm = dmRoom();
        when(roomRepository.findDmBetween(10L, 20L)).thenReturn(Optional.of(dm));

        RoomResponse response = roomService.getOrCreateDm(10L, 20L);

        assertThat(response.getType()).isEqualTo("DM");
        verify(roomRepository, never()).save(any(Room.class));
    }

    @Test
    @DisplayName("getOrCreateDm() creates new DM and adds both users")
    void getOrCreateDm_new_success() {
        Room dm = dmRoom();
        when(roomRepository.findDmBetween(10L, 20L)).thenReturn(Optional.empty());
        when(roomRepository.save(any(Room.class))).thenReturn(dm);
        when(memberRepository.save(any(RoomMember.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomResponse response = roomService.getOrCreateDm(10L, 20L);

        assertThat(response.getName()).isEqualTo("DM-10-20");
        verify(memberRepository, times(2)).save(any(RoomMember.class));
    }

    @Test
    @DisplayName("getMembers() returns members for a room member")
    void getMembers_success() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));
        when(memberRepository.existsByRoomIdAndUserId(1L, 20L)).thenReturn(true);
        when(memberRepository.findByRoomId(1L)).thenReturn(List.of(adminMember, regularMember));

        List<RoomMemberResponse> response = roomService.getMembers(1L, 20L);

        assertThat(response).extracting(RoomMemberResponse::getUserId)
                .containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("getAllRooms() maps all rooms")
    void getAllRooms_success() {
        when(roomRepository.findAll()).thenReturn(List.of(testRoom));

        List<RoomResponse> response = roomService.getAllRooms();

        assertThat(response).hasSize(1);
    }

    @Test
    @DisplayName("adminDeleteRoom() deletes members and room")
    void adminDeleteRoom_success() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(testRoom));

        roomService.adminDeleteRoom(1L);

        verify(memberRepository).deleteByRoomId(1L);
        verify(roomRepository).delete(testRoom);
    }

    private Room dmRoom() {
        Room dm = Room.builder()
                .id(2L)
                .name("DM-10-20")
                .type(Room.RoomType.DM)
                .createdById(10L)
                .isPrivate(true)
                .maxMembers(2)
                .inviteCode("DMCODE")
                .build();
        RoomMember first = RoomMember.builder()
                .id(3L).room(dm).userId(10L)
                .role(RoomMember.MemberRole.ADMIN).isMuted(false).build();
        RoomMember second = RoomMember.builder()
                .id(4L).room(dm).userId(20L)
                .role(RoomMember.MemberRole.ADMIN).isMuted(false).build();
        dm.getMembers().add(first);
        dm.getMembers().add(second);
        return dm;
    }
}
