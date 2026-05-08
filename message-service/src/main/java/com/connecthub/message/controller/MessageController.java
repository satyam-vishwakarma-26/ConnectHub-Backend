package com.connecthub.message.controller;

import com.connecthub.message.dto.request.EditMessageRequest;
import com.connecthub.message.dto.request.ReactMessageRequest;
import com.connecthub.message.dto.request.SendDirectMessageRequest;
import com.connecthub.message.dto.request.SendMessageRequest;
import com.connecthub.message.dto.request.UpdateDeliveryStatusRequest;
import com.connecthub.message.dto.response.ApiResponse;
import com.connecthub.message.dto.response.MessageResponse;
import com.connecthub.message.security.AuthenticatedUser;
import com.connecthub.message.service.MediaStorageService;
import com.connecthub.message.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
@Tag(name = "Messages", description = "Send, retrieve, edit, delete, search, pin, delivery status")
@SecurityRequirement(name = "bearerAuth")
public class MessageController {

    private final MessageService messageService;
    private final MediaStorageService mediaStorageService;

    // ────────────────────────────────────────────────────────
    // SEND
    // ────────────────────────────────────────────────────────

    /**
     * POST /api/messages
     *
     * Primary REST entry point for sending a message.
     * The websocket-handler also calls this internally after receiving a
     * CHAT_MESSAGE STOMP frame so the message is persisted before broadcast.
     */
    @PostMapping
    @Operation(summary = "Send a message (TEXT, IMAGE, FILE, REACTION, SYSTEM)")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @AuthenticationPrincipal AuthenticatedUser me,
            @Valid @RequestBody SendMessageRequest request) {

        MessageResponse message = messageService.sendMessage(me.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Message sent", message));
    }

    @PostMapping(value = "/room/{roomId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an image file and send it as a room message")
    public ResponseEntity<ApiResponse<MessageResponse>> sendImageMessage(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId,
            @RequestPart("image") MultipartFile image,
            @RequestPart(value = "content", required = false) String content,
            @RequestPart(value = "replyToMessageId", required = false) Long replyToMessageId) {

        MessageResponse message = messageService.sendImageMessage(
                me.getUserId(), roomId, image, content, replyToMessageId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Image message sent", message));
    }

        @PostMapping(value = "/room/{roomId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Upload a file and send it as a room message")
        public ResponseEntity<ApiResponse<MessageResponse>> sendFileMessage(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "content", required = false) String content,
            @RequestPart(value = "replyToMessageId", required = false) Long replyToMessageId) {

        MessageResponse message = messageService.sendFileMessage(
            me.getUserId(), roomId, file, content, replyToMessageId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("File message sent", message));
        }

        @PostMapping(value = "/direct/{recipientId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Upload an image and send it as a direct message")
        public ResponseEntity<ApiResponse<MessageResponse>> sendDirectImageMessage(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long recipientId,
            @RequestPart("image") MultipartFile image,
            @RequestPart(value = "content", required = false) String content,
            @RequestPart(value = "replyToMessageId", required = false) Long replyToMessageId) {

        MessageResponse message = messageService.sendDirectImageMessage(
            me.getUserId(), recipientId, image, content, replyToMessageId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Direct image message sent", message));
        }

        @PostMapping(value = "/direct/{recipientId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Upload a file and send it as a direct message")
        public ResponseEntity<ApiResponse<MessageResponse>> sendDirectFileMessage(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long recipientId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "content", required = false) String content,
            @RequestPart(value = "replyToMessageId", required = false) Long replyToMessageId) {

        MessageResponse message = messageService.sendDirectFileMessage(
            me.getUserId(), recipientId, file, content, replyToMessageId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Direct file message sent", message));
        }

    @GetMapping("/media/{fileName:.+}")
    @Operation(summary = "Download image media for a message")
    public ResponseEntity<Resource> getImage(@PathVariable String fileName) {
        Resource resource = mediaStorageService.loadAsResource(fileName);
        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        try {
            if (resource.getFile().exists()) {
                String detected = Files.probeContentType(resource.getFile().toPath());
                if (detected != null) {
                    contentType = detected;
                }
            }
        } catch (Exception ignored) {
            // fallback to application/octet-stream
        }

        ContentDisposition disposition = contentType.startsWith("image/")
                ? ContentDisposition.inline().filename(resource.getFilename()).build()
                : ContentDisposition.attachment().filename(resource.getFilename()).build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    /**
     * POST /api/messages/media/upload
     *
     * Standalone media upload — stores the file and returns the URL.
     * Does NOT create a message. Used for room avatars, profile images, etc.
     */
    @PostMapping(value = "/media/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a media file and return its URL (no message created)")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadMedia(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestPart("file") MultipartFile file) {

        MediaStorageService.StoredMedia stored = mediaStorageService.storeImage(file, 0L);
        return ResponseEntity.ok(ApiResponse.success(
                "File uploaded",
                Map.of("mediaUrl", stored.mediaUrl(), "fileName", stored.fileName())
        ));
    }

    @PostMapping("/direct")
    @Operation(summary = "Send a direct (1:1) message")
    public ResponseEntity<ApiResponse<MessageResponse>> sendDirectMessage(
            @AuthenticationPrincipal AuthenticatedUser me,
            @Valid @RequestBody SendDirectMessageRequest request) {

        MessageResponse message = messageService.sendDirectMessage(me.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Direct message sent", message));
    }

    // ────────────────────────────────────────────────────────
    // FETCH
    // ────────────────────────────────────────────────────────

    @GetMapping("/{messageId}")
    @Operation(summary = "Get a single message by ID")
    public ResponseEntity<ApiResponse<MessageResponse>> getMessageById(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long messageId) {

        MessageResponse message = messageService.getMessageById(messageId, me.getUserId());
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    /**
     * GET /api/messages/room/{roomId}?page=0&size=30
     *
     * Returns paginated messages for a room, newest first.
     * page=0 returns the most recent batch (used on room open).
     */
    @GetMapping("/room/{roomId}")
    @Operation(summary = "Get paginated messages for a room (newest first)")
    public ResponseEntity<ApiResponse<Page<MessageResponse>>> getMessagesByRoom(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "30") int size) {

        Page<MessageResponse> messages =
                messageService.getMessagesByRoom(roomId, me.getUserId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    /**
     * GET /api/messages/room/{roomId}/before?before=2026-04-01T12:00:00&page=0&size=30
     *
     * Infinite-scroll: load older messages before a cursor timestamp.
     * Client passes the sentAt of the oldest currently visible message.
     */
    @GetMapping("/room/{roomId}/before")
    @Operation(summary = "Load older messages before a sentAt cursor (infinite scroll)")
    public ResponseEntity<ApiResponse<Page<MessageResponse>>> getMessagesBefore(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "30") int size) {

        Page<MessageResponse> messages =
                messageService.getMessagesBefore(roomId, me.getUserId(), before, page, size);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @GetMapping("/direct/{otherUserId}")
    @Operation(summary = "Get paginated direct messages with another user (newest first)")
    public ResponseEntity<ApiResponse<Page<MessageResponse>>> getDirectMessages(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long otherUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        Page<MessageResponse> messages =
                messageService.getDirectMessages(me.getUserId(), otherUserId, page, size);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @GetMapping("/direct/{otherUserId}/before")
    @Operation(summary = "Load older direct messages before a sentAt cursor")
    public ResponseEntity<ApiResponse<Page<MessageResponse>>> getDirectMessagesBefore(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long otherUserId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        Page<MessageResponse> messages = messageService
                .getDirectMessagesBefore(me.getUserId(), otherUserId, before, page, size);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    // ────────────────────────────────────────────────────────
    // EDIT / DELETE
    // ────────────────────────────────────────────────────────

    @PutMapping("/{messageId}")
    @Operation(summary = "Edit a sent message — sender only, TEXT type only")
    public ResponseEntity<ApiResponse<MessageResponse>> editMessage(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long messageId,
            @Valid @RequestBody EditMessageRequest request) {

        MessageResponse message = messageService.editMessage(messageId, me.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Message edited", message));
    }

    @DeleteMapping("/{messageId}")
    @Operation(summary = "Soft-delete a sent message — sender only")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long messageId) {

        messageService.deleteMessage(messageId, me.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Message deleted", null));
    }

    @PostMapping("/{messageId}/reactions")
    @Operation(summary = "React to a message with an emoji")
    public ResponseEntity<ApiResponse<MessageResponse>> addReaction(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long messageId,
            @Valid @RequestBody ReactMessageRequest request) {

        MessageResponse message = messageService.addReaction(messageId, me.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Reaction added", message));
    }

    @DeleteMapping("/{messageId}/reactions")
    @Operation(summary = "Remove your emoji reaction from a message")
    public ResponseEntity<ApiResponse<MessageResponse>> removeReaction(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long messageId,
            @RequestParam String emoji) {

        MessageResponse message = messageService.removeReaction(messageId, me.getUserId(), emoji);
        return ResponseEntity.ok(ApiResponse.success("Reaction removed", message));
    }

    // ────────────────────────────────────────────────────────
    // DELIVERY STATUS
    // ────────────────────────────────────────────────────────

    /**
     * PUT /api/messages/{messageId}/status
     *
     * Called by websocket-handler to advance the delivery status:
     *   SENT → DELIVERED (when recipient WebSocket session is confirmed active)
     *   DELIVERED → READ  (when READ_RECEIPT STOMP event is received)
     */
    @PutMapping("/{messageId}/status")
    @Operation(summary = "Update delivery status — called by websocket-handler")
    public ResponseEntity<ApiResponse<Void>> updateDeliveryStatus(
            @PathVariable Long messageId,
            @Valid @RequestBody UpdateDeliveryStatusRequest request) {

        messageService.updateDeliveryStatus(messageId, request);
        return ResponseEntity.ok(ApiResponse.success("Delivery status updated", null));
    }

    // ────────────────────────────────────────────────────────
    // SEARCH
    // ────────────────────────────────────────────────────────

    @GetMapping("/room/{roomId}/search")
    @Operation(summary = "Full-text keyword search within a room's message history")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> searchMessages(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId,
            @RequestParam String keyword) {

        List<MessageResponse> results =
                messageService.searchMessages(roomId, keyword, me.getUserId());
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    // ────────────────────────────────────────────────────────
    // UNREAD COUNT
    // ────────────────────────────────────────────────────────

    /**
     * GET /api/messages/room/{roomId}/unread?after=2026-04-01T12:00:00
     *
     * Returns the count of non-deleted messages sent after the given timestamp.
     * room-service calls this with the user's lastReadAt to compute the unread badge.
     */
    @GetMapping("/room/{roomId}/unread")
    @Operation(summary = "Count unread messages after a given timestamp")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUnreadCount(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime after) {

        long count = messageService.getUnreadCount(roomId, after);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("roomId", roomId, "unreadCount", count)));
    }

    @GetMapping("/room/{roomId}/unread/list")
    @Operation(summary = "List unread messages after a given timestamp (for notification dispatch)")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getUnreadMessages(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime after) {

        List<MessageResponse> messages = messageService.getUnreadMessages(roomId, after);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    // ────────────────────────────────────────────────────────
    // STATS
    // ────────────────────────────────────────────────────────

    @GetMapping("/room/{roomId}/count")
    @Operation(summary = "Get total message count for a room")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMessageCount(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId) {

        long count = messageService.getMessageCount(roomId);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("roomId", roomId, "messageCount", count)));
    }

    // ────────────────────────────────────────────────────────
    // PIN / UNPIN  (Room Admin calls these via websocket-handler or web controller)
    // ────────────────────────────────────────────────────────

    @PutMapping("/{messageId}/pin")
    @Operation(summary = "Pin a message to the top of the room — Room Admin only")
    public ResponseEntity<ApiResponse<MessageResponse>> pinMessage(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long messageId) {

        MessageResponse message = messageService.pinMessage(messageId, me.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Message pinned", message));
    }

    @PutMapping("/{messageId}/unpin")
    @Operation(summary = "Unpin a message — Room Admin only")
    public ResponseEntity<ApiResponse<MessageResponse>> unpinMessage(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long messageId) {

        MessageResponse message = messageService.unpinMessage(messageId, me.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Message unpinned", message));
    }

    @GetMapping("/room/{roomId}/pinned")
    @Operation(summary = "Get all pinned messages in a room")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getPinnedMessages(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId) {

        List<MessageResponse> pinned = messageService.getPinnedMessages(roomId, me.getUserId());
        return ResponseEntity.ok(ApiResponse.success(pinned));
    }

    // ────────────────────────────────────────────────────────
    // PLATFORM ADMIN
    // ────────────────────────────────────────────────────────

    @DeleteMapping("/admin/{messageId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Admin — force soft-delete any message for policy violation")
    public ResponseEntity<ApiResponse<Void>> adminDeleteMessage(
            @PathVariable Long messageId) {

        messageService.adminDeleteMessage(messageId);
        return ResponseEntity.ok(ApiResponse.success("Message deleted by admin", null));
    }

    /**
     * DELETE /api/messages/admin/room/{roomId}/history
     *
     * Called by room-service (Room Admin) to wipe an entire room's message history.
     */
    @DeleteMapping("/admin/room/{roomId}/history")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('USER')")
    @Operation(summary = "Clear all messages in a room — Room Admin / Platform Admin")
    public ResponseEntity<ApiResponse<Map<String, Object>>> clearRoomHistory(
            @PathVariable Long roomId) {

        int count = messageService.clearRoomHistory(roomId);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("roomId", roomId, "messagesCleared", count)));
    }

    @GetMapping("/admin/count")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Admin — get total count of all messages")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getTotalMessageCount() {
        long count = messageService.getTotalMessageCount();
        return ResponseEntity.ok(ApiResponse.success(Map.of("totalMessages", count)));
    }
}
