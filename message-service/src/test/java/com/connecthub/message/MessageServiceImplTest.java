package com.connecthub.message;

import com.connecthub.message.dto.request.EditMessageRequest;
import com.connecthub.message.dto.request.SendMessageRequest;
import com.connecthub.message.dto.request.UpdateDeliveryStatusRequest;
import com.connecthub.message.dto.response.MessageResponse;
import com.connecthub.message.entity.Message;
import com.connecthub.message.exception.BadRequestException;
import com.connecthub.message.exception.ForbiddenException;
import com.connecthub.message.exception.ResourceNotFoundException;
import com.connecthub.message.repository.MessageReactionRepository;
import com.connecthub.message.repository.MessageRepository;
import com.connecthub.message.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageServiceImpl Tests")
class MessageServiceImplTest {

    @Mock private MessageRepository messageRepository;
    @Mock private MessageReactionRepository messageReactionRepository;
    @Mock private RestTemplate      restTemplate;

    @InjectMocks private MessageServiceImpl messageService;

    private Message textMessage;
    private Message imageMessage;
    private Message deletedMessage;

    @BeforeEach
    void setUp() {
        // Inject @Value fields via ReflectionTestUtils
        ReflectionTestUtils.setField(messageService, "roomServiceUrl", "http://localhost:8082/api");
        ReflectionTestUtils.setField(messageService, "maxPageSize", 100);

        lenient().when(messageReactionRepository.countGroupedByMessageIds(any()))
                .thenReturn(java.util.Collections.emptyList());

        textMessage = Message.builder()
                .id(1L)
                .roomId(10L)
                .senderId(100L)
                .content("Hello, World!")
                .type(Message.MessageType.TEXT)
                .isEdited(false)
                .isDeleted(false)
                .isPinned(false)
                .deliveryStatus(Message.DeliveryStatus.SENT)
                .sentAt(LocalDateTime.now())
                .build();

        imageMessage = Message.builder()
                .id(2L)
                .roomId(10L)
                .senderId(100L)
                .content(null)
                .type(Message.MessageType.IMAGE)
                .mediaUrl("https://s3.amazonaws.com/bucket/image.png")
                .mediaFilename("image.png")
                .mediaSizeKb(512L)
                .isDeleted(false)
                .deliveryStatus(Message.DeliveryStatus.SENT)
                .sentAt(LocalDateTime.now())
                .build();

        deletedMessage = Message.builder()
                .id(3L)
                .roomId(10L)
                .senderId(100L)
                .content("[deleted]")
                .type(Message.MessageType.TEXT)
                .isDeleted(true)
                .deliveryStatus(Message.DeliveryStatus.SENT)
                .sentAt(LocalDateTime.now())
                .build();
    }

    // ── sendMessage ────────────────────────────────────────

    @Test
    @DisplayName("sendMessage() — TEXT message is persisted and room-service notified")
    void sendMessage_text_success() {
        SendMessageRequest req = new SendMessageRequest();
        req.setRoomId(10L);
        req.setContent("Hello!");
        req.setType("TEXT");

        when(messageRepository.save(any(Message.class))).thenReturn(textMessage);
        // RestTemplate.put() is void — no stub needed; fire-and-forget

        MessageResponse response = messageService.sendMessage(100L, req);

        assertThat(response).isNotNull();
        assertThat(response.getRoomId()).isEqualTo(10L);
        assertThat(response.getSenderId()).isEqualTo(100L);
        assertThat(response.getContent()).isEqualTo("Hello, World!");
        assertThat(response.getType()).isEqualTo("TEXT");
        assertThat(response.getDeliveryStatus()).isEqualTo("SENT");

        verify(messageRepository).save(any(Message.class));
    }

