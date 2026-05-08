package com.connecthub.notification.service.impl;

import com.connecthub.notification.config.FirebaseConfig;
import com.connecthub.notification.dto.request.*;
import com.connecthub.notification.dto.response.NotificationResponse;
import com.connecthub.notification.entity.FcmToken;
import com.connecthub.notification.entity.Notification;
import com.connecthub.notification.repository.FcmTokenRepository;
import com.connecthub.notification.repository.NotificationRepository;
import com.connecthub.notification.service.NotificationService;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final FcmTokenRepository     fcmTokenRepository;
    private final JavaMailSender         mailSender;
    private final FirebaseConfig         firebaseConfig;

    @Value("${app.notification.email-from}")
    private String emailFrom;

    @Value("${app.notification.email-name:ConnectHub}")
    private String emailName;

    // ── Send In-App Notification ──────────────────────────

    @Override
    public NotificationResponse send(SendNotificationRequest req) {
        Notification n = Notification.builder()
                .recipientId(req.getRecipientId())
                .actorId(req.getActorId())
                .type(parseType(req.getType()))
                .title(req.getTitle())
                .message(req.getMessage())
                .roomId(req.getRoomId())
                .messageId(req.getMessageId())
                .isRead(false)
                .build();

        n = notificationRepository.save(n);
        log.info("[Notif] Saved in-app → recipientId={} type={}", req.getRecipientId(), req.getType());
        return NotificationResponse.from(n);
    }

    // ── Bulk Send ─────────────────────────────────────────

    @Override
    public void sendBulk(BulkNotificationRequest req) {
        List<Notification> notifications = req.getRecipientIds().stream()
                .map(recipientId -> Notification.builder()
                        .recipientId(recipientId)
                        .actorId(req.getActorId())
                        .type(parseType(req.getType()))
                        .title(req.getTitle())
                        .message(req.getMessage())
                        .roomId(req.getRoomId())
                        .messageId(req.getMessageId())
                        .isRead(false)
                        .build())
                .collect(Collectors.toList());

        notificationRepository.saveAll(notifications);
        log.info("[Notif] Bulk saved {} in-app notifications", notifications.size());
    }

    // ── Read / Unread ─────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getByRecipient(Long recipientId) {
        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(recipientId)
                .stream().map(NotificationResponse::from).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadByRecipient(Long recipientId) {
        return notificationRepository
                .findByRecipientIdAndIsReadOrderByCreatedAtDesc(recipientId, false)
                .stream().map(NotificationResponse::from).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(Long recipientId) {
        return notificationRepository.countByRecipientIdAndIsRead(recipientId, false);
    }

    @Override
    public void markAsRead(Long notificationId, Long recipientId) {
        int updated = notificationRepository.markOneRead(notificationId, recipientId);
        if (updated == 0) log.warn("[Notif] markAsRead failed: id={} recipient={}", notificationId, recipientId);
    }

    @Override
    public void markAllRead(Long recipientId) {
        int updated = notificationRepository.markAllRead(recipientId);
        log.info("[Notif] Marked {} notifications read for recipientId={}", updated, recipientId);
    }

    @Override
    public void delete(Long notificationId, Long recipientId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getRecipientId().equals(recipientId)) {
                notificationRepository.delete(n);
            }
        });
    }

    // ── FCM Push Notification ─────────────────────────────

    @Override
    @Async
    public void sendPushNotification(Long userId, String title, String body) {
        if (!firebaseConfig.isInitialised()) {
            log.warn("[FCM] Firebase not initialised — skipping push for userId={}", userId);
            return;
        }

        List<FcmToken> tokens = fcmTokenRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            log.debug("[FCM] No tokens registered for userId={}", userId);
            return;
        }

        List<String> tokenStrings = tokens.stream()
                .map(FcmToken::getToken)
                .collect(Collectors.toList());

        MulticastMessage message = MulticastMessage.builder()
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .addAllTokens(tokenStrings)
                .putData("title", title)
                .putData("body", body)
                .build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            log.info("[FCM] Sent to userId={}: {}/{} success",
                      userId, response.getSuccessCount(), tokens.size());

            // Remove invalid tokens
            List<SendResponse> responses = response.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                if (!responses.get(i).isSuccessful()) {
                    String invalidToken = tokenStrings.get(i);
                    log.warn("[FCM] Invalid token removed for userId={}", userId);
                    fcmTokenRepository.deleteByUserIdAndToken(userId, invalidToken);
                }
            }
        } catch (FirebaseMessagingException e) {
            log.error("[FCM] Failed to send to userId={}: {}", userId, e.getMessage());
        }
    }

    // ── FCM Token Management ──────────────────────────────

    @Override
    public void registerFcmToken(Long userId, RegisterFcmTokenRequest request) {
        if (fcmTokenRepository.existsByUserIdAndToken(userId, request.getToken())) {
            log.debug("[FCM] Token already registered for userId={}", userId);
            return;
        }
        FcmToken token = FcmToken.builder()
                .userId(userId)
                .token(request.getToken())
                .deviceType(request.getDeviceType())
                .build();
        fcmTokenRepository.save(token);
        log.info("[FCM] Token registered for userId={} device={}", userId, request.getDeviceType());
    }

    @Override
    public void removeFcmToken(Long userId, String token) {
        fcmTokenRepository.deleteByUserIdAndToken(userId, token);
        log.info("[FCM] Token removed for userId={}", userId);
    }

    // ── Email ─────────────────────────────────────────────

    @Override
    @Async
    public void sendEmail(SendEmailRequest request) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(emailFrom, emailName);
            helper.setTo(request.getTo());
            helper.setSubject(request.getSubject());
            helper.setText(request.getBody(), request.isHtml());
            mailSender.send(mime);
            log.info("[Email] Sent to={} subject={}", request.getTo(), request.getSubject());
        } catch (Exception e) {
            log.error("[Email] Failed to send to={}: {}", request.getTo(), e.getMessage());
        }
    }

    @Override
    @Async
    public void sendMissedDmEmail(Long recipientId, String recipientEmail,
                                   String senderName, String messagePreview) {
        String subject = "You have a missed message from " + senderName + " on ConnectHub";
        String body = """
                <html><body style="font-family:DM Sans,sans-serif;color:#0f1020;padding:32px">
                  <h2 style="color:#2f75ff">You have a new message</h2>
                  <p><strong>%s</strong> sent you a message on ConnectHub:</p>
                  <blockquote style="border-left:3px solid #2f75ff;padding-left:16px;color:#4b5068">
                    %s
                  </blockquote>
                  <a href="http://localhost:3000/chat"
                     style="display:inline-block;margin-top:16px;padding:10px 20px;
                            background:#2f75ff;color:#fff;border-radius:8px;text-decoration:none">
                    Open ConnectHub
                  </a>
                  <p style="margin-top:24px;font-size:12px;color:#8b91aa">
                    You received this email because you've been offline for over 30 minutes.
                  </p>
                </body></html>
                """.formatted(senderName, messagePreview);

        SendEmailRequest req = new SendEmailRequest();
        req.setTo(recipientEmail);
        req.setSubject(subject);
        req.setBody(body);
        req.setHtml(true);
        sendEmail(req);
        log.info("[Email] Missed DM email sent to recipientId={}", recipientId);
    }

    // ── Helper ────────────────────────────────────────────

    private Notification.NotificationType parseType(String type) {
        try {
            return Notification.NotificationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Notification.NotificationType.SYSTEM;
        }
    }
}
