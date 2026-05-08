package com.connecthub.notification.controller;

import com.connecthub.notification.config.AuthUser;
import com.connecthub.notification.dto.request.BulkNotificationRequest;
import com.connecthub.notification.dto.request.RegisterFcmTokenRequest;
import com.connecthub.notification.dto.request.SendNotificationRequest;
import com.connecthub.notification.dto.response.NotificationResponse;
import com.connecthub.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = NotificationController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX, 
        pattern = "com\\.connecthub\\.notification\\.config\\..*"
    )
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("NotificationController Tests")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationService notificationService;

    private AuthUser authUser;
    private UsernamePasswordAuthenticationToken authToken;
    private NotificationResponse notificationResponse;

    @BeforeEach
    void setUp() {
        authUser = new AuthUser(100L, "user@test.com", "USER");
        authToken = new UsernamePasswordAuthenticationToken(authUser, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        
        notificationResponse = new NotificationResponse();
        notificationResponse.setId(1L);
        notificationResponse.setTitle("Test");
        
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    private void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMyNotifications_success() throws Exception {
        when(notificationService.getByRecipient(100L)).thenReturn(List.of(notificationResponse));

        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1));
        
        clearAuth();
    }

    @Test
    void getUnread_success() throws Exception {
        when(notificationService.getUnreadByRecipient(100L)).thenReturn(List.of(notificationResponse));

        mockMvc.perform(get("/notifications/unread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1));
        
        clearAuth();
    }

    @Test
    void getUnreadCount_success() throws Exception {
        when(notificationService.getUnreadCount(100L)).thenReturn(5L);

        mockMvc.perform(get("/notifications/unread/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unreadCount").value(5));
        
        clearAuth();
    }

    @Test
    void markRead_success() throws Exception {
        mockMvc.perform(put("/notifications/1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).markAsRead(1L, 100L);
        clearAuth();
    }

    @Test
    void markAllRead_success() throws Exception {
        mockMvc.perform(put("/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).markAllRead(100L);
        clearAuth();
    }

    @Test
    void delete_success() throws Exception {
        mockMvc.perform(delete("/notifications/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).delete(1L, 100L);
        clearAuth();
    }

    @Test
    void send_success() throws Exception {
        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId(100L);
        req.setType("NEW_MESSAGE");
        req.setTitle("Test Title");
        req.setMessage("Test Message");

        when(notificationService.send(any())).thenReturn(notificationResponse);

        mockMvc.perform(post("/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
        
        clearAuth();
    }

    @Test
    void sendBulk_success() throws Exception {
        BulkNotificationRequest req = new BulkNotificationRequest();
        req.setRecipientIds(List.of(100L, 101L));
        req.setType("NEW_MESSAGE");
        req.setTitle("Broadcast Title");
        req.setMessage("Broadcast Message");

        mockMvc.perform(post("/notifications/send-bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).sendBulk(any());
        clearAuth();
    }

    @Test
    void registerToken_success() throws Exception {
        RegisterFcmTokenRequest req = new RegisterFcmTokenRequest();
        req.setToken("fcm-token");
        req.setDeviceType("ANDROID");

        mockMvc.perform(post("/notifications/fcm/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).registerFcmToken(eq(100L), any());
        clearAuth();
    }

    @Test
    void removeToken_success() throws Exception {
        mockMvc.perform(delete("/notifications/fcm/remove")
                        .param("token", "fcm-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).removeFcmToken(100L, "fcm-token");
        clearAuth();
    }

    @Test
    void sendPush_success() throws Exception {
        mockMvc.perform(post("/notifications/push")
                        .param("userId", "100")
                        .param("title", "Title")
                        .param("body", "Body"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).sendPushNotification(100L, "Title", "Body");
        clearAuth();
    }

    @Test
    void broadcast_success() throws Exception {
        BulkNotificationRequest req = new BulkNotificationRequest();
        req.setRecipientIds(List.of(100L, 101L));
        req.setType("NEW_MESSAGE");
        req.setTitle("Broadcast Title");
        req.setMessage("Broadcast Message");

        mockMvc.perform(post("/notifications/admin/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).sendBulk(any());
        clearAuth();
    }
}