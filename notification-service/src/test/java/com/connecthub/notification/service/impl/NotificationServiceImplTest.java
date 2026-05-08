package com.connecthub.notification.service.impl;

import com.connecthub.notification.config.FirebaseConfig;
import com.connecthub.notification.dto.request.BulkNotificationRequest;
import com.connecthub.notification.dto.request.RegisterFcmTokenRequest;
import com.connecthub.notification.dto.request.SendEmailRequest;
import com.connecthub.notification.dto.request.SendNotificationRequest;
import com.connecthub.notification.dto.response.NotificationResponse;
import com.connecthub.notification.entity.FcmToken;
import com.connecthub.notification.entity.Notification;
import com.connecthub.notification.repository.FcmTokenRepository;
import com.connecthub.notification.repository.NotificationRepository;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationServiceImpl Tests")
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private FcmTokenRepository fcmTokenRepository;
    @Mock private JavaMailSender mailSender;
    @Mock private FirebaseConfig firebaseConfig;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Notification testNotification;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "emailFrom", "noreply@connecthub.com");
        ReflectionTestUtils.setField(notificationService, "emailName", "ConnectHub");

        testNotification = Notification.builder()
                .id(1L)
                .recipientId(100L)
                .actorId(200L)
                .type(Notification.NotificationType.NEW_MESSAGE)
                .title("Test Title")
                .message("Test Message")
                .isRead(false)
                .build();
    }

    @Test
    void send_success() {
        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId(100L);
        req.setActorId(200L);
        req.setType("NEW_MESSAGE");
        req.setTitle("Test Title");
        req.setMessage("Test Message");

        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(2L);
            return n;
        });

        NotificationResponse res = notificationService.send(req);

        assertThat(res.getId()).isEqualTo(2L);
        assertThat(res.getTitle()).isEqualTo("Test Title");
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void sendBulk_success() {
        BulkNotificationRequest req = new BulkNotificationRequest();
        req.setRecipientIds(List.of(100L, 101L));
        req.setActorId(200L);
        req.setType("SYSTEM");
        req.setTitle("System Update");
        req.setMessage("Update message");

        notificationService.sendBulk(req);

        verify(notificationRepository).saveAll(anyIterable());
    }

    @Test
    void getByRecipient_success() {
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(100L))
                .thenReturn(List.of(testNotification));
        List<NotificationResponse> responses = notificationService.getByRecipient(100L);
        assertThat(responses).hasSize(1);
    }

    @Test
    void getUnreadByRecipient_success() {
        when(notificationRepository.findByRecipientIdAndIsReadOrderByCreatedAtDesc(100L, false))
                .thenReturn(List.of(testNotification));
        List<NotificationResponse> responses = notificationService.getUnreadByRecipient(100L);
        assertThat(responses).hasSize(1);
    }

    @Test
    void getUnreadCount_success() {
        when(notificationRepository.countByRecipientIdAndIsRead(100L, false)).thenReturn(5L);
        long count = notificationService.getUnreadCount(100L);
        assertThat(count).isEqualTo(5L);
    }

    @Test
    void markAsRead_success() {
        notificationService.markAsRead(1L, 100L);
        verify(notificationRepository).markOneRead(1L, 100L);
    }

    @Test
    void markAllRead_success() {
        notificationService.markAllRead(100L);
        verify(notificationRepository).markAllRead(100L);
    }

    @Test
    void delete_success() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(testNotification));
        notificationService.delete(1L, 100L);
        verify(notificationRepository).delete(testNotification);
    }

    @Test
    void registerFcmToken_success() {
        RegisterFcmTokenRequest req = new RegisterFcmTokenRequest();
        req.setToken("fcm-token-123");
        req.setDeviceType("ANDROID");

        when(fcmTokenRepository.existsByUserIdAndToken(100L, "fcm-token-123")).thenReturn(false);

        notificationService.registerFcmToken(100L, req);

        verify(fcmTokenRepository).save(any(FcmToken.class));
    }

    @Test
    void registerFcmToken_alreadyExists() {
        RegisterFcmTokenRequest req = new RegisterFcmTokenRequest();
        req.setToken("fcm-token-123");

        when(fcmTokenRepository.existsByUserIdAndToken(100L, "fcm-token-123")).thenReturn(true);

        notificationService.registerFcmToken(100L, req);

        verify(fcmTokenRepository, never()).save(any(FcmToken.class));
    }

    @Test
    void removeFcmToken_success() {
        notificationService.removeFcmToken(100L, "fcm-token-123");
        verify(fcmTokenRepository).deleteByUserIdAndToken(100L, "fcm-token-123");
    }

    @Test
    void sendEmail_success() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        SendEmailRequest req = new SendEmailRequest();
        req.setTo("test@test.com");
        req.setSubject("Subject");
        req.setBody("Body");
        req.setHtml(false);

        notificationService.sendEmail(req);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendMissedDmEmail_success() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        notificationService.sendMissedDmEmail(100L, "test@test.com", "John Doe", "Hello there");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendPushNotification_firebaseNotInitialised() {
        when(firebaseConfig.isInitialised()).thenReturn(false);
        notificationService.sendPushNotification(100L, "Title", "Body");
        verifyNoInteractions(fcmTokenRepository);
    }

    @Test
    void sendPushNotification_noTokens() {
        when(firebaseConfig.isInitialised()).thenReturn(true);
        when(fcmTokenRepository.findByUserId(100L)).thenReturn(List.of());
        notificationService.sendPushNotification(100L, "Title", "Body");
    }

    @Test
    void sendPushNotification_success() throws Exception {
        when(firebaseConfig.isInitialised()).thenReturn(true);
        FcmToken token = FcmToken.builder().userId(100L).token("token123").build();
        when(fcmTokenRepository.findByUserId(100L)).thenReturn(List.of(token));

        FirebaseMessaging mockFirebaseMessaging = mock(FirebaseMessaging.class);
        BatchResponse mockBatchResponse = mock(BatchResponse.class);
        SendResponse mockSendResponse = mock(SendResponse.class);

        when(mockBatchResponse.getSuccessCount()).thenReturn(1);
        when(mockBatchResponse.getResponses()).thenReturn(List.of(mockSendResponse));
        when(mockSendResponse.isSuccessful()).thenReturn(true);
        when(mockFirebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(mockBatchResponse);

        try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
            mockedStatic.when(FirebaseMessaging::getInstance).thenReturn(mockFirebaseMessaging);
            
            notificationService.sendPushNotification(100L, "Title", "Body");
            
            verify(mockFirebaseMessaging).sendEachForMulticast(any(MulticastMessage.class));
            verify(fcmTokenRepository, never()).deleteByUserIdAndToken(anyLong(), anyString());
        }
    }
}