    @Test
    @DisplayName("sendMessage() — IMAGE message requires mediaUrl")
    void sendMessage_image_missingMediaUrl_throwsBadRequest() {
        SendMessageRequest req = new SendMessageRequest();
        req.setRoomId(10L);
        req.setType("IMAGE");
        // mediaUrl intentionally omitted

        assertThatThrownBy(() -> messageService.sendMessage(100L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("mediaUrl is required");

        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("sendMessage() — FILE message requires mediaUrl")
    void sendMessage_file_missingMediaUrl_throwsBadRequest() {
        SendMessageRequest req = new SendMessageRequest();
        req.setRoomId(10L);
        req.setType("FILE");

        assertThatThrownBy(() -> messageService.sendMessage(100L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("mediaUrl is required");
    }

    @Test
    @DisplayName("sendMessage() — invalid type throws BadRequestException")
    void sendMessage_invalidType_throwsBadRequest() {
        SendMessageRequest req = new SendMessageRequest();
        req.setRoomId(10L);
        req.setContent("hello");
        req.setType("INVALID_TYPE");

        assertThatThrownBy(() -> messageService.sendMessage(100L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid message type");
    }

    @Test
    @DisplayName("sendMessage() — reply target not found throws ResourceNotFoundException")
    void sendMessage_replyTargetNotFound_throwsNotFound() {
        SendMessageRequest req = new SendMessageRequest();
        req.setRoomId(10L);
        req.setContent("reply");
        req.setType("TEXT");
        req.setReplyToMessageId(999L);

        when(messageRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.sendMessage(100L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reply target message not found");
    }

    @Test
    @DisplayName("sendMessage() — reply to deleted message throws ResourceNotFoundException")
    void sendMessage_replyToDeletedMessage_throwsNotFound() {
        SendMessageRequest req = new SendMessageRequest();
        req.setRoomId(10L);
        req.setContent("reply");
        req.setType("TEXT");
        req.setReplyToMessageId(3L);

        when(messageRepository.findById(3L)).thenReturn(Optional.of(deletedMessage));

        assertThatThrownBy(() -> messageService.sendMessage(100L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getMessageById ─────────────────────────────────────

    @Test
    @DisplayName("getMessageById() — returns MessageResponse when found")
    void getMessageById_found() {
        when(messageRepository.findById(1L)).thenReturn(Optional.of(textMessage));

        MessageResponse response = messageService.getMessageById(1L, 100L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getContent()).isEqualTo("Hello, World!");
    }

    @Test
    @DisplayName("getMessageById() — throws ResourceNotFoundException when not found")
    void getMessageById_notFound() {
        when(messageRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.getMessageById(99L, 100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Message not found");
    }

    // ── getMessagesByRoom ──────────────────────────────────

    @Test
    @DisplayName("getMessagesByRoom() — returns page of messages")
    void getMessagesByRoom_returnsPaginatedResults() {
        Page<Message> page = new PageImpl<>(List.of(textMessage, imageMessage));
        when(messageRepository.findByRoomIdAndIsDeletedFalseOrderBySentAtDesc(
                eq(10L), any(Pageable.class))).thenReturn(page);

        Page<MessageResponse> result =
                messageService.getMessagesByRoom(10L, 100L, 0, 30);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getType()).isEqualTo("TEXT");
        assertThat(result.getContent().get(1).getType()).isEqualTo("IMAGE");
    }

    @Test
    @DisplayName("getMessagesByRoom() — clamps page size to maxPageSize")
    void getMessagesByRoom_clampsSize() {
        Page<Message> page = new PageImpl<>(List.of());
        when(messageRepository.findByRoomIdAndIsDeletedFalseOrderBySentAtDesc(
                eq(10L), any(Pageable.class))).thenReturn(page);

        // Request size=9999 — should be clamped to 100
        messageService.getMessagesByRoom(10L, 100L, 0, 9999);

        verify(messageRepository).findByRoomIdAndIsDeletedFalseOrderBySentAtDesc(
                eq(10L),
                argThat(p -> p.getPageSize() == 100));
    }

    // ── editMessage ────────────────────────────────────────

    @Test
    @DisplayName("editMessage() — updates content and sets isEdited=true")
    void editMessage_success() {
        EditMessageRequest req = new EditMessageRequest();
        req.setContent("Updated content");

        when(messageRepository.findById(1L)).thenReturn(Optional.of(textMessage));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageResponse response = messageService.editMessage(1L, 100L, req);

        assertThat(response.getContent()).isEqualTo("Updated content");
        assertThat(response.getIsEdited()).isTrue();
    }

    @Test
    @DisplayName("editMessage() — non-sender cannot edit message")
    void editMessage_notSender_throwsForbidden() {
        EditMessageRequest req = new EditMessageRequest();
        req.setContent("Hacked content");

        when(messageRepository.findById(1L)).thenReturn(Optional.of(textMessage));

        // userId=999 is not the sender (100L)
        assertThatThrownBy(() -> messageService.editMessage(1L, 999L, req))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("own messages");
    }

    @Test
    @DisplayName("editMessage() — IMAGE message cannot be edited")
    void editMessage_imageMessage_throwsBadRequest() {
        EditMessageRequest req = new EditMessageRequest();
        req.setContent("new content");

        when(messageRepository.findById(2L)).thenReturn(Optional.of(imageMessage));

        assertThatThrownBy(() -> messageService.editMessage(2L, 100L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only TEXT messages");
    }

    @Test
    @DisplayName("editMessage() — already deleted message throws BadRequestException")
    void editMessage_deletedMessage_throwsBadRequest() {
        EditMessageRequest req = new EditMessageRequest();
        req.setContent("edit attempt");

        when(messageRepository.findById(3L)).thenReturn(Optional.of(deletedMessage));

        assertThatThrownBy(() -> messageService.editMessage(3L, 100L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been deleted");
    }

    // ── deleteMessage ──────────────────────────────────────

    @Test
    @DisplayName("deleteMessage() — soft-deletes message and blanks content")
    void deleteMessage_success() {
        when(messageRepository.findById(1L)).thenReturn(Optional.of(textMessage));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        messageService.deleteMessage(1L, 100L);

        assertThat(textMessage.getIsDeleted()).isTrue();
        assertThat(textMessage.getContent()).isEqualTo("[deleted]");
        assertThat(textMessage.getMediaUrl()).isNull();
        verify(messageRepository).save(textMessage);
    }

    @Test
    @DisplayName("deleteMessage() — non-sender cannot delete message")
    void deleteMessage_notSender_throwsForbidden() {
        when(messageRepository.findById(1L)).thenReturn(Optional.of(textMessage));

        assertThatThrownBy(() -> messageService.deleteMessage(1L, 999L))
                .isInstanceOf(ForbiddenException.class);

        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteMessage() — already deleted message throws BadRequestException")
    void deleteMessage_alreadyDeleted_throwsBadRequest() {
        when(messageRepository.findById(3L)).thenReturn(Optional.of(deletedMessage));

        assertThatThrownBy(() -> messageService.deleteMessage(3L, 100L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been deleted");
    }

    // ── adminDeleteMessage ─────────────────────────────────

    @Test
    @DisplayName("adminDeleteMessage() — force soft-deletes any message")
    void adminDeleteMessage_success() {
        when(messageRepository.findById(1L)).thenReturn(Optional.of(textMessage));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        messageService.adminDeleteMessage(1L);

        assertThat(textMessage.getIsDeleted()).isTrue();
    }

    @Test
    @DisplayName("adminDeleteMessage() — already deleted message is skipped gracefully")
    void adminDeleteMessage_alreadyDeleted_noOp() {
        when(messageRepository.findById(3L)).thenReturn(Optional.of(deletedMessage));

        // Should not throw — admin delete is idempotent
        assertThatCode(() -> messageService.adminDeleteMessage(3L))
                .doesNotThrowAnyException();

        verify(messageRepository, never()).save(any());
    }

    // ── searchMessages ─────────────────────────────────────

    @Test
    @DisplayName("searchMessages() — returns matching messages")
    void searchMessages_returnsResults() {
        when(messageRepository.searchInRoom(10L, "Hello"))
                .thenReturn(List.of(textMessage));

        List<MessageResponse> results = messageService.searchMessages(10L, "Hello", 100L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getContent()).isEqualTo("Hello, World!");
    }

    @Test
    @DisplayName("searchMessages() — blank keyword throws BadRequestException")
    void searchMessages_blankKeyword_throwsBadRequest() {
        assertThatThrownBy(() -> messageService.searchMessages(10L, "  ", 100L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("keyword cannot be empty");
    }

    // ── updateDeliveryStatus ───────────────────────────────

    @Test
    @DisplayName("updateDeliveryStatus() — advances status to DELIVERED")
    void updateDeliveryStatus_delivered() {
        UpdateDeliveryStatusRequest req = new UpdateDeliveryStatusRequest();
        req.setStatus("DELIVERED");

        when(messageRepository.updateDeliveryStatus(1L, Message.DeliveryStatus.DELIVERED))
                .thenReturn(1);

        assertThatCode(() -> messageService.updateDeliveryStatus(1L, req))
                .doesNotThrowAnyException();

        verify(messageRepository).updateDeliveryStatus(1L, Message.DeliveryStatus.DELIVERED);
    }

    @Test
    @DisplayName("updateDeliveryStatus() — invalid status throws BadRequestException")
    void updateDeliveryStatus_invalid_throwsBadRequest() {
        UpdateDeliveryStatusRequest req = new UpdateDeliveryStatusRequest();
        req.setStatus("UNKNOWN");

        assertThatThrownBy(() -> messageService.updateDeliveryStatus(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid delivery status");
    }

    // ── getUnreadCount ─────────────────────────────────────

    @Test
    @DisplayName("getUnreadCount() — returns count of unread messages")
    void getUnreadCount_returnsCount() {
        LocalDateTime after = LocalDateTime.now().minusHours(1);
        when(messageRepository.countUnreadMessages(10L, after)).thenReturn(5L);

        long count = messageService.getUnreadCount(10L, after);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    @DisplayName("getUnreadCount() — returns 0 when after is null")
    void getUnreadCount_nullAfter_returnsZero() {
        long count = messageService.getUnreadCount(10L, null);
        assertThat(count).isZero();
        verify(messageRepository, never()).countUnreadMessages(any(), any());
    }

    // ── pinMessage / unpinMessage ──────────────────────────

    @Test
    @DisplayName("pinMessage() — sets isPinned=true")
    void pinMessage_success() {
        when(messageRepository.findById(1L)).thenReturn(Optional.of(textMessage));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageResponse response = messageService.pinMessage(1L, 100L);

        assertThat(response.getIsPinned()).isTrue();
    }

    @Test
    @DisplayName("unpinMessage() — sets isPinned=false")
    void unpinMessage_success() {
        textMessage.setIsPinned(true);
        when(messageRepository.findById(1L)).thenReturn(Optional.of(textMessage));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageResponse response = messageService.unpinMessage(1L, 100L);

        assertThat(response.getIsPinned()).isFalse();
    }

    // ── getMessageCount ────────────────────────────────────

    @Test
    @DisplayName("getMessageCount() — returns count from repository")
    void getMessageCount_returnsCount() {
        when(messageRepository.countByRoomIdAndIsDeletedFalse(10L)).thenReturn(42L);

        long count = messageService.getMessageCount(10L);

        assertThat(count).isEqualTo(42L);
    }

    // ── clearRoomHistory ───────────────────────────────────

    @Test
    @DisplayName("clearRoomHistory() — bulk soft-deletes all room messages")
    void clearRoomHistory_success() {
        when(messageRepository.clearRoomHistory(10L)).thenReturn(15);

        int count = messageService.clearRoomHistory(10L);

        assertThat(count).isEqualTo(15);
        verify(messageRepository).clearRoomHistory(10L);
    }
}
