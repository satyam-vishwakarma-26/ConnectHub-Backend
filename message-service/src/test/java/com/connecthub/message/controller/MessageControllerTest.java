package com.connecthub.message.controller;

import com.connecthub.message.dto.request.EditMessageRequest;
import com.connecthub.message.dto.request.ReactMessageRequest;
import com.connecthub.message.dto.request.SendDirectMessageRequest;
import com.connecthub.message.dto.request.SendMessageRequest;
import com.connecthub.message.dto.request.UpdateDeliveryStatusRequest;
import com.connecthub.message.dto.response.MessageResponse;
import com.connecthub.message.security.JwtAuthenticationFilter;
import com.connecthub.message.security.JwtService;
import com.connecthub.message.service.MediaStorageService;
import com.connecthub.message.service.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MessageController.class)
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:3000")
@org.springframework.context.annotation.Import(com.connecthub.message.config.SecurityConfig.class)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MessageService messageService;

    @MockBean
    private MediaStorageService mediaStorageService;

    @MockBean
    private JwtService jwtService;
    
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MessageResponse sampleMessageResponse;
    private UsernamePasswordAuthenticationToken auth;
    private UsernamePasswordAuthenticationToken adminAuth;

    @BeforeEach
    void setUp() throws Exception {
        Mockito.doAnswer(invocation -> {
            jakarta.servlet.ServletRequest request = invocation.getArgument(0);
            jakarta.servlet.ServletResponse response = invocation.getArgument(1);
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        auth = new UsernamePasswordAuthenticationToken(
                new com.connecthub.message.security.AuthenticatedUser(100L, "test@test.com", "USER"),
                null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        adminAuth = new UsernamePasswordAuthenticationToken(
                new com.connecthub.message.security.AuthenticatedUser(100L, "admin@test.com", "PLATFORM_ADMIN"),
                null, List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
                
        sampleMessageResponse = new MessageResponse();
        sampleMessageResponse.setId(1L);
        sampleMessageResponse.setRoomId(10L);
        sampleMessageResponse.setSenderId(100L);
        sampleMessageResponse.setContent("Hello World");
        sampleMessageResponse.setType("TEXT");
        sampleMessageResponse.setDeliveryStatus("SENT");
    }

    @Test
    @DisplayName("POST /messages - Success")
    void sendMessage_success() throws Exception {
        SendMessageRequest request = new SendMessageRequest();
        request.setRoomId(10L);
        request.setContent("Hello");
        request.setType("TEXT");

        when(messageService.sendMessage(any(), any(SendMessageRequest.class))).thenReturn(sampleMessageResponse);

        mockMvc.perform(post("/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(authentication(auth)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Message sent"))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("POST /messages/room/{roomId}/image - Success")
    void sendImageMessage_success() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "test.jpg", MediaType.IMAGE_JPEG_VALUE, "image content".getBytes());
        
        when(messageService.sendImageMessage(any(), eq(10L), any(), eq("caption"), eq(1L)))
                .thenReturn(sampleMessageResponse);

        mockMvc.perform(multipart("/messages/room/{roomId}/image", 10L)
                .file(image)
                .param("content", "caption")
                .param("replyToMessageId", "1")
                .with(authentication(auth)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Image message sent"));
    }

    @Test
    @DisplayName("POST /messages/room/{roomId}/file - Success")
    void sendFileMessage_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", MediaType.TEXT_PLAIN_VALUE, "file content".getBytes());

        when(messageService.sendFileMessage(any(), eq(10L), any(), eq("caption"), eq(1L)))
                .thenReturn(sampleMessageResponse);

        mockMvc.perform(multipart("/messages/room/{roomId}/file", 10L)
                .file(file)
                .param("content", "caption")
                .param("replyToMessageId", "1")
                .with(authentication(auth)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("File message sent"));
    }

    @Test
    @DisplayName("POST /messages/direct/{recipientId}/image - Success")
    void sendDirectImageMessage_success() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "test.jpg", MediaType.IMAGE_JPEG_VALUE, "image content".getBytes());

        when(messageService.sendDirectImageMessage(any(), eq(200L), any(), eq("caption"), eq(1L)))
                .thenReturn(sampleMessageResponse);

        mockMvc.perform(multipart("/messages/direct/{recipientId}/image", 200L)
                .file(image)
                .param("content", "caption")
                .param("replyToMessageId", "1")
                .with(authentication(auth)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Direct image message sent"));
    }

    @Test
    @DisplayName("POST /messages/direct/{recipientId}/file - Success")
    void sendDirectFileMessage_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", MediaType.TEXT_PLAIN_VALUE, "file content".getBytes());

        when(messageService.sendDirectFileMessage(any(), eq(200L), any(), eq("caption"), eq(1L)))
                .thenReturn(sampleMessageResponse);

        mockMvc.perform(multipart("/messages/direct/{recipientId}/file", 200L)
                .file(file)
                .param("content", "caption")
                .param("replyToMessageId", "1")
                .with(authentication(auth)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Direct file message sent"));
    }

    @Test
    @DisplayName("GET /messages/media/{fileName} - Success")
    void getImage_success() throws Exception {
        org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource("test".getBytes()) {
            @Override
            public String getFilename() {
                return "test.jpg";
            }
        };

        when(mediaStorageService.loadAsResource("test.jpg")).thenReturn(resource);

        mockMvc.perform(get("/messages/media/test.jpg")
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(header().exists("Content-Type"));
    }

    @Test
    @DisplayName("POST /messages/media/upload - Success")
    void uploadMedia_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", MediaType.IMAGE_JPEG_VALUE, "image content".getBytes());

        MediaStorageService.StoredMedia storedMedia = new MediaStorageService.StoredMedia("test.jpg", "http://media.com/test.jpg", 100L);
        when(mediaStorageService.storeImage(any(), eq(0L))).thenReturn(storedMedia);

        mockMvc.perform(multipart("/messages/media/upload")
                .file(file)
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("File uploaded"))
                .andExpect(jsonPath("$.data.mediaUrl").value("http://media.com/test.jpg"));
    }

    @Test
    @DisplayName("POST /messages/direct - Success")
    void sendDirectMessage_success() throws Exception {
        SendDirectMessageRequest request = new SendDirectMessageRequest();
        request.setRecipientId(200L);
        request.setContent("Hello Direct");
        request.setType("TEXT");

        when(messageService.sendDirectMessage(any(), any(SendDirectMessageRequest.class))).thenReturn(sampleMessageResponse);

        mockMvc.perform(post("/messages/direct")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(authentication(auth)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Direct message sent"));
    }

    @Test
    @DisplayName("GET /messages/{messageId} - Success")
    void getMessageById_success() throws Exception {
        when(messageService.getMessageById(eq(1L), any())).thenReturn(sampleMessageResponse);

        mockMvc.perform(get("/messages/{messageId}", 1L)
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1L));
    }

    @Test
    @DisplayName("GET /messages/room/{roomId} - Success")
    void getMessagesByRoom_success() throws Exception {
        Page<MessageResponse> page = new PageImpl<>(List.of(sampleMessageResponse));
        when(messageService.getMessagesByRoom(eq(10L), any(), eq(0), eq(30))).thenReturn(page);

        mockMvc.perform(get("/messages/room/{roomId}", 10L)
                .param("page", "0")
                .param("size", "30")
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1L));
    }

    @Test
    @DisplayName("GET /messages/room/{roomId}/before - Success")
    void getMessagesBefore_success() throws Exception {
        Page<MessageResponse> page = new PageImpl<>(List.of(sampleMessageResponse));
        LocalDateTime time = LocalDateTime.of(2026, 4, 1, 12, 0);
        when(messageService.getMessagesBefore(eq(10L), any(), any(), eq(0), eq(30))).thenReturn(page);

        mockMvc.perform(get("/messages/room/{roomId}/before", 10L)
                .param("before", time.toString())
                .param("page", "0")
                .param("size", "30")
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1L));
    }

    @Test
    @DisplayName("GET /messages/direct/{otherUserId} - Success")
    void getDirectMessages_success() throws Exception {
        Page<MessageResponse> page = new PageImpl<>(List.of(sampleMessageResponse));
        when(messageService.getDirectMessages(any(), eq(200L), eq(0), eq(30))).thenReturn(page);

        mockMvc.perform(get("/messages/direct/{otherUserId}", 200L)
                .param("page", "0")
                .param("size", "30")
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1L));
    }

    @Test
    @DisplayName("GET /messages/direct/{otherUserId}/before - Success")
    void getDirectMessagesBefore_success() throws Exception {
        Page<MessageResponse> page = new PageImpl<>(List.of(sampleMessageResponse));
        LocalDateTime time = LocalDateTime.of(2026, 4, 1, 12, 0);
        when(messageService.getDirectMessagesBefore(any(), eq(200L), any(), eq(0), eq(30))).thenReturn(page);

        mockMvc.perform(get("/messages/direct/{otherUserId}/before", 200L)
                .param("before", time.toString())
                .param("page", "0")
                .param("size", "30")
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1L));
    }

    @Test
    @DisplayName("PUT /messages/{messageId} - Success")
    void editMessage_success() throws Exception {
        EditMessageRequest request = new EditMessageRequest();
        request.setContent("New content");

        when(messageService.editMessage(eq(1L), any(), any(EditMessageRequest.class))).thenReturn(sampleMessageResponse);

        mockMvc.perform(put("/messages/{messageId}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Message edited"));
    }

    @Test
    @DisplayName("DELETE /messages/{messageId} - Success")
    void deleteMessage_success() throws Exception {
        doNothing().when(messageService).deleteMessage(eq(1L), any());

        mockMvc.perform(delete("/messages/{messageId}", 1L)
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Message deleted"));
    }

    @Test
    @DisplayName("POST /messages/{messageId}/reactions - Success")
    void addReaction_success() throws Exception {
        ReactMessageRequest request = new ReactMessageRequest();
        request.setEmoji("👍");

        when(messageService.addReaction(eq(1L), any(), any(ReactMessageRequest.class))).thenReturn(sampleMessageResponse);

        mockMvc.perform(post("/messages/{messageId}/reactions", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Reaction added"));
    }

    @Test
    @DisplayName("DELETE /messages/{messageId}/reactions - Success")
    void removeReaction_success() throws Exception {
        when(messageService.removeReaction(eq(1L), any(), eq("👍"))).thenReturn(sampleMessageResponse);

        mockMvc.perform(delete("/messages/{messageId}/reactions", 1L)
                .param("emoji", "👍")
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Reaction removed"));
    }

    @Test
    @DisplayName("PUT /messages/{messageId}/status - Success")
    void updateDeliveryStatus_success() throws Exception {
        UpdateDeliveryStatusRequest request = new UpdateDeliveryStatusRequest();
        request.setStatus("READ");

        doNothing().when(messageService).updateDeliveryStatus(eq(1L), any(UpdateDeliveryStatusRequest.class));

        mockMvc.perform(put("/messages/{messageId}/status", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Delivery status updated"));
    }

    @Test
    @DisplayName("GET /messages/room/{roomId}/search - Success")
    void searchMessages_success() throws Exception {
        when(messageService.searchMessages(eq(10L), eq("test"), any())).thenReturn(List.of(sampleMessageResponse));

        mockMvc.perform(get("/messages/room/{roomId}/search", 10L)
                .param("keyword", "test")
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1L));
    }

    @Test
    @DisplayName("GET /messages/room/{roomId}/unread - Success")
    void getUnreadCount_success() throws Exception {
        LocalDateTime time = LocalDateTime.of(2026, 4, 1, 12, 0);
        when(messageService.getUnreadCount(eq(10L), any())).thenReturn(5L);

        mockMvc.perform(get("/messages/room/{roomId}/unread", 10L)
                .param("after", time.toString())
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(5L));
    }

    @Test
    @DisplayName("GET /messages/room/{roomId}/unread/list - Success")
    void getUnreadMessages_success() throws Exception {
        LocalDateTime time = LocalDateTime.of(2026, 4, 1, 12, 0);
        when(messageService.getUnreadMessages(eq(10L), any())).thenReturn(List.of(sampleMessageResponse));

        mockMvc.perform(get("/messages/room/{roomId}/unread/list", 10L)
                .param("after", time.toString())
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1L));
    }

    @Test
    @DisplayName("GET /messages/room/{roomId}/count - Success")
    void getMessageCount_success() throws Exception {
        when(messageService.getMessageCount(eq(10L))).thenReturn(50L);

        mockMvc.perform(get("/messages/room/{roomId}/count", 10L)
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageCount").value(50L));
    }

    @Test
    @DisplayName("PUT /messages/{messageId}/pin - Success")
    void pinMessage_success() throws Exception {
        when(messageService.pinMessage(eq(1L), any())).thenReturn(sampleMessageResponse);

        mockMvc.perform(put("/messages/{messageId}/pin", 1L)
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Message pinned"));
    }

    @Test
    @DisplayName("PUT /messages/{messageId}/unpin - Success")
    void unpinMessage_success() throws Exception {
        when(messageService.unpinMessage(eq(1L), any())).thenReturn(sampleMessageResponse);

        mockMvc.perform(put("/messages/{messageId}/unpin", 1L)
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Message unpinned"));
    }

    @Test
    @DisplayName("GET /messages/room/{roomId}/pinned - Success")
    void getPinnedMessages_success() throws Exception {
        when(messageService.getPinnedMessages(eq(10L), any())).thenReturn(List.of(sampleMessageResponse));

        mockMvc.perform(get("/messages/room/{roomId}/pinned", 10L)
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1L));
    }

    @Test
    @DisplayName("DELETE /messages/admin/{messageId} - Success")
    void adminDeleteMessage_success() throws Exception {
        doNothing().when(messageService).adminDeleteMessage(1L);

        mockMvc.perform(delete("/messages/admin/{messageId}", 1L)
                .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Message deleted by admin"));
    }

    @Test
    @DisplayName("DELETE /messages/admin/room/{roomId}/history - Success")
    void clearRoomHistory_success() throws Exception {
        when(messageService.clearRoomHistory(10L)).thenReturn(100);

        mockMvc.perform(delete("/messages/admin/room/{roomId}/history", 10L)
                .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messagesCleared").value(100));
    }

    @Test
    @DisplayName("GET /messages/admin/count - Success")
    void getTotalMessageCount_success() throws Exception {
        when(messageService.getTotalMessageCount()).thenReturn(5000L);

        mockMvc.perform(get("/messages/admin/count")
                .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMessages").value(5000L));
    }
}
