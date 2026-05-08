package com.connecthub.notification.controller;

import com.connecthub.notification.config.AuthUser;
import com.connecthub.notification.dto.request.*;
import com.connecthub.notification.dto.response.ApiResponse;
import com.connecthub.notification.dto.response.NotificationResponse;
import com.connecthub.notification.service.NotificationService;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app alerts, FCM tokens, email, bulk send")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    // ────────────────────────────────────────────────────────
    // IN-APP — read & manage
    // ────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Get all notifications for the current user")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMyNotifications(
            @AuthenticationPrincipal AuthUser me) {

        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getByRecipient(me.getUserId())));
    }

    @GetMapping("/unread")
    @Operation(summary = "Get unread notifications for current user")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getUnread(
            @AuthenticationPrincipal AuthUser me) {

        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getUnreadByRecipient(me.getUserId())));
    }

    @GetMapping("/unread/count")
    @Operation(summary = "Get unread notification count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            @AuthenticationPrincipal AuthUser me) {

        return ResponseEntity.ok(ApiResponse.success(
                Map.of("unreadCount", notificationService.getUnreadCount(me.getUserId()))));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark a single notification as read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long id) {

        notificationService.markAsRead(id, me.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", null));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @AuthenticationPrincipal AuthUser me) {

        notificationService.markAllRead(me.getUserId());
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", null));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a notification")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long id) {

        notificationService.delete(id, me.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Notification deleted", null));
    }

    // ────────────────────────────────────────────────────────
    // INTERNAL — called by websocket-handler / other services
    // ────────────────────────────────────────────────────────

    @PostMapping("/send")
    @Operation(summary = "Internal — send a single in-app notification")
    public ResponseEntity<ApiResponse<NotificationResponse>> send(
            @Valid @RequestBody SendNotificationRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Notification sent",
                        notificationService.send(request)));
    }

    @PostMapping("/send-bulk")
    @Operation(summary = "Internal — send in-app notification to multiple users (platform broadcast)")
    public ResponseEntity<ApiResponse<Void>> sendBulk(
            @Valid @RequestBody BulkNotificationRequest request) {

        notificationService.sendBulk(request);
        return ResponseEntity.ok(ApiResponse.success("Bulk notification sent", null));
    }

    // ────────────────────────────────────────────────────────
    // FCM TOKEN MANAGEMENT
    // ────────────────────────────────────────────────────────

    @PostMapping("/fcm/register")
    @Operation(summary = "Register a device FCM token for push notifications")
    public ResponseEntity<ApiResponse<Void>> registerToken(
            @AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody RegisterFcmTokenRequest request) {

        notificationService.registerFcmToken(me.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("FCM token registered", null));
    }

    @DeleteMapping("/fcm/remove")
    @Operation(summary = "Remove a device FCM token on logout")
    public ResponseEntity<ApiResponse<Void>> removeToken(
            @AuthenticationPrincipal AuthUser me,
            @RequestParam String token) {

        notificationService.removeFcmToken(me.getUserId(), token);
        return ResponseEntity.ok(ApiResponse.success("FCM token removed", null));
    }

    @PostMapping("/push")
    @Operation(summary = "Internal — send FCM push notification to a user")
    public ResponseEntity<ApiResponse<Void>> sendPush(
            @RequestParam Long userId,
            @RequestParam String title,
            @RequestParam String body) {

        notificationService.sendPushNotification(userId, title, body);
        return ResponseEntity.ok(ApiResponse.success("Push notification queued", null));
    }

    // ────────────────────────────────────────────────────────
    // ADMIN
    // ────────────────────────────────────────────────────────

    @PostMapping("/admin/broadcast")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Admin — broadcast platform-wide notification to all users")
    public ResponseEntity<ApiResponse<Void>> broadcast(
            @Valid @RequestBody BulkNotificationRequest request) {

        notificationService.sendBulk(request);
        return ResponseEntity.ok(ApiResponse.success("Platform broadcast sent", null));
    }
}
