package com.connecthub.message.service.impl;

import com.connecthub.message.dto.request.EditMessageRequest;
import com.connecthub.message.dto.request.ReactMessageRequest;
import com.connecthub.message.dto.request.SendDirectMessageRequest;
import com.connecthub.message.dto.request.SendMessageRequest;
import com.connecthub.message.dto.request.UpdateDeliveryStatusRequest;
import com.connecthub.message.dto.response.MessageResponse;
import com.connecthub.message.entity.Message;
import com.connecthub.message.entity.MessageReaction;
import com.connecthub.message.exception.*;
import com.connecthub.message.repository.MessageReactionRepository;
import com.connecthub.message.repository.MessageRepository;
import com.connecthub.message.service.MediaStorageService;
import com.connecthub.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MessageServiceImpl implements MessageService {

    private final MessageRepository         messageRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final MediaStorageService       mediaStorageService;
    private final RestTemplate              restTemplate;

    @Value("${app.room-service.url}")
    private String roomServiceUrl;

    @Value("${app.pagination.max-page-size:100}")
    private int maxPageSize;

    // ── Send Message ───────────────────────────────────────

    @Override
    public MessageResponse sendMessage(Long senderId, SendMessageRequest request) {
        Message.MessageType type = parseMessageType(request.getType());

        // Validate media fields for IMAGE / FILE messages
        if ((type == Message.MessageType.IMAGE || type == Message.MessageType.FILE)
                && (request.getMediaUrl() == null || request.getMediaUrl().isBlank())) {
            throw new BadRequestException("mediaUrl is required for " + type + " messages.");
        }

        // Validate reply target exists (if set)
        if (request.getReplyToMessageId() != null) {
            messageRepository.findById(request.getReplyToMessageId())
                    .filter(m -> !m.getIsDeleted())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Reply target message not found: " + request.getReplyToMessageId()));
        }

        if ((type == Message.MessageType.TEXT || type == Message.MessageType.REACTION || type == Message.MessageType.SYSTEM)
            && (request.getContent() == null || request.getContent().isBlank())) {
            throw new BadRequestException("content is required for " + type + " messages.");
        }

        Message message = Message.builder()
                .roomId(request.getRoomId())
                .senderId(senderId)
                .content(request.getContent())
                .type(type)
                .mediaUrl(request.getMediaUrl())
                .mediaFilename(request.getMediaFilename())
                .mediaSizeKb(request.getMediaSizeKb())
                .replyToMessageId(request.getReplyToMessageId())
                .deliveryStatus(Message.DeliveryStatus.SENT)
                .build();

        message = messageRepository.save(message);
        log.info("Message sent: id={} roomId={} senderId={} type={}",
                  message.getId(), message.getRoomId(), senderId, type);

        // Notify room-service to update lastMessageAt
        notifyRoomLastMessageAt(request.getRoomId(), message.getSentAt(), senderId);

        return toResponse(message);
    }

    @Override
    public MessageResponse sendImageMessage(Long senderId, Long roomId, MultipartFile image,
                                            String content, Long replyToMessageId) {
        MediaStorageService.StoredMedia media = mediaStorageService.storeImage(image, roomId);

        SendMessageRequest request = new SendMessageRequest();
        request.setRoomId(roomId);
        request.setType(Message.MessageType.IMAGE.name());
        request.setContent(content);
        request.setMediaUrl(media.mediaUrl());
        request.setMediaFilename(media.fileName());
        request.setMediaSizeKb(media.sizeKb());
        request.setReplyToMessageId(replyToMessageId);

        return sendMessage(senderId, request);
    }

    @Override
    public MessageResponse sendFileMessage(Long senderId, Long roomId, MultipartFile file,
                                           String content, Long replyToMessageId) {
        MediaStorageService.StoredMedia media = mediaStorageService.storeFile(file, roomId);

        SendMessageRequest request = new SendMessageRequest();
        request.setRoomId(roomId);
        request.setType(Message.MessageType.FILE.name());
        request.setContent(content);
        request.setMediaUrl(media.mediaUrl());
        request.setMediaFilename(media.fileName());
        request.setMediaSizeKb(media.sizeKb());
        request.setReplyToMessageId(replyToMessageId);

        return sendMessage(senderId, request);
    }

    @Override
    public MessageResponse sendDirectImageMessage(Long senderId, Long recipientId, MultipartFile image,
                                                  String content, Long replyToMessageId) {
        // --- ENFORCE ONE-WAY COMMUNICATION ---
        org.springframework.security.core.Authentication auth = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isSenderAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN"));

        if (!isSenderAdmin) {
            try {
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                if (auth != null && auth.getCredentials() != null) headers.setBearerAuth(auth.getCredentials().toString());
                org.springframework.http.ResponseEntity<Map> response = restTemplate.exchange(
                        "http://localhost:8081/api/auth/profile/" + recipientId,
                        org.springframework.http.HttpMethod.GET,
                        new org.springframework.http.HttpEntity<>(headers), Map.class);
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                if ("PLATFORM_ADMIN".equals(data.get("role"))) {
                    throw new ForbiddenException("You cannot send messages to a Platform Administrator. This is a one-way channel.");
                }
            } catch (ForbiddenException fe) { throw fe; } catch (Exception e) {}
        }
        
        final Long effectiveSenderId = senderId;
        // -------------------------------------

        MediaStorageService.StoredMedia media = mediaStorageService.storeImage(image, 0L);

        SendDirectMessageRequest request = new SendDirectMessageRequest();
        request.setRecipientId(recipientId);
        request.setType(Message.MessageType.IMAGE.name());
        request.setContent(content);
        request.setMediaUrl(media.mediaUrl());
        request.setMediaFilename(media.fileName());
        request.setMediaSizeKb(media.sizeKb());
        request.setReplyToMessageId(replyToMessageId);

        return sendDirectMessage(effectiveSenderId, request);
    }

    @Override
    public MessageResponse sendDirectFileMessage(Long senderId, Long recipientId, MultipartFile file,
                                                 String content, Long replyToMessageId) {
        // --- ENFORCE ONE-WAY COMMUNICATION ---
        org.springframework.security.core.Authentication auth = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isSenderAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN"));

        if (!isSenderAdmin) {
            try {
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                if (auth != null && auth.getCredentials() != null) headers.setBearerAuth(auth.getCredentials().toString());
                org.springframework.http.ResponseEntity<Map> response = restTemplate.exchange(
                        "http://localhost:8081/api/auth/profile/" + recipientId,
                        org.springframework.http.HttpMethod.GET,
                        new org.springframework.http.HttpEntity<>(headers), Map.class);
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                if ("PLATFORM_ADMIN".equals(data.get("role"))) {
                    throw new ForbiddenException("You cannot send messages to a Platform Administrator. This is a one-way channel.");
                }
            } catch (ForbiddenException fe) { throw fe; } catch (Exception e) {}
        }
        
        final Long effectiveSenderId = senderId;
        // -------------------------------------

        MediaStorageService.StoredMedia media = mediaStorageService.storeFile(file, 0L);

        SendDirectMessageRequest request = new SendDirectMessageRequest();
        request.setRecipientId(recipientId);
        request.setType(Message.MessageType.FILE.name());
        request.setContent(content);
        request.setMediaUrl(media.mediaUrl());
        request.setMediaFilename(media.fileName());
        request.setMediaSizeKb(media.sizeKb());
        request.setReplyToMessageId(replyToMessageId);

        return sendDirectMessage(effectiveSenderId, request);
    }

    @Override
    public MessageResponse sendDirectMessage(Long senderId, SendDirectMessageRequest request) {
        // --- ENFORCE ONE-WAY COMMUNICATION ---
        // If sender is NOT an admin, check if recipient IS an admin.
        org.springframework.security.core.Authentication auth = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        
        boolean isSenderAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN"));

        if (!isSenderAdmin) {
            try {
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                if (auth != null && auth.getCredentials() != null) {
                    headers.setBearerAuth(auth.getCredentials().toString());
                }
                org.springframework.http.ResponseEntity<Map> response = restTemplate.exchange(
                        "http://localhost:8081/api/auth/profile/" + request.getRecipientId(),
                        org.springframework.http.HttpMethod.GET,
                        new org.springframework.http.HttpEntity<>(headers), Map.class);
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                if ("PLATFORM_ADMIN".equals(data.get("role"))) {
                    throw new ForbiddenException("You cannot send messages to a Platform Administrator. This is a one-way channel.");
                }
            } catch (ForbiddenException fe) {
                throw fe;
            } catch (Exception e) {
                log.warn("Could not fetch role for recipient: {}", request.getRecipientId());
            }
        }
        
        final Long effectiveSenderId = senderId;

        if (request.getRecipientId().equals(senderId) && !isSenderAdmin) {
            throw new BadRequestException("You cannot send a direct message to yourself.");
        }
        // -------------------------------------

        Message.MessageType type = parseMessageType(request.getType());

        if ((type == Message.MessageType.IMAGE || type == Message.MessageType.FILE)
                && (request.getMediaUrl() == null || request.getMediaUrl().isBlank())) {
            throw new BadRequestException("mediaUrl is required for " + type + " messages.");
        }

        if ((type == Message.MessageType.TEXT || type == Message.MessageType.REACTION || type == Message.MessageType.SYSTEM)
                && (request.getContent() == null || request.getContent().isBlank())) {
            throw new BadRequestException("content is required for " + type + " messages.");
        }

        if (request.getReplyToMessageId() != null) {
            messageRepository.findById(request.getReplyToMessageId())
                    .filter(m -> !m.getIsDeleted())
                    .filter(m -> isSameDirectConversation(effectiveSenderId, request.getRecipientId(), m))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Reply target message not found in this direct conversation: " +
                            request.getReplyToMessageId()));
        }

        Message message = Message.builder()
                .roomId(null)
                .senderId(effectiveSenderId)
                .recipientId(request.getRecipientId())
                .content(request.getContent())
                .type(type)
                .mediaUrl(request.getMediaUrl())
                .mediaFilename(request.getMediaFilename())
                .mediaSizeKb(request.getMediaSizeKb())
                .replyToMessageId(request.getReplyToMessageId())
                .deliveryStatus(Message.DeliveryStatus.SENT)
                .build();

        message = messageRepository.save(message);
        log.info("Direct message sent: id={} senderId={} recipientId={} type={}",
                 message.getId(), effectiveSenderId, request.getRecipientId(), type);

        // Ask room-service for DM room id to update last message & unread count
        Long dmRoomId = null;
        try {
            org.springframework.http.HttpHeaders dmHeaders = new org.springframework.http.HttpHeaders();
            if (auth != null && auth.getCredentials() != null) {
                dmHeaders.setBearerAuth(auth.getCredentials().toString());
            }
            org.springframework.http.ResponseEntity<Map> dmRes = restTemplate.exchange(
                    roomServiceUrl + "/rooms/dm/" + request.getRecipientId(),
                    org.springframework.http.HttpMethod.POST, new org.springframework.http.HttpEntity<>(null, dmHeaders), Map.class);
            Map<String, Object> dmRoom = (Map<String, Object>) ((Map<String, Object>) dmRes.getBody()).get("data");
            dmRoomId = ((Number) dmRoom.get("id")).longValue();
        } catch (Exception e) {
            log.warn("Could not get or create DM room for unread tracking: {}", e.getMessage());
        }

        if (dmRoomId != null) {
            notifyRoomLastMessageAt(dmRoomId, message.getSentAt(), effectiveSenderId);
        }

        return toResponse(message);
    }

    // ── Get Message By Id ──────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public MessageResponse getMessageById(Long messageId, Long requesterId) {
        Message message = findMessage(messageId);
        return toResponse(message);
    }

    // ── Get Messages By Room (paginated) ──────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessagesByRoom(Long roomId, Long requesterId,
                                                    int page, int size) {
        size = Math.min(size, maxPageSize);
        Pageable pageable = PageRequest.of(page, size);
        Page<Message> messages = messageRepository
            .findByRoomIdAndIsDeletedFalseOrderBySentAtDesc(roomId, pageable);
        return toResponsePage(messages);
    }

    // ── Get Messages Before Cursor ─────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessagesBefore(Long roomId, Long requesterId,
                                                    LocalDateTime before,
                                                    int page, int size) {
        size = Math.min(size, maxPageSize);
        Pageable pageable = PageRequest.of(page, size);
        Page<Message> messages = messageRepository
                .findByRoomIdAndSentAtBeforeAndIsDeletedFalseOrderBySentAtDesc(
                roomId, before, pageable);
        return toResponsePage(messages);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MessageResponse> getDirectMessages(Long me, Long otherUserId, int page, int size) {
        size = Math.min(size, maxPageSize);
        Pageable pageable = PageRequest.of(page, size);
        Page<Message> messages = messageRepository.findDirectConversation(me, otherUserId, pageable);
        return toResponsePage(messages);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MessageResponse> getDirectMessagesBefore(Long me, Long otherUserId,
                                                         LocalDateTime before,
                                                         int page, int size) {
        size = Math.min(size, maxPageSize);
        Pageable pageable = PageRequest.of(page, size);
        Page<Message> messages = messageRepository
            .findDirectConversationBefore(me, otherUserId, before, pageable);
        return toResponsePage(messages);
    }

    // ── Edit Message ───────────────────────────────────────

    @Override
    public MessageResponse editMessage(Long messageId, Long requesterId,
                                        EditMessageRequest request) {
        Message message = findMessage(messageId);
        assertNotDeleted(message);
        assertSender(message, requesterId);

        if (message.getType() != Message.MessageType.TEXT) {
            throw new BadRequestException("Only TEXT messages can be edited.");
        }

        String newContent = request.getContent();
        if (newContent == null || newContent.isBlank()) {
            throw new BadRequestException("Edited content cannot be empty.");
        }

        message.setContent(newContent);
        message.setIsEdited(true);
        message = messageRepository.save(message);

        log.info("Message edited: id={} by userId={}", messageId, requesterId);
        return toResponse(message);
    }

    // ── Delete Message ─────────────────────────────────────

    @Override
    public void deleteMessage(Long messageId, Long requesterId) {
        Message message = findMessage(messageId);
        assertNotDeleted(message);
        assertSender(message, requesterId);

        softDelete(message);
        log.info("Message soft-deleted: id={} by userId={}", messageId, requesterId);
    }

    @Override
    public MessageResponse addReaction(Long messageId, Long requesterId, ReactMessageRequest request) {
        Message message = findMessage(messageId);
        assertNotDeleted(message);

        String emoji = normalizeEmoji(request.getEmoji());
        boolean exists = messageReactionRepository
                .existsByMessageIdAndUserIdAndEmoji(messageId, requesterId, emoji);

        if (!exists) {
            messageReactionRepository.save(MessageReaction.builder()
                    .messageId(messageId)
                    .userId(requesterId)
                    .emoji(emoji)
                    .build());
        }

        return toResponse(message);
    }

    @Override
    public MessageResponse removeReaction(Long messageId, Long requesterId, String emoji) {
        Message message = findMessage(messageId);
        assertNotDeleted(message);

        String normalized = normalizeEmoji(emoji);
        messageReactionRepository.deleteByMessageIdAndUserIdAndEmoji(messageId, requesterId, normalized);
        return toResponse(message);
    }

    // ── Admin Delete ───────────────────────────────────────

    @Override
    public void adminDeleteMessage(Long messageId) {
        Message message = findMessage(messageId);
        if (!message.getIsDeleted()) {
            softDelete(message);
        }
        log.info("Message force-deleted by admin: id={}", messageId);
    }

    // ── Clear Room History (Room Admin) ───────────────────

    @Override
    public int clearRoomHistory(Long roomId) {
        List<Long> messageIds = messageRepository.findIdsByRoomId(roomId);
        int count = messageRepository.clearRoomHistory(roomId);
        if (!messageIds.isEmpty()) {
            messageReactionRepository.deleteByMessageIdIn(messageIds);
        }
        log.info("Room history cleared: roomId={} messagesAffected={}", roomId, count);
        return count;
    }

    // ── Pin / Unpin ────────────────────────────────────────

    @Override
    public MessageResponse pinMessage(Long messageId, Long requesterId) {
        Message message = findMessage(messageId);
        assertNotDeleted(message);
        message.setIsPinned(true);
        message = messageRepository.save(message);
        log.info("Message pinned: id={} by userId={}", messageId, requesterId);
        return toResponse(message);
    }

    @Override
    public MessageResponse unpinMessage(Long messageId, Long requesterId) {
        Message message = findMessage(messageId);
        message.setIsPinned(false);
        message = messageRepository.save(message);
        log.info("Message unpinned: id={} by userId={}", messageId, requesterId);
        return toResponse(message);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> getPinnedMessages(Long roomId, Long requesterId) {
        List<Message> messages = messageRepository
                .findByRoomIdAndIsPinnedTrueAndIsDeletedFalseOrderBySentAtDesc(roomId)
            .stream().toList();
        return toResponseList(messages);
    }

    // ── Search ─────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> searchMessages(Long roomId, String keyword, Long requesterId) {
        if (keyword == null || keyword.isBlank()) {
            throw new BadRequestException("Search keyword cannot be empty.");
        }
        List<Message> messages = messageRepository.searchInRoom(roomId, keyword.trim());
        return toResponseList(messages);
    }

    // ── Delivery Status ────────────────────────────────────

    @Override
    public void updateDeliveryStatus(Long messageId, UpdateDeliveryStatusRequest request) {
        Message.DeliveryStatus status = parseDeliveryStatus(request.getStatus());
        int updated = messageRepository.updateDeliveryStatus(messageId, status);
        if (updated == 0) {
            log.debug("Delivery status update skipped (already READ or not found): id={}", messageId);
        } else {
            log.debug("Delivery status updated: id={} → {}", messageId, status);
        }
    }

    // ── Unread Count ───────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(Long roomId, LocalDateTime after) {
        if (after == null) return 0;
        return messageRepository.countUnreadMessages(roomId, after);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> getUnreadMessages(Long roomId, LocalDateTime after) {
        if (after == null) return List.of();
        List<Message> messages = messageRepository.findUnreadMessages(roomId, after);
        return toResponseList(messages);
    }

    // ── Stats ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public long getMessageCount(Long roomId) {
        return messageRepository.countByRoomIdAndIsDeletedFalse(roomId);
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalMessageCount() {
        return messageRepository.count();
    }

    // ── Private Helpers ────────────────────────────────────

    private Message findMessage(Long messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Message not found: " + messageId));
    }

    private void assertNotDeleted(Message message) {
        if (message.getIsDeleted()) {
            throw new BadRequestException("Message has already been deleted.");
        }
    }

    private void assertSender(Message message, Long requesterId) {
        if (!message.getSenderId().equals(requesterId)) {
            throw new ForbiddenException("You can only modify your own messages.");
        }
    }

    private void softDelete(Message message) {
        message.setIsDeleted(true);
        message.setContent("[deleted]");
        message.setMediaUrl(null);
        messageReactionRepository.deleteByMessageId(message.getId());
        messageRepository.save(message);
    }

    private MessageResponse toResponse(Message message) {
        Map<String, Long> reactions = buildReactionMapForMessages(List.of(message.getId()))
                .getOrDefault(message.getId(), Map.of());
        return toResponse(message, reactions);
        }

        private Page<MessageResponse> toResponsePage(Page<Message> messages) {
        Map<Long, Map<String, Long>> reactionMap = buildReactionMapForMessages(
            messages.getContent().stream().map(Message::getId).toList());

        return messages.map(message ->
            toResponse(message, reactionMap.getOrDefault(message.getId(), Map.of())));
        }

        private List<MessageResponse> toResponseList(List<Message> messages) {
        Map<Long, Map<String, Long>> reactionMap = buildReactionMapForMessages(
            messages.stream().map(Message::getId).toList());

        return messages.stream()
            .map(message -> toResponse(message, reactionMap.getOrDefault(message.getId(), Map.of())))
            .collect(Collectors.toList());
        }

        private MessageResponse toResponse(Message message, Map<String, Long> reactions) {
        MessageResponse response = MessageResponse.from(message);
        response.setReactions(reactions);
        return response;
    }

    private Map<Long, Map<String, Long>> buildReactionMapForMessages(Collection<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Map<String, Long>> grouped = new HashMap<>();
        messageReactionRepository.countGroupedByMessageIds(messageIds)
                .forEach(row -> grouped
                        .computeIfAbsent(row.getMessageId(), ignored -> new HashMap<>())
                        .put(row.getEmoji(), row.getCount()));
        return grouped;
    }

    private String normalizeEmoji(String emoji) {
        if (emoji == null || emoji.isBlank()) {
            throw new BadRequestException("emoji is required");
        }
        String normalized = emoji.trim();
        if (normalized.length() > 50) {
            throw new BadRequestException("emoji must not exceed 50 characters");
        }
        return normalized;
    }

    /**
     * Calls room-service to update the room's lastMessageAt timestamp.
     * Fire-and-forget — failure is logged but does not block the send response.
     */
    private void notifyRoomLastMessageAt(Long roomId, LocalDateTime sentAt, Long senderId) {
        if (roomId == null) {
            return;
        }
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            org.springframework.security.core.Authentication auth = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getCredentials() != null) {
                headers.setBearerAuth(auth.getCredentials().toString());
            }

            String url = roomServiceUrl + "/rooms/" + roomId +
                         "/last-message?sentAt=" + sentAt;
            if (senderId != null) {
                url += "&senderId=" + senderId;
            }
            restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT, 
                new org.springframework.http.HttpEntity<>(null, headers), Void.class);
            log.debug("Notified room-service: roomId={} lastMessageAt={}", roomId, sentAt);
        } catch (Exception ex) {
            log.warn("Failed to notify room-service of lastMessageAt: roomId={} error={}",
                      roomId, ex.getMessage());
        }
    }

    private Message.MessageType parseMessageType(String type) {
        if (type == null) return Message.MessageType.TEXT;
        try {
            return Message.MessageType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "Invalid message type: " + type +
                    ". Use TEXT, IMAGE, FILE, REACTION, or SYSTEM.");
        }
    }

    private Message.DeliveryStatus parseDeliveryStatus(String status) {
        try {
            return Message.DeliveryStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "Invalid delivery status: " + status +
                    ". Use SENT, DELIVERED, or READ.");
        }
    }

    private boolean isSameDirectConversation(Long userA, Long userB, Message m) {
        if (m.getRoomId() != null || m.getRecipientId() == null) {
            return false;
        }

        return (m.getSenderId().equals(userA) && m.getRecipientId().equals(userB))
                || (m.getSenderId().equals(userB) && m.getRecipientId().equals(userA));
    }
}
