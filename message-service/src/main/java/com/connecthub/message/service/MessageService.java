package com.connecthub.message.service;

import com.connecthub.message.dto.request.EditMessageRequest;
import com.connecthub.message.dto.request.ReactMessageRequest;
import com.connecthub.message.dto.request.SendDirectMessageRequest;
import com.connecthub.message.dto.request.SendMessageRequest;
import com.connecthub.message.dto.request.UpdateDeliveryStatusRequest;
import com.connecthub.message.dto.response.MessageResponse;
import com.connecthub.message.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageService {

    // ── Core send ──────────────────────────────────────────
    /**
     * Persists a new message and notifies room-service to update lastMessageAt.
     * Called by websocket-handler after receiving a CHAT_MESSAGE STOMP frame.
     */
    MessageResponse sendMessage(Long senderId, SendMessageRequest request);

    MessageResponse sendImageMessage(Long senderId, Long roomId, MultipartFile image,
                                     String content, Long replyToMessageId);

    MessageResponse sendFileMessage(Long senderId, Long roomId, MultipartFile file,
                                    String content, Long replyToMessageId);

    MessageResponse sendDirectImageMessage(Long senderId, Long recipientId, MultipartFile image,
                                           String content, Long replyToMessageId);

    MessageResponse sendDirectFileMessage(Long senderId, Long recipientId, MultipartFile file,
                                          String content, Long replyToMessageId);

    MessageResponse sendDirectMessage(Long senderId, SendDirectMessageRequest request);

    // ── Fetch ──────────────────────────────────────────────
    MessageResponse getMessageById(Long messageId, Long requesterId);

    /**
     * Returns paginated messages for a room, newest first.
     * page=0 returns the most recent {@code size} messages.
     */
    Page<MessageResponse> getMessagesByRoom(Long roomId, Long requesterId, int page, int size);

    /**
     * Returns messages sent before {@code before}, newest first.
     * Used for infinite scroll — client passes the sentAt of the oldest visible message.
     */
    Page<MessageResponse> getMessagesBefore(Long roomId, Long requesterId,
                                             LocalDateTime before, int page, int size);

    Page<MessageResponse> getDirectMessages(Long me, Long otherUserId, int page, int size);

    Page<MessageResponse> getDirectMessagesBefore(Long me, Long otherUserId,
                                                  LocalDateTime before, int page, int size);

    // ── Mutations ──────────────────────────────────────────
    MessageResponse editMessage(Long messageId, Long requesterId, EditMessageRequest request);

    void deleteMessage(Long messageId, Long requesterId);

    MessageResponse addReaction(Long messageId, Long requesterId, ReactMessageRequest request);

    MessageResponse removeReaction(Long messageId, Long requesterId, String emoji);

    // ── Admin operations ───────────────────────────────────
    void adminDeleteMessage(Long messageId);

    int clearRoomHistory(Long roomId);

    // ── Pin ────────────────────────────────────────────────
    MessageResponse pinMessage(Long messageId, Long requesterId);

    MessageResponse unpinMessage(Long messageId, Long requesterId);

    List<MessageResponse> getPinnedMessages(Long roomId, Long requesterId);

    // ── Search ─────────────────────────────────────────────
    List<MessageResponse> searchMessages(Long roomId, String keyword, Long requesterId);

    // ── Delivery status ────────────────────────────────────
    /**
     * Transitions deliveryStatus: SENT → DELIVERED or DELIVERED → READ.
     * Called by websocket-handler on DELIVERED confirmation and READ_RECEIPT events.
     */
    void updateDeliveryStatus(Long messageId, UpdateDeliveryStatusRequest request);

    // ── Unread count ───────────────────────────────────────
    long getUnreadCount(Long roomId, LocalDateTime after);

    List<MessageResponse> getUnreadMessages(Long roomId, LocalDateTime after);

    // ── Stats ──────────────────────────────────────────────
    long getMessageCount(Long roomId);

    long getTotalMessageCount();
}
