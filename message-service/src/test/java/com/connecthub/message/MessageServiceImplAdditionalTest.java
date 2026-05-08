package com.connecthub.message;

import com.connecthub.message.dto.request.ReactMessageRequest;
import com.connecthub.message.dto.request.SendDirectMessageRequest;
import com.connecthub.message.dto.response.MessageResponse;
import com.connecthub.message.entity.Message;
import com.connecthub.message.repository.MessageReactionRepository;
import com.connecthub.message.repository.MessageRepository;
import com.connecthub.message.service.MediaStorageService;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageServiceImpl Additional Tests")
class MessageServiceImplAdditionalTest {

    @Mock private MessageRepository messageRepository;
    @Mock private MessageReactionRepository messageReactionRepository;
    @Mock private MediaStorageService mediaStorageService;
    @Mock private RestTemplate restTemplate;

    @InjectMocks private MessageServiceImpl messageService;

    private Message textMessage;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(messageService, "roomServiceUrl", "http://localhost:8082/api");
        ReflectionTestUtils.setField(messageService, "maxPageSize", 100);

        lenient().when(messageReactionRepository.countGroupedByMessageIds(any()))
                .thenReturn(java.util.Collections.emptyList());

        textMessage = Message.builder()
                .id(1L)
                .roomId(10L)
                .senderId(100L)
                .recipientId(200L)
                .content("Hello, World!")
                .type(Message.MessageType.TEXT)
                .isEdited(false)
                .isDeleted(false)
                .isPinned(false)
                .deliveryStatus(Message.DeliveryStatus.SENT)
                .sentAt(LocalDateTime.now())
                .build();
                
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "test-token", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))
        );
    }

    @Test
    void sendImageMessage_success() {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "img".getBytes());
        MediaStorageService.StoredMedia media = new MediaStorageService.StoredMedia("test.jpg", "url", 100L);
        when(mediaStorageService.storeImage(file, 10L)).thenReturn(media);
        when(messageRepository.save(any())).thenAnswer(i -> {
            Message m = i.getArgument(0);
            m.setId(2L);
            return m;
        });

        MessageResponse res = messageService.sendImageMessage(100L, 10L, file, "caption", null);
        assertThat(res.getType()).isEqualTo("IMAGE");
        assertThat(res.getMediaUrl()).isEqualTo("url");
    }

    @Test
    void sendFileMessage_success() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "txt".getBytes());
        MediaStorageService.StoredMedia media = new MediaStorageService.StoredMedia("test.txt", "url", 100L);
        when(mediaStorageService.storeFile(file, 10L)).thenReturn(media);
        when(messageRepository.save(any())).thenAnswer(i -> {
            Message m = i.getArgument(0);
            m.setId(2L);
            return m;
        });

        MessageResponse res = messageService.sendFileMessage(100L, 10L, file, "caption", null);
        assertThat(res.getType()).isEqualTo("FILE");
        assertThat(res.getMediaUrl()).isEqualTo("url");
    }

    @Test
    void sendDirectMessage_success() {
        SendDirectMessageRequest req = new SendDirectMessageRequest();
        req.setRecipientId(200L);
        req.setContent("Direct");
        req.setType("TEXT");

        when(restTemplate.exchange(contains("/auth/profile/"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", Map.of("role", "USER"))));

        when(messageRepository.save(any())).thenAnswer(i -> {
            Message m = i.getArgument(0);
            m.setId(2L);
            return m;
        });

        when(restTemplate.exchange(contains("/rooms/dm/"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", Map.of("id", 999))));

        MessageResponse res = messageService.sendDirectMessage(100L, req);
        assertThat(res.getRecipientId()).isEqualTo(200L);
    }

    @Test
    void sendDirectImageMessage_success() {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "img".getBytes());
        MediaStorageService.StoredMedia media = new MediaStorageService.StoredMedia("test.jpg", "url", 100L);
        when(mediaStorageService.storeImage(file, 0L)).thenReturn(media);
        when(messageRepository.save(any())).thenAnswer(i -> {
            Message m = i.getArgument(0);
            m.setId(2L);
            return m;
        });
        when(restTemplate.exchange(contains("/auth/profile/"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", Map.of("role", "USER"))));

        MessageResponse res = messageService.sendDirectImageMessage(100L, 200L, file, "caption", null);
        assertThat(res.getType()).isEqualTo("IMAGE");
    }

    @Test
    void sendDirectFileMessage_success() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "txt".getBytes());
        MediaStorageService.StoredMedia media = new MediaStorageService.StoredMedia("test.txt", "url", 100L);
        when(mediaStorageService.storeFile(file, 0L)).thenReturn(media);
        when(messageRepository.save(any())).thenAnswer(i -> {
            Message m = i.getArgument(0);
            m.setId(2L);
            return m;
        });
        when(restTemplate.exchange(contains("/auth/profile/"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", Map.of("role", "USER"))));

        MessageResponse res = messageService.sendDirectFileMessage(100L, 200L, file, "caption", null);
        assertThat(res.getType()).isEqualTo("FILE");
    }

    @Test
    void getMessagesBefore_success() {
        Page<Message> page = new PageImpl<>(List.of(textMessage));
        when(messageRepository.findByRoomIdAndSentAtBeforeAndIsDeletedFalseOrderBySentAtDesc(anyLong(), any(), any(Pageable.class)))
                .thenReturn(page);
        Page<MessageResponse> res = messageService.getMessagesBefore(10L, 100L, LocalDateTime.now(), 0, 10);
        assertThat(res.getContent()).hasSize(1);
    }

    @Test
    void getDirectMessages_success() {
        Page<Message> page = new PageImpl<>(List.of(textMessage));
        when(messageRepository.findDirectConversation(anyLong(), anyLong(), any(Pageable.class))).thenReturn(page);
        Page<MessageResponse> res = messageService.getDirectMessages(100L, 200L, 0, 10);
        assertThat(res.getContent()).hasSize(1);
    }

    @Test
    void getDirectMessagesBefore_success() {
        Page<Message> page = new PageImpl<>(List.of(textMessage));
        when(messageRepository.findDirectConversationBefore(anyLong(), anyLong(), any(), any(Pageable.class))).thenReturn(page);
        Page<MessageResponse> res = messageService.getDirectMessagesBefore(100L, 200L, LocalDateTime.now(), 0, 10);
        assertThat(res.getContent()).hasSize(1);
    }

    @Test
    void addReaction_success() {
        ReactMessageRequest req = new ReactMessageRequest();
        req.setEmoji("👍");

        when(messageRepository.findById(1L)).thenReturn(Optional.of(textMessage));
        when(messageReactionRepository.existsByMessageIdAndUserIdAndEmoji(1L, 100L, "👍")).thenReturn(false);

        MessageResponse res = messageService.addReaction(1L, 100L, req);
        verify(messageReactionRepository).save(any());
        assertThat(res.getId()).isEqualTo(1L);
    }

    @Test
    void removeReaction_success() {
        when(messageRepository.findById(1L)).thenReturn(Optional.of(textMessage));
        MessageResponse res = messageService.removeReaction(1L, 100L, "👍");
        verify(messageReactionRepository).deleteByMessageIdAndUserIdAndEmoji(1L, 100L, "👍");
        assertThat(res.getId()).isEqualTo(1L);
    }

    @Test
    void getPinnedMessages_success() {
        when(messageRepository.findByRoomIdAndIsPinnedTrueAndIsDeletedFalseOrderBySentAtDesc(10L))
                .thenReturn(List.of(textMessage));
        List<MessageResponse> res = messageService.getPinnedMessages(10L, 100L);
        assertThat(res).hasSize(1);
    }

    @Test
    void getUnreadMessages_success() {
        when(messageRepository.findUnreadMessages(eq(10L), any())).thenReturn(List.of(textMessage));
        List<MessageResponse> res = messageService.getUnreadMessages(10L, LocalDateTime.now());
        assertThat(res).hasSize(1);
    }

    @Test
    void getTotalMessageCount_success() {
        when(messageRepository.count()).thenReturn(500L);
        long count = messageService.getTotalMessageCount();
        assertThat(count).isEqualTo(500L);
    }

    @Test
    void sendDirectMessage_toSelf_throwsBadRequest() {
        SendDirectMessageRequest req = new SendDirectMessageRequest();
        req.setRecipientId(100L);
        req.setContent("Direct");
        req.setType("TEXT");

        when(restTemplate.exchange(contains("/auth/profile/"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", Map.of("role", "USER"))));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.sendDirectMessage(100L, req))
                .isInstanceOf(com.connecthub.message.exception.BadRequestException.class)
                .hasMessageContaining("You cannot send a direct message to yourself.");
    }

    @Test
    void sendDirectMessage_invalidType_throwsBadRequest() {
        SendDirectMessageRequest req = new SendDirectMessageRequest();
        req.setRecipientId(200L);
        req.setContent("Direct");
        req.setType("INVALID");

        when(restTemplate.exchange(contains("/auth/profile/"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", Map.of("role", "USER"))));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.sendDirectMessage(100L, req))
                .isInstanceOf(com.connecthub.message.exception.BadRequestException.class)
                .hasMessageContaining("Invalid message type");
    }

    @Test
    void sendDirectMessage_missingContent_throwsBadRequest() {
        SendDirectMessageRequest req = new SendDirectMessageRequest();
        req.setRecipientId(200L);
        req.setType("TEXT");

        when(restTemplate.exchange(contains("/auth/profile/"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", Map.of("role", "USER"))));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.sendDirectMessage(100L, req))
                .isInstanceOf(com.connecthub.message.exception.BadRequestException.class)
                .hasMessageContaining("content is required");
    }

    @Test
    void sendDirectMessage_imageMissingUrl_throwsBadRequest() {
        SendDirectMessageRequest req = new SendDirectMessageRequest();
        req.setRecipientId(200L);
        req.setType("IMAGE");
        req.setMediaUrl(null);

        when(restTemplate.exchange(contains("/auth/profile/"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", Map.of("role", "USER"))));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.sendDirectMessage(100L, req))
                .isInstanceOf(com.connecthub.message.exception.BadRequestException.class)
                .hasMessageContaining("mediaUrl is required");
    }

    @Test
    void sendDirectMessage_replyTargetNotFound_throwsNotFound() {
        SendDirectMessageRequest req = new SendDirectMessageRequest();
        req.setRecipientId(200L);
        req.setType("TEXT");
        req.setContent("content");
        req.setReplyToMessageId(999L);

        when(restTemplate.exchange(contains("/auth/profile/"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", Map.of("role", "USER"))));
        when(messageRepository.findById(999L)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.sendDirectMessage(100L, req))
                .isInstanceOf(com.connecthub.message.exception.ResourceNotFoundException.class)
                .hasMessageContaining("Reply target message not found");
    }

    @Test
    void sendMessage_missingContent_throwsBadRequest() {
        com.connecthub.message.dto.request.SendMessageRequest req = new com.connecthub.message.dto.request.SendMessageRequest();
        req.setRoomId(10L);
        req.setType("TEXT");
        req.setContent(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.sendMessage(100L, req))
                .isInstanceOf(com.connecthub.message.exception.BadRequestException.class)
                .hasMessageContaining("content is required");
    }

    @Test
    void editMessage_notText_throwsBadRequest() {
        textMessage.setType(Message.MessageType.IMAGE);
        when(messageRepository.findById(1L)).thenReturn(Optional.of(textMessage));

        com.connecthub.message.dto.request.EditMessageRequest req = new com.connecthub.message.dto.request.EditMessageRequest();
        req.setContent("new content");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.editMessage(1L, 100L, req))
                .isInstanceOf(com.connecthub.message.exception.BadRequestException.class)
                .hasMessageContaining("Only TEXT messages can be edited.");
    }

    @Test
    void editMessage_emptyContent_throwsBadRequest() {
        when(messageRepository.findById(1L)).thenReturn(Optional.of(textMessage));

        com.connecthub.message.dto.request.EditMessageRequest req = new com.connecthub.message.dto.request.EditMessageRequest();
        req.setContent("   ");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.editMessage(1L, 100L, req))
                .isInstanceOf(com.connecthub.message.exception.BadRequestException.class)
                .hasMessageContaining("Edited content cannot be empty.");
    }

    @Test
    void deleteMessage_alreadyDeleted_throwsBadRequest() {
        textMessage.setIsDeleted(true);
        when(messageRepository.findById(1L)).thenReturn(Optional.of(textMessage));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.deleteMessage(1L, 100L))
                .isInstanceOf(com.connecthub.message.exception.BadRequestException.class)
                .hasMessageContaining("Message has already been deleted.");
    }

    @Test
    void deleteMessage_notSender_throwsForbidden() {
        when(messageRepository.findById(1L)).thenReturn(Optional.of(textMessage));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.deleteMessage(1L, 999L))
                .isInstanceOf(com.connecthub.message.exception.ForbiddenException.class)
                .hasMessageContaining("You can only modify your own messages.");
    }

    @Test
    void adminDeleteMessage_success() {
        when(messageRepository.findById(1L)).thenReturn(Optional.of(textMessage));
        messageService.adminDeleteMessage(1L);
        assertThat(textMessage.getIsDeleted()).isTrue();
        verify(messageRepository).save(any());
    }

    @Test
    void updateDeliveryStatus_success() {
        com.connecthub.message.dto.request.UpdateDeliveryStatusRequest req = new com.connecthub.message.dto.request.UpdateDeliveryStatusRequest();
        req.setStatus("DELIVERED");

        when(messageRepository.updateDeliveryStatus(1L, Message.DeliveryStatus.DELIVERED)).thenReturn(1);
        messageService.updateDeliveryStatus(1L, req);
        verify(messageRepository).updateDeliveryStatus(1L, Message.DeliveryStatus.DELIVERED);
    }

    @Test
    void updateDeliveryStatus_invalidStatus_throwsBadRequest() {
        com.connecthub.message.dto.request.UpdateDeliveryStatusRequest req = new com.connecthub.message.dto.request.UpdateDeliveryStatusRequest();
        req.setStatus("INVALID");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.updateDeliveryStatus(1L, req))
                .isInstanceOf(com.connecthub.message.exception.BadRequestException.class)
                .hasMessageContaining("Invalid delivery status");
    }

    @Test
    void addReaction_invalidEmoji_throwsBadRequest() {
        ReactMessageRequest req = new ReactMessageRequest();
        req.setEmoji("   ");
        when(messageRepository.findById(1L)).thenReturn(Optional.of(textMessage));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.addReaction(1L, 100L, req))
                .isInstanceOf(com.connecthub.message.exception.BadRequestException.class)
                .hasMessageContaining("emoji is required");
    }

    @Test
    void addReaction_emojiTooLong_throwsBadRequest() {
        ReactMessageRequest req = new ReactMessageRequest();
        req.setEmoji("A".repeat(51));
        when(messageRepository.findById(1L)).thenReturn(Optional.of(textMessage));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.addReaction(1L, 100L, req))
                .isInstanceOf(com.connecthub.message.exception.BadRequestException.class)
                .hasMessageContaining("emoji must not exceed 50 characters");
    }

    @Test
    void getUnreadCount_success() {
        when(messageRepository.countUnreadMessages(10L, LocalDateTime.MAX)).thenReturn(5L);
        long count = messageService.getUnreadCount(10L, LocalDateTime.MAX);
        assertThat(count).isEqualTo(5L);
    }

    @Test
    void clearRoomHistory_success() {
        when(messageRepository.findIdsByRoomId(10L)).thenReturn(List.of(1L, 2L));
        when(messageRepository.clearRoomHistory(10L)).thenReturn(2);
        int count = messageService.clearRoomHistory(10L);
        assertThat(count).isEqualTo(2);
        verify(messageReactionRepository).deleteByMessageIdIn(List.of(1L, 2L));
    }

    @Test
    void getPinnedMessages_empty() {
        when(messageRepository.findByRoomIdAndIsPinnedTrueAndIsDeletedFalseOrderBySentAtDesc(10L))
                .thenReturn(List.of());
        List<MessageResponse> res = messageService.getPinnedMessages(10L, 100L);
        assertThat(res).isEmpty();
    }

    @Test
    void getUnreadMessages_empty() {
        when(messageRepository.findUnreadMessages(eq(10L), any())).thenReturn(List.of());
        List<MessageResponse> res = messageService.getUnreadMessages(10L, LocalDateTime.now());
        assertThat(res).isEmpty();
    }

    @Test
    void getUnreadCount_nullAfter() {
        long count = messageService.getUnreadCount(10L, null);
        assertThat(count).isEqualTo(0L);
    }

    @Test
    void getUnreadMessages_nullAfter() {
        List<MessageResponse> res = messageService.getUnreadMessages(10L, null);
        assertThat(res).isEmpty();
    }

    @Test
    void sendDirectMessage_recipientRoleFetchThrowsException() {
        SendDirectMessageRequest req = new SendDirectMessageRequest();
        req.setRecipientId(200L);
        req.setContent("Direct");
        req.setType("TEXT");

        when(restTemplate.exchange(contains("/auth/profile/"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("auth service down"));

        when(messageRepository.save(any())).thenReturn(textMessage);

        when(restTemplate.exchange(contains("/rooms/dm/"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", Map.of("id", 999))));

        MessageResponse res = messageService.sendDirectMessage(100L, req);
        assertThat(res.getRecipientId()).isEqualTo(200L);
    }

    @Test
    void sendDirectMessage_replyTargetNotSameConversation_throwsNotFound() {
        SendDirectMessageRequest req = new SendDirectMessageRequest();
        req.setRecipientId(200L);
        req.setContent("Direct");
        req.setType("TEXT");
        req.setReplyToMessageId(999L);

        when(restTemplate.exchange(contains("/auth/profile/"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", Map.of("role", "USER"))));

        Message otherConvoMsg = Message.builder().isDeleted(false).senderId(100L).recipientId(300L).build();
        when(messageRepository.findById(999L)).thenReturn(Optional.of(otherConvoMsg));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.sendDirectMessage(100L, req))
                .isInstanceOf(com.connecthub.message.exception.ResourceNotFoundException.class)
                .hasMessageContaining("Reply target message not found in this direct conversation");
    }

    @Test
    void editMessage_nullContent_throwsBadRequest() {
        when(messageRepository.findById(1L)).thenReturn(Optional.of(textMessage));
        com.connecthub.message.dto.request.EditMessageRequest req = new com.connecthub.message.dto.request.EditMessageRequest();
        req.setContent(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.editMessage(1L, 100L, req))
                .isInstanceOf(com.connecthub.message.exception.BadRequestException.class)
                .hasMessageContaining("Edited content cannot be empty.");
    }

    @Test
    void searchMessages_emptyKeyword_throwsBadRequest() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.searchMessages(10L, "   ", 100L))
                .isInstanceOf(com.connecthub.message.exception.BadRequestException.class)
                .hasMessageContaining("Search keyword cannot be empty.");
    }

    @Test
    void searchMessages_success() {
        when(messageRepository.searchInRoom(10L, "test")).thenReturn(List.of(textMessage));
        List<MessageResponse> res = messageService.searchMessages(10L, "test", 100L);
        assertThat(res).hasSize(1);
    }

    @Test
    void parseMessageType_invalidType_throwsBadRequest() {
        com.connecthub.message.dto.request.SendMessageRequest req = new com.connecthub.message.dto.request.SendMessageRequest();
        req.setRoomId(10L);
        req.setType("UNKNOWN");
        req.setContent("test");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.sendMessage(100L, req))
                .isInstanceOf(com.connecthub.message.exception.BadRequestException.class)
                .hasMessageContaining("Invalid message type: UNKNOWN");
    }

    @Test
    void sendMessage_replyTargetDeleted_throwsNotFound() {
        Message deletedMsg = Message.builder().isDeleted(true).build();
        when(messageRepository.findById(999L)).thenReturn(Optional.of(deletedMsg));

        com.connecthub.message.dto.request.SendMessageRequest req = new com.connecthub.message.dto.request.SendMessageRequest();
        req.setRoomId(10L);
        req.setType("TEXT");
        req.setContent("test");
        req.setReplyToMessageId(999L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.sendMessage(100L, req))
                .isInstanceOf(com.connecthub.message.exception.ResourceNotFoundException.class)
                .hasMessageContaining("Reply target message not found");
    }

    @Test
    void sendDirectMessage_recipientIsAdmin_throwsForbidden() {
        SendDirectMessageRequest req = new SendDirectMessageRequest();
        req.setRecipientId(200L);
        req.setContent("Direct");
        req.setType("TEXT");

        when(restTemplate.exchange(contains("/auth/profile/"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", Map.of("role", "PLATFORM_ADMIN"))));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.sendDirectMessage(100L, req))
                .isInstanceOf(com.connecthub.message.exception.ForbiddenException.class)
                .hasMessageContaining("You cannot send messages to a Platform Administrator");
    }

    @Test
    void sendMessage_notifyRoomServiceFails_doesNotThrow() {
        com.connecthub.message.dto.request.SendMessageRequest req = new com.connecthub.message.dto.request.SendMessageRequest();
        req.setRoomId(10L);
        req.setType("TEXT");
        req.setContent("test");

        when(messageRepository.save(any())).thenReturn(textMessage);
        
        // Mock restTemplate to throw an exception when notifyRoomLastMessageAt is called
        when(restTemplate.exchange(contains("/rooms/10/last-message"), eq(HttpMethod.PUT), any(), eq(Void.class)))
                .thenThrow(new RuntimeException("Room service down"));

        // Should successfully send the message and just log the error
        MessageResponse res = messageService.sendMessage(100L, req);
        assertThat(res.getId()).isEqualTo(1L);
    }
}
