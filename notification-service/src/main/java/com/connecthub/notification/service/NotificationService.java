package com.connecthub.notification.service;

import com.connecthub.notification.dto.request.*;
import com.connecthub.notification.dto.response.NotificationResponse;

import java.util.List;

public interface NotificationService {

    // ── In-app ────────────────────────────────────────────
    NotificationResponse send(SendNotificationRequest request);

    void sendBulk(BulkNotificationRequest request);

    // ── Read state ────────────────────────────────────────
    List<NotificationResponse> getByRecipient(Long recipientId);

    List<NotificationResponse> getUnreadByRecipient(Long recipientId);

    long getUnreadCount(Long recipientId);

    void markAsRead(Long notificationId, Long recipientId);

    void markAllRead(Long recipientId);

    void delete(Long notificationId, Long recipientId);

    // ── FCM Push ──────────────────────────────────────────
    void sendPushNotification(Long userId, String title, String body);

    void registerFcmToken(Long userId, RegisterFcmTokenRequest request);

    void removeFcmToken(Long userId, String token);

    // ── Email ─────────────────────────────────────────────
    void sendEmail(SendEmailRequest request);

    void sendMissedDmEmail(Long recipientId, String recipientEmail,
                           String senderName, String messagePreview);
}
