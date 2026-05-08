package com.connecthub.room.controller;

import com.connecthub.room.dto.request.AddMemberRequest;
import com.connecthub.room.dto.request.CreateRoomRequest;
import com.connecthub.room.dto.request.UpdateLastReadRequest;
import com.connecthub.room.dto.request.UpdateMemberRoleRequest;
import com.connecthub.room.dto.request.UpdateRoomRequest;
import com.connecthub.room.dto.response.ApiResponse;
import com.connecthub.room.dto.response.RoomMemberResponse;
import com.connecthub.room.dto.response.RoomResponse;
import com.connecthub.room.security.AuthenticatedUser;
import com.connecthub.room.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomController Tests")
class RoomControllerTest {

    @Mock private RoomService roomService;
    @InjectMocks private RoomController controller;

    private AuthenticatedUser me;
    private RoomResponse room;
    private RoomMemberResponse member;

    @BeforeEach
    void setUp() {
        me = new AuthenticatedUser(10L, "me@example.com", "USER");
        room = RoomResponse.builder()
                .id(1L)
                .name("Engineering")
                .type("GROUP")
                .createdById(10L)
                .maxMembers(100)
                .inviteCode("ABC123XYZ000")
                .build();
        member = RoomMemberResponse.builder()
                .id(2L)
                .roomId(1L)
                .userId(20L)
                .role("MEMBER")
                .isMuted(false)
                .build();
    }

    @Test
    void createRoom_returnsCreated() {
        CreateRoomRequest request = new CreateRoomRequest();
        when(roomService.createRoom(10L, request)).thenReturn(room);

        ResponseEntity<ApiResponse<RoomResponse>> response = controller.createRoom(me, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData()).isEqualTo(room);
    }

    @Test
    void roomCrudEndpoints_delegateToService() {
        UpdateRoomRequest request = new UpdateRoomRequest();
        when(roomService.getRoomById(1L, 10L)).thenReturn(room);
        when(roomService.getRoomsByUser(10L)).thenReturn(List.of(room));
        when(roomService.updateRoom(1L, 10L, request)).thenReturn(room);

        assertThat(controller.getRoomById(me, 1L).getBody().getData()).isEqualTo(room);
        assertThat(controller.getMyRooms(me).getBody().getData()).containsExactly(room);
        assertThat(controller.updateRoom(me, 1L, request).getBody().getData()).isEqualTo(room);
        assertThat(controller.deleteRoom(me, 1L).getBody().getMessage()).isEqualTo("Room deleted");

        verify(roomService).deleteRoom(1L, 10L);
    }

    @Test
    void inviteAndLeaveEndpoints_delegateToService() {
        when(roomService.joinRoomByInviteCode("ABC123XYZ000", 10L)).thenReturn(room);
        when(roomService.regenerateInviteCode(1L, 10L)).thenReturn("NEWCODE12345");

        assertThat(controller.joinByInviteCode(me, "ABC123XYZ000").getBody().getData()).isEqualTo(room);
        assertThat(controller.leaveRoom(me, 1L).getBody().getMessage()).isEqualTo("Left room");
        ResponseEntity<ApiResponse<Map<String, String>>> regenerated =
                controller.regenerateInviteCode(me, 1L);

        assertThat(regenerated.getBody().getData()).containsEntry("inviteCode", "NEWCODE12345");
        verify(roomService).leaveRoom(1L, 10L);
    }

    @Test
    void memberManagementEndpoints_delegateToService() {
        AddMemberRequest addRequest = new AddMemberRequest();
        UpdateMemberRoleRequest roleRequest = new UpdateMemberRoleRequest();
        when(roomService.getMembers(1L, 10L)).thenReturn(List.of(member));
        when(roomService.addMember(1L, 10L, addRequest)).thenReturn(member);
        when(roomService.updateMemberRole(1L, 20L, 10L, roleRequest)).thenReturn(member);
        when(roomService.muteMember(1L, 20L, 10L, true)).thenReturn(member);
        when(roomService.muteMember(1L, 20L, 10L, false)).thenReturn(member);

        assertThat(controller.getMembers(me, 1L).getBody().getData()).containsExactly(member);
        assertThat(controller.addMember(me, 1L, addRequest).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.removeMember(me, 1L, 20L).getBody().getMessage()).isEqualTo("Member removed");
        assertThat(controller.updateMemberRole(me, 1L, 20L, roleRequest).getBody().getData()).isEqualTo(member);
        assertThat(controller.muteMember(me, 1L, 20L).getBody().getMessage()).isEqualTo("Member muted");
        assertThat(controller.unmuteMember(me, 1L, 20L).getBody().getMessage()).isEqualTo("Member unmuted");

        verify(roomService).removeMember(1L, 20L, 10L);
    }

    @Test
    void unreadAndInternalEndpoints_delegateToService() {
        UpdateLastReadRequest readRequest = new UpdateLastReadRequest();
        LocalDateTime sentAt = LocalDateTime.parse("2026-05-08T10:15:30");
        when(roomService.getUnreadCount(1L, 10L, sentAt)).thenReturn(3);

        assertThat(controller.updateLastRead(me, 1L, readRequest).getBody().getMessage())
                .isEqualTo("Last read updated");
        assertThat(controller.getUnreadCount(me, 1L, sentAt.toString()).getBody().getData())
                .containsEntry("unreadCount", 3);
        assertThat(controller.getUnreadCount(me, 1L, null).getBody().getData())
                .containsEntry("unreadCount", 0);
        assertThat(controller.updateLastMessageAt(1L, sentAt.toString(), 10L).getBody().getMessage())
                .isEqualTo("lastMessageAt updated");

        verify(roomService).updateLastRead(1L, 10L, readRequest);
        verify(roomService).updateLastMessageAt(1L, sentAt, 10L);
    }

    @Test
    void dmAndAdminEndpoints_delegateToService() {
        when(roomService.getOrCreateDm(10L, 20L)).thenReturn(room);
        when(roomService.getAllRooms()).thenReturn(List.of(room));

        assertThat(controller.getOrCreateDm(me, 20L).getBody().getData()).isEqualTo(room);
        assertThat(controller.getAllRooms().getBody().getData()).containsExactly(room);
        assertThat(controller.adminDeleteRoom(1L).getBody().getMessage()).isEqualTo("Room deleted by admin");

        verify(roomService).adminDeleteRoom(1L);
    }
}
