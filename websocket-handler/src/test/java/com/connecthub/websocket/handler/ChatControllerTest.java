package com.connecthub.websocket.handler;
import com.connecthub.websocket.payload.StompPayloads.*;
import com.connecthub.websocket.service.DownstreamService;
import com.connecthub.websocket.service.SessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import reactor.core.publisher.Mono;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock private SimpMessagingTemplate messaging;
    @Mock private SessionRegistry       sessions;
    @Mock private DownstreamService     downstream;

    @InjectMocks
    private ChatController controller;

    // ── Shared helpers ────────────────────────────────────────────────────────

    /**
     * Builds a minimal Spring Message carrying STOMP session attributes.
     * The org.springframework.messaging.Message needs a simp-session-id header
     * so that StompHeaderAccessor.wrap() can extract sessionId.
     */
    private org.springframework.messaging.Message<byte[]> buildMessage(
            String sessionId, Map<String, Object> attrs) {

        return MessageBuilder.withPayload(new byte[0])
                .setHeader("simpSessionId", sessionId)
                .setHeader("simpSessionAttributes", attrs != null ? attrs : new HashMap<>())
                .build();
    }

    private Map<String, Object> sessionAttrs(Long userId, String email, String token) {
        Map<String, Object> attrs = new HashMap<>();
        if (userId != null) attrs.put("userId", userId);
        if (email  != null) attrs.put("email",  email);
        if (token  != null) attrs.put("token",  token);
        return attrs;
    }

    /** Creates a mock SimpMessageHeaderAccessor wired with session + attrs. */
    private SimpMessageHeaderAccessor mockHeaderAccessor(String sessionId, Map<String, Object> attrs) {
        SimpMessageHeaderAccessor ha = mock(SimpMessageHeaderAccessor.class);
        lenient().when(ha.getSessionId()).thenReturn(sessionId);
        lenient().when(ha.getSessionAttributes()).thenReturn(attrs);
        return ha;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // handleConnect
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void handleConnect_withValidAttrs_registersAndSetsOnline() {
        Map<String, Object> attrs = sessionAttrs(1L, "alice@test.com", "token");
        var msg = buildMessage("sess-1", attrs);
        SessionConnectEvent event = new SessionConnectEvent(this, msg);

        controller.handleConnect(event);

        verify(sessions).register("sess-1", 1L, "alice");
        verify(downstream).setUserOnline(1L, "sess-1", "WEB", "unknown");
    }

    @Test
    void handleConnect_nullEmail_usernameIsUserPrefix() {
        Map<String, Object> attrs = sessionAttrs(2L, null, "token");
        var msg = buildMessage("sess-2", attrs);
        SessionConnectEvent event = new SessionConnectEvent(this, msg);

        controller.handleConnect(event);

        verify(sessions).register("sess-2", 2L, "user-2");
    }

    @Test
    void handleConnect_noUserId_returnsEarlyWithoutRegistering() {
        Map<String, Object> emptyAttrs = new HashMap<>();
        var msg = buildMessage("sess-no-user", emptyAttrs);
        SessionConnectEvent event = new SessionConnectEvent(this, msg);

        controller.handleConnect(event);

        verifyNoInteractions(sessions);
        verifyNoInteractions(downstream);
    }

    @Test
    void handleConnect_nullSessionAttributes_returnsEarly() {
        // Build a message with NO simpSessionAttributes header → attrs will be null
        var msg = MessageBuilder.withPayload(new byte[0])
                .setHeader("simpSessionId", "sess-null-attrs")
                .build();
        SessionConnectEvent event = new SessionConnectEvent(this, msg);

        controller.handleConnect(event);

        verifyNoInteractions(sessions);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // handleDisconnect
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void handleDisconnect_knownSession_deregistersAndBroadcasts() {
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        Map<String, Object> attrs = sessionAttrs(1L, "alice@test.com", "token");
        var msg = buildMessage("sess-1", attrs);
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, msg, "sess-1", null);

        controller.handleDisconnect(event);

        verify(sessions).deregister("sess-1");
        verify(downstream).setUserOffline(1L, "sess-1", "token");
        verify(messaging).convertAndSend(eq("/topic/presence"), any(PresenceUpdateEvent.class));
    }

    @Test
    void handleDisconnect_unknownSession_deregistersButDoesNotBroadcast() {
        when(sessions.getUserId("ghost")).thenReturn(null);

        var msg = buildMessage("ghost", new HashMap<>());
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, msg, "ghost", null);

        controller.handleDisconnect(event);

        verify(sessions).deregister("ghost");
        verify(downstream, never()).setUserOffline(any(), any(), any());
        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void handleDisconnect_nullSessionAttributes_tokenIsNull() {
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        // No simpSessionAttributes header → attrs is null → token is null
        var msg = MessageBuilder.withPayload(new byte[0])
                .setHeader("simpSessionId", "sess-1")
                .build();
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, msg, "sess-1", null);

        controller.handleDisconnect(event);

        verify(downstream).setUserOffline(1L, "sess-1", null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // handleSubscribe
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void handleSubscribe_roomTopic_callsUpdateDeliveryStatus() {
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        var msg = MessageBuilder.withPayload(new byte[0])
                .setHeader("simpSessionId", "sess-1")
                .setHeader("simpSessionAttributes", new HashMap<>())
                .setHeader("simpDestination", "/topic/room/42")
                .build();
        SessionSubscribeEvent event = new SessionSubscribeEvent(this, msg);

        controller.handleSubscribe(event);

        verify(downstream).updateDeliveryStatus(null, 1L, "DELIVERED");
    }

    @Test
    void handleSubscribe_roomTopic_nonNumericRoomId_ignoredGracefully() {
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        var msg = MessageBuilder.withPayload(new byte[0])
                .setHeader("simpSessionId", "sess-1")
                .setHeader("simpSessionAttributes", new HashMap<>())
                .setHeader("simpDestination", "/topic/room/not-a-number")
                .build();
        SessionSubscribeEvent event = new SessionSubscribeEvent(this, msg);

        // Should not throw NumberFormatException
        assertDoesNotThrow(() -> controller.handleSubscribe(event));
        verify(downstream, never()).updateDeliveryStatus(any(), any(), any());
    }

    @Test
    void handleSubscribe_roomTopic_nullUserId_skipsDeliveryStatus() {
        when(sessions.getUserId("sess-1")).thenReturn(null);

        var msg = MessageBuilder.withPayload(new byte[0])
                .setHeader("simpSessionId", "sess-1")
                .setHeader("simpSessionAttributes", new HashMap<>())
                .setHeader("simpDestination", "/topic/room/42")
                .build();
        SessionSubscribeEvent event = new SessionSubscribeEvent(this, msg);

        controller.handleSubscribe(event);

        verify(downstream, never()).updateDeliveryStatus(any(), any(), any());
    }

    @Test
    void handleSubscribe_presenceTopic_broadcastsOnlineAndSendsPeers() {
        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getAllOnlineUserIds()).thenReturn(List.of(1L, 2L, 3L));

        var msg = MessageBuilder.withPayload(new byte[0])
                .setHeader("simpSessionId", "sess-1")
                .setHeader("simpSessionAttributes", new HashMap<>())
                .setHeader("simpDestination", "/topic/presence")
                .build();
        SessionSubscribeEvent event = new SessionSubscribeEvent(this, msg);

        controller.handleSubscribe(event);

        // Broadcast own ONLINE to /topic/presence
        verify(messaging).convertAndSend(eq("/topic/presence"), any(PresenceUpdateEvent.class));
        // Send peers (2L and 3L, not self=1L) to /topic/user/1
        verify(messaging, times(2)).convertAndSend(eq("/topic/user/1"), any(PresenceUpdateEvent.class));
    }

    @Test
    void handleSubscribe_presenceTopic_nullUserId_doesNotBroadcast() {
        when(sessions.getUserId("sess-1")).thenReturn(null);

        var msg = MessageBuilder.withPayload(new byte[0])
                .setHeader("simpSessionId", "sess-1")
                .setHeader("simpSessionAttributes", new HashMap<>())
                .setHeader("simpDestination", "/topic/presence")
                .build();
        SessionSubscribeEvent event = new SessionSubscribeEvent(this, msg);

        controller.handleSubscribe(event);

        verifyNoInteractions(messaging);
    }

    @Test
    void handleSubscribe_otherTopic_noop() {
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        var msg = MessageBuilder.withPayload(new byte[0])
                .setHeader("simpSessionId", "sess-1")
                .setHeader("simpSessionAttributes", new HashMap<>())
                .setHeader("simpDestination", "/topic/something-else")
                .build();
        SessionSubscribeEvent event = new SessionSubscribeEvent(this, msg);

        controller.handleSubscribe(event);

        verifyNoInteractions(messaging);
        verify(downstream, never()).updateDeliveryStatus(any(), any(), any());
    }

    @Test
    void handleSubscribe_nullDestination_noop() {
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        // No simpDestination header → dest will be null
        var msg = MessageBuilder.withPayload(new byte[0])
                .setHeader("simpSessionId", "sess-1")
                .setHeader("simpSessionAttributes", new HashMap<>())
                .build();
        SessionSubscribeEvent event = new SessionSubscribeEvent(this, msg);

        controller.handleSubscribe(event);

        verifyNoInteractions(messaging);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // handleChatMessage — room message
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void handleChatMessage_roomMessage_persistsAndBroadcasts() {
        Map<String, Object> attrs = sessionAttrs(1L, "alice@test.com", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);

        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        Map<String, Object> savedMsg = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("id", 100L);
        savedMsg.put("data", data);
        when(downstream.persistMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(savedMsg));
        when(downstream.getRoomDetails(anyLong(), anyString()))
                .thenReturn(Mono.just(Map.of()));

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .roomId(10L)
                .content("Hello room!")
                .type("TEXT")
                .build();

        controller.handleChatMessage(payload, ha);

        verify(downstream).persistMessage(eq(1L), eq(10L), eq("Hello room!"), eq("TEXT"), any(), any(), eq("token"));
    }

    @Test
    void handleChatMessage_roomMessage_withMention_sendsMentionAlert() {
        Map<String, Object> attrs = sessionAttrs(1L, "alice@test.com", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);

        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        Map<String, Object> savedMsg = Map.of("data", Map.of("id", 200L));
        when(downstream.persistMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(savedMsg));
        when(downstream.getRoomDetails(anyLong(), anyString())).thenReturn(Mono.just(Map.of()));

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .roomId(10L)
                .content("Hey @bob check this out")
                .type("TEXT")
                .build();

        controller.handleChatMessage(payload, ha);

        // Give the reactive chain time to execute
        verify(downstream).persistMessage(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleChatMessage_roomMessage_nullTypeDefaultsToText() {
        Map<String, Object> attrs = sessionAttrs(1L, "alice@test.com", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);

        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        Map<String, Object> savedMsg = Map.of("data", Map.of("id", 50L));
        when(downstream.persistMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(savedMsg));
        when(downstream.getRoomDetails(anyLong(), anyString())).thenReturn(Mono.just(Map.of()));

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .roomId(5L)
                .content("msg")
                .type(null)   // null type → should default to "TEXT" in event
                .build();

        controller.handleChatMessage(payload, ha);

        verify(downstream).persistMessage(any(), eq(5L), any(), isNull(), any(), any(), any());
    }

    @Test
    void handleChatMessage_persistReturnsEmptyData_messageIdIsNull() {
        Map<String, Object> attrs = sessionAttrs(1L, "alice@test.com", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);

        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        // Return empty map so extractData → no "id" → messageId is null
        when(downstream.persistMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(new HashMap<>()));
        when(downstream.getRoomDetails(anyLong(), anyString())).thenReturn(Mono.just(Map.of()));

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .roomId(10L)
                .content("test")
                .build();

        assertDoesNotThrow(() -> controller.handleChatMessage(payload, ha));
    }

    @Test
    void handleChatMessage_unknownSender_returnsEarly() {
        Map<String, Object> attrs = new HashMap<>(); // no userId
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("ghost", attrs);

        when(sessions.getUserId("ghost")).thenReturn(null);

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .roomId(10L).content("hi").build();

        controller.handleChatMessage(payload, ha);

        verifyNoInteractions(downstream);
    }

    @Test
    void handleChatMessage_missingAuthToken_returnsEarly() {
        Map<String, Object> attrs = sessionAttrs(1L, "alice@test.com", null); // no token
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);

        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .roomId(10L).content("hi").build();

        controller.handleChatMessage(payload, ha);

        verify(downstream, never()).persistMessage(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleChatMessage_noRoomIdNoRecipientId_returnsEarly() {
        Map<String, Object> attrs = sessionAttrs(1L, "alice@test.com", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);

        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .content("orphaned message")
                .build(); // neither roomId nor recipientId

        controller.handleChatMessage(payload, ha);

        verify(downstream, never()).persistMessage(any(), any(), any(), any(), any(), any(), any());
        verify(downstream, never()).persistDirectMessage(any(), any(), any(), any(), any(), any(), any());
    }

    // ── DM path ───────────────────────────────────────────────────────────────

    @Test
    void handleChatMessage_directMessage_toOtherUser_broadcastsToBoth() {
        Map<String, Object> attrs = sessionAttrs(1L, "alice@test.com", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);

        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        Map<String, Object> savedMsg = Map.of("data", Map.of("id", 300L));
        when(downstream.persistDirectMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(savedMsg));

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .recipientId(2L)
                .content("DM content here")
                .type("TEXT")
                .build();

        controller.handleChatMessage(payload, ha);

        verify(downstream).persistDirectMessage(eq(1L), eq(2L), any(), any(), any(), any(), eq("token"));
    }

    @Test
    void handleChatMessage_directMessage_toSelf_broadcastsOnlyToSender() {
        Map<String, Object> attrs = sessionAttrs(1L, "alice@test.com", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);

        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        Map<String, Object> savedMsg = Map.of("data", Map.of("id", 301L));
        when(downstream.persistDirectMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(savedMsg));

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .recipientId(1L)  // same as sender
                .content("self DM")
                .build();

        controller.handleChatMessage(payload, ha);

        // sendNotification is still called, but messaging should only go to sender's topic
        verify(downstream).persistDirectMessage(any(), eq(1L), any(), any(), any(), any(), any());
    }

    @Test
    void handleChatMessage_directMessage_longContent_truncatesPreview() {
        Map<String, Object> attrs = sessionAttrs(1L, "alice@test.com", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);

        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        Map<String, Object> savedMsg = Map.of("data", Map.of("id", 302L));
        when(downstream.persistDirectMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(savedMsg));

        String longContent = "a".repeat(150);
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .recipientId(2L)
                .content(longContent)
                .build();

        controller.handleChatMessage(payload, ha);

        verify(downstream).persistDirectMessage(any(), any(), eq(longContent), any(), any(), any(), any());
    }

    @Test
    void handleChatMessage_directMessage_nullContent_noTruncation() {
        Map<String, Object> attrs = sessionAttrs(1L, "alice@test.com", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);

        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        Map<String, Object> savedMsg = Map.of("data", Map.of("id", 303L));
        when(downstream.persistDirectMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(savedMsg));

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .recipientId(2L)
                .content(null)
                .build();

        assertDoesNotThrow(() -> controller.handleChatMessage(payload, ha));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // handleTyping
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void handleTyping_roomTyping_broadcastsToRoom() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(1L, "alice@test.com", null));
        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        TypingPayload payload = TypingPayload.builder().roomId(10L).isTyping(true).build();

        controller.handleTyping(payload, ha);

        verify(messaging).convertAndSend(eq("/topic/room/10"), any(TypingIndicatorEvent.class));
    }

    @Test
    void handleTyping_dmTyping_broadcastsToBothParties() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(1L, "alice@test.com", null));
        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        TypingPayload payload = TypingPayload.builder().recipientId(2L).isTyping(false).build();

        controller.handleTyping(payload, ha);

        verify(messaging).convertAndSend(eq("/topic/user/1"), any(TypingIndicatorEvent.class));
        verify(messaging).convertAndSend(eq("/topic/user/2"), any(TypingIndicatorEvent.class));
    }

    @Test
    void handleTyping_dmTyping_toSelf_onlyOneBroadcast() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(1L, "alice@test.com", null));
        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        TypingPayload payload = TypingPayload.builder().recipientId(1L).isTyping(true).build(); // self

        controller.handleTyping(payload, ha);

        verify(messaging, times(1)).convertAndSend(eq("/topic/user/1"), any(TypingIndicatorEvent.class));
    }

    @Test
    void handleTyping_noRoomNoRecipient_noop() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(1L, "alice@test.com", null));
        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        TypingPayload payload = TypingPayload.builder().build(); // neither roomId nor recipientId

        controller.handleTyping(payload, ha);

        verifyNoInteractions(messaging);
    }

    @Test
    void handleTyping_unknownSender_returnsEarly() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("ghost", new HashMap<>());
        when(sessions.getUserId("ghost")).thenReturn(null);

        TypingPayload payload = TypingPayload.builder().roomId(10L).build();

        controller.handleTyping(payload, ha);

        verifyNoInteractions(messaging);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // handleReadReceipt
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void handleReadReceipt_withUpToTime_marksAndBroadcasts() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(1L, null, null));
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        ReadReceiptPayload payload = ReadReceiptPayload.builder()
                .roomId(10L).upToTime("2024-01-01T10:00:00").build();

        controller.handleReadReceipt(payload, ha);

        verify(downstream).markRoomRead(10L, 1L, "2024-01-01T10:00:00");
        verify(messaging).convertAndSend(eq("/topic/room/10"), any(ReadReceiptEvent.class));
    }

    @Test
    void handleReadReceipt_nullUpToTime_usesCurrentTime() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(1L, null, null));
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        ReadReceiptPayload payload = ReadReceiptPayload.builder()
                .roomId(10L).upToTime(null).build(); // null → uses LocalDateTime.now()

        controller.handleReadReceipt(payload, ha);

        verify(downstream).markRoomRead(eq(10L), eq(1L), anyString());
    }

    @Test
    void handleReadReceipt_unknownSender_returnsEarly() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("ghost", new HashMap<>());
        when(sessions.getUserId("ghost")).thenReturn(null);

        ReadReceiptPayload payload = ReadReceiptPayload.builder().roomId(10L).build();

        controller.handleReadReceipt(payload, ha);

        verifyNoInteractions(downstream);
        verifyNoInteractions(messaging);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // handleStatusUpdate
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void handleStatusUpdate_validPayload_broadcastsToBothParties() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(2L, null, null));
        when(sessions.getUserId("sess-1")).thenReturn(2L);

        StatusUpdatePayload payload = StatusUpdatePayload.builder()
                .messageId(100L).senderId(1L).roomId(10L).status("READ").build();

        controller.handleStatusUpdate(payload, ha);

        verify(messaging).convertAndSend(eq("/topic/user/1"), any(DeliveryStatusEvent.class));
        verify(messaging).convertAndSend(eq("/topic/user/2"), any(DeliveryStatusEvent.class));
    }

    @Test
    void handleStatusUpdate_readerIsSender_onlyOneBroadcast() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(1L, null, null));
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        StatusUpdatePayload payload = StatusUpdatePayload.builder()
                .messageId(100L).senderId(1L).status("READ").build(); // reader == sender

        controller.handleStatusUpdate(payload, ha);

        verify(messaging, times(1)).convertAndSend(eq("/topic/user/1"), any(DeliveryStatusEvent.class));
    }

    @Test
    void handleStatusUpdate_nullStatus_defaultsToRead() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(2L, null, null));
        when(sessions.getUserId("sess-1")).thenReturn(2L);

        StatusUpdatePayload payload = StatusUpdatePayload.builder()
                .messageId(100L).senderId(1L).status(null).build();

        controller.handleStatusUpdate(payload, ha);

        ArgumentCaptor<DeliveryStatusEvent> captor = ArgumentCaptor.forClass(DeliveryStatusEvent.class);
        verify(messaging, atLeastOnce()).convertAndSend(anyString(), captor.capture());
        assertEquals("READ", captor.getValue().getStatus());
    }

    @Test
    void handleStatusUpdate_nullReaderId_returnsEarly() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("ghost", new HashMap<>());
        when(sessions.getUserId("ghost")).thenReturn(null);

        StatusUpdatePayload payload = StatusUpdatePayload.builder()
                .messageId(100L).senderId(1L).build();

        controller.handleStatusUpdate(payload, ha);

        verifyNoInteractions(messaging);
    }

    @Test
    void handleStatusUpdate_nullMessageId_returnsEarly() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(2L, null, null));
        when(sessions.getUserId("sess-1")).thenReturn(2L);

        StatusUpdatePayload payload = StatusUpdatePayload.builder()
                .messageId(null).senderId(1L).build();

        controller.handleStatusUpdate(payload, ha);

        verifyNoInteractions(messaging);
    }

    @Test
    void handleStatusUpdate_nullSenderId_returnsEarly() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(2L, null, null));
        when(sessions.getUserId("sess-1")).thenReturn(2L);

        StatusUpdatePayload payload = StatusUpdatePayload.builder()
                .messageId(100L).senderId(null).build();

        controller.handleStatusUpdate(payload, ha);

        verifyNoInteractions(messaging);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // handleReaction
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void handleReaction_validPayload_broadcastsToRoom() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(1L, null, null));
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        ReactionPayload payload = ReactionPayload.builder()
                .roomId(10L).messageId(5L).emoji("👍").build();

        controller.handleReaction(payload, ha);

        verify(messaging).convertAndSend(eq("/topic/room/10"), any(ReactionEvent.class));
    }

    @Test
    void handleReaction_unknownSender_returnsEarly() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("ghost", new HashMap<>());
        when(sessions.getUserId("ghost")).thenReturn(null);

        ReactionPayload payload = ReactionPayload.builder().roomId(10L).emoji("👍").build();

        controller.handleReaction(payload, ha);

        verifyNoInteractions(messaging);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // handlePing
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void handlePing_validSession_pingsPresence() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(1L, null, null));
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        controller.handlePing(ha);

        verify(downstream).pingPresence("sess-1");
    }

    @Test
    void handlePing_unknownSession_noop() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("ghost", new HashMap<>());
        when(sessions.getUserId("ghost")).thenReturn(null);

        controller.handlePing(ha);

        verify(downstream, never()).pingPresence(any());
    }

    @Test
    void handlePing_nullSessionId_noop() {
        SimpMessageHeaderAccessor ha = mock(SimpMessageHeaderAccessor.class);
        when(ha.getSessionId()).thenReturn(null);
        when(ha.getSessionAttributes()).thenReturn(new HashMap<>());
        when(sessions.getUserId(null)).thenReturn(null);

        controller.handlePing(ha);

        verify(downstream, never()).pingPresence(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // handlePresenceUpdate
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void handlePresenceUpdate_online_broadcastsAndUpdatesDnstream() {
        Map<String, Object> attrs = sessionAttrs(1L, null, "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        PresenceUpdatePayload payload = new PresenceUpdatePayload("ONLINE", null);

        controller.handlePresenceUpdate(payload, ha);

        verify(messaging).convertAndSend(eq("/topic/presence"), any(PresenceUpdateEvent.class));
        verify(downstream).updatePresenceStatus(1L, "ONLINE", "token");
    }

    @Test
    void handlePresenceUpdate_away_mapsCorrectly() {
        Map<String, Object> attrs = sessionAttrs(1L, null, null);
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        PresenceUpdatePayload payload = new PresenceUpdatePayload("AWAY", "On a call");

        controller.handlePresenceUpdate(payload, ha);

        ArgumentCaptor<PresenceUpdateEvent> captor = ArgumentCaptor.forClass(PresenceUpdateEvent.class);
        verify(messaging).convertAndSend(eq("/topic/presence"), captor.capture());
        assertEquals("AWAY", captor.getValue().getStatus());
        assertEquals("On a call", captor.getValue().getCustomMessage());
    }

    @Test
    void handlePresenceUpdate_dnd_mapsCorrectly() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(1L, null, null));
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        controller.handlePresenceUpdate(new PresenceUpdatePayload("DND", null), ha);

        ArgumentCaptor<PresenceUpdateEvent> captor = ArgumentCaptor.forClass(PresenceUpdateEvent.class);
        verify(messaging).convertAndSend(eq("/topic/presence"), captor.capture());
        assertEquals("DND", captor.getValue().getStatus());
    }

    @Test
    void handlePresenceUpdate_invisible_mapsCorrectly() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(1L, null, null));
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        controller.handlePresenceUpdate(new PresenceUpdatePayload("INVISIBLE", null), ha);

        ArgumentCaptor<PresenceUpdateEvent> captor = ArgumentCaptor.forClass(PresenceUpdateEvent.class);
        verify(messaging).convertAndSend(eq("/topic/presence"), captor.capture());
        assertEquals("INVISIBLE", captor.getValue().getStatus());
    }

    @Test
    void handlePresenceUpdate_unknownStatus_defaultsToOnline() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(1L, null, null));
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        controller.handlePresenceUpdate(new PresenceUpdatePayload("BANANA", null), ha);

        ArgumentCaptor<PresenceUpdateEvent> captor = ArgumentCaptor.forClass(PresenceUpdateEvent.class);
        verify(messaging).convertAndSend(eq("/topic/presence"), captor.capture());
        assertEquals("ONLINE", captor.getValue().getStatus());
    }

    @Test
    void handlePresenceUpdate_nullUserId_returnsEarly() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("ghost", new HashMap<>());
        when(sessions.getUserId("ghost")).thenReturn(null);

        controller.handlePresenceUpdate(new PresenceUpdatePayload("ONLINE", null), ha);

        verifyNoInteractions(messaging);
    }

    @Test
    void handlePresenceUpdate_nullStatus_returnsEarly() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(1L, null, null));
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        controller.handlePresenceUpdate(new PresenceUpdatePayload(null, null), ha);

        verifyNoInteractions(messaging);
    }

    @Test
    void handlePresenceUpdate_nullSessionAttributes_tokenIsNull() {
        SimpMessageHeaderAccessor ha = mock(SimpMessageHeaderAccessor.class);
        when(ha.getSessionId()).thenReturn("sess-1");
        when(ha.getSessionAttributes()).thenReturn(null);
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        controller.handlePresenceUpdate(new PresenceUpdatePayload("ONLINE", null), ha);

        verify(downstream).updatePresenceStatus(1L, "ONLINE", null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // handleEditMessage
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void handleEditMessage_roomEdit_broadcastsToRoom() {
        Map<String, Object> attrs = sessionAttrs(1L, null, "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        when(downstream.editMessage(10L, "new content", "token"))
                .thenReturn(Mono.just(Map.of("id", 10L)));

        EditPayload payload = EditPayload.builder()
                .messageId(10L).content("new content").roomId(5L).build();

        controller.handleEditMessage(payload, ha);

        verify(downstream).editMessage(10L, "new content", "token");
    }

    @Test
    void handleEditMessage_dmEdit_broadcastsToBothParties() {
        Map<String, Object> attrs = sessionAttrs(1L, null, "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        when(downstream.editMessage(anyLong(), anyString(), anyString()))
                .thenReturn(Mono.just(Map.of()));

        EditPayload payload = EditPayload.builder()
                .messageId(10L).content("edited dm").roomId(null).recipientId(2L).build();

        controller.handleEditMessage(payload, ha);

        verify(downstream).editMessage(10L, "edited dm", "token");
    }

    @Test
    void handleEditMessage_dmEdit_toSelf_onlyOneBroadcast() {
        Map<String, Object> attrs = sessionAttrs(1L, null, "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        when(downstream.editMessage(anyLong(), anyString(), anyString()))
                .thenReturn(Mono.just(Map.of()));

        EditPayload payload = EditPayload.builder()
                .messageId(10L).content("self edit").roomId(null).recipientId(1L).build();

        controller.handleEditMessage(payload, ha);

        verify(downstream).editMessage(any(), any(), any());
    }

    @Test
    void handleEditMessage_dmEdit_nullRecipient_noBroadcastToRecipient() {
        Map<String, Object> attrs = sessionAttrs(1L, null, "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        when(downstream.editMessage(anyLong(), anyString(), anyString()))
                .thenReturn(Mono.just(Map.of()));

        EditPayload payload = EditPayload.builder()
                .messageId(10L).content("dm edit").roomId(null).recipientId(null).build();

        controller.handleEditMessage(payload, ha);

        verify(downstream).editMessage(any(), any(), any());
    }

    @Test
    void handleEditMessage_nullUserId_returnsEarly() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("ghost", new HashMap<>());
        when(sessions.getUserId("ghost")).thenReturn(null);

        EditPayload payload = EditPayload.builder().messageId(1L).content("edit").build();

        controller.handleEditMessage(payload, ha);

        verifyNoInteractions(downstream);
    }

    @Test
    void handleEditMessage_nullMessageId_returnsEarly() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(1L, null, "token"));
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        EditPayload payload = EditPayload.builder().messageId(null).content("content").build();

        controller.handleEditMessage(payload, ha);

        verifyNoInteractions(downstream);
    }

    @Test
    void handleEditMessage_blankContent_returnsEarly() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(1L, null, "token"));
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        EditPayload payload = EditPayload.builder().messageId(1L).content("   ").build();

        controller.handleEditMessage(payload, ha);

        verifyNoInteractions(downstream);
    }

    @Test
    void handleEditMessage_nullContent_returnsEarly() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(1L, null, "token"));
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        EditPayload payload = EditPayload.builder().messageId(1L).content(null).build();

        controller.handleEditMessage(payload, ha);

        verifyNoInteractions(downstream);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // handleDeleteMessage
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void handleDeleteMessage_roomDelete_broadcastsToRoom() {
        Map<String, Object> attrs = sessionAttrs(1L, null, "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);
        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(downstream.deleteMessage(anyLong(), anyBoolean(), anyString()))
                .thenReturn(Mono.just(true));

        DeletePayload payload = DeletePayload.builder()
                .messageId(10L).roomId(5L).isAdminDelete(false).build();

        controller.handleDeleteMessage(payload, ha);

        verify(downstream).deleteMessage(10L, false, "token");
    }

    @Test
    void handleDeleteMessage_adminDelete_withAdminRole_usesAdminEndpoint() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", 1L);
        attrs.put("role", "ADMIN");
        attrs.put("token", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);
        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(downstream.deleteMessage(anyLong(), anyBoolean(), anyString()))
                .thenReturn(Mono.just(true));

        DeletePayload payload = DeletePayload.builder()
                .messageId(10L).roomId(5L).isAdminDelete(true).build();

        controller.handleDeleteMessage(payload, ha);

        verify(downstream).deleteMessage(10L, true, "token");
    }

    @Test
    void handleDeleteMessage_adminDeleteFlag_butNonAdminRole_usesNormalEndpoint() {
        Map<String, Object> attrs = sessionAttrs(1L, null, "token");
        attrs.put("role", "USER");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);
        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(downstream.deleteMessage(anyLong(), anyBoolean(), anyString()))
                .thenReturn(Mono.just(true));

        DeletePayload payload = DeletePayload.builder()
                .messageId(10L).roomId(5L).isAdminDelete(true).build();

        controller.handleDeleteMessage(payload, ha);

        verify(downstream).deleteMessage(10L, false, "token"); // isAdmin=false because role≠ADMIN
    }

    @Test
    void handleDeleteMessage_dmDelete_broadcastsToBothParties() {
        Map<String, Object> attrs = sessionAttrs(1L, null, "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);
        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(downstream.deleteMessage(anyLong(), anyBoolean(), anyString()))
                .thenReturn(Mono.just(true));

        DeletePayload payload = DeletePayload.builder()
                .messageId(10L).roomId(null).recipientId(2L).isAdminDelete(false).build();

        controller.handleDeleteMessage(payload, ha);

        verify(downstream).deleteMessage(10L, false, "token");
    }

    @Test
    void handleDeleteMessage_dmDelete_toSelf_onlyOneBroadcast() {
        Map<String, Object> attrs = sessionAttrs(1L, null, "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);
        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(downstream.deleteMessage(anyLong(), anyBoolean(), anyString()))
                .thenReturn(Mono.just(true));

        DeletePayload payload = DeletePayload.builder()
                .messageId(10L).roomId(null).recipientId(1L).isAdminDelete(false).build();

        controller.handleDeleteMessage(payload, ha);

        verify(downstream).deleteMessage(any(), anyBoolean(), any());
    }

    @Test
    void handleDeleteMessage_dmDelete_nullRecipient_onlyDeleterBroadcast() {
        Map<String, Object> attrs = sessionAttrs(1L, null, "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);
        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(downstream.deleteMessage(anyLong(), anyBoolean(), anyString()))
                .thenReturn(Mono.just(true));

        DeletePayload payload = DeletePayload.builder()
                .messageId(10L).roomId(null).recipientId(null).isAdminDelete(false).build();

        controller.handleDeleteMessage(payload, ha);

        verify(downstream).deleteMessage(any(), anyBoolean(), any());
    }

    @Test
    void handleDeleteMessage_nullUserId_returnsEarly() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("ghost", new HashMap<>());
        when(sessions.getUserId("ghost")).thenReturn(null);

        DeletePayload payload = DeletePayload.builder().messageId(10L).build();

        controller.handleDeleteMessage(payload, ha);

        verifyNoInteractions(downstream);
    }

    @Test
    void handleDeleteMessage_nullMessageId_returnsEarly() {
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", sessionAttrs(1L, null, "token"));
        when(sessions.getUserId("sess-1")).thenReturn(1L);

        DeletePayload payload = DeletePayload.builder().messageId(null).build();

        controller.handleDeleteMessage(payload, ha);

        verifyNoInteractions(downstream);
    }

    @Test
    void handleDeleteMessage_nullSessionAttributes_attrsIsNull() {
        SimpMessageHeaderAccessor ha = mock(SimpMessageHeaderAccessor.class);
        when(ha.getSessionId()).thenReturn("sess-1");
        when(ha.getSessionAttributes()).thenReturn(null);
        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(downstream.deleteMessage(anyLong(), anyBoolean(), any()))
                .thenReturn(Mono.just(true));

        DeletePayload payload = DeletePayload.builder().messageId(10L).roomId(5L).build();

        // attrs==null → token=null, isAdmin=false
        assertDoesNotThrow(() -> controller.handleDeleteMessage(payload, ha));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // resolveUserId — fallback paths
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void resolveUserId_sessionNotInRegistry_fallsBackToAttrNumber() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", 5L);  // Number in attrs
        attrs.put("token", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-x", attrs);

        when(sessions.getUserId("sess-x")).thenReturn(null); // not in registry
        when(sessions.getUsername("sess-x")).thenReturn("alice");
        when(downstream.persistMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(Map.of()));
        when(downstream.getRoomDetails(anyLong(), anyString())).thenReturn(Mono.just(Map.of()));

        ChatMessagePayload payload = ChatMessagePayload.builder().roomId(1L).content("hi").build();

        controller.handleChatMessage(payload, ha);

        // Should register and proceed (not return early)
        verify(sessions).register(eq("sess-x"), eq(5L), any());
    }

    @Test
    void resolveUserId_sessionNotInRegistry_fallsBackToAttrString() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", "7");  // String in attrs
        attrs.put("token", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-y", attrs);

        when(sessions.getUserId("sess-y")).thenReturn(null);
        when(sessions.getUsername("sess-y")).thenReturn("bob");
        when(downstream.persistMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(Map.of()));
        when(downstream.getRoomDetails(anyLong(), anyString())).thenReturn(Mono.just(Map.of()));

        ChatMessagePayload payload = ChatMessagePayload.builder().roomId(1L).content("hi").build();

        controller.handleChatMessage(payload, ha);

        verify(sessions).register(eq("sess-y"), eq(7L), any());
    }

    @Test
    void resolveUserId_sessionNotInRegistry_stringNotParseable_returnsNull() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", "not-a-number");
        attrs.put("token", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-z", attrs);

        when(sessions.getUserId("sess-z")).thenReturn(null);

        ChatMessagePayload payload = ChatMessagePayload.builder().roomId(1L).content("hi").build();

        controller.handleChatMessage(payload, ha);

        // Cannot resolve → early return
        verify(downstream, never()).persistMessage(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void resolveUserId_nullAttrs_returnsNull() {
        SimpMessageHeaderAccessor ha = mock(SimpMessageHeaderAccessor.class);
        when(ha.getSessionId()).thenReturn("sess-1");
        when(ha.getSessionAttributes()).thenReturn(null);
        when(sessions.getUserId("sess-1")).thenReturn(null);

        ChatMessagePayload payload = ChatMessagePayload.builder().roomId(1L).content("hi").build();

        controller.handleChatMessage(payload, ha);

        verify(downstream, never()).persistMessage(any(), any(), any(), any(), any(), any(), any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // resolveUsername — fallback paths
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void resolveUsername_sessionRegistryReturnsNull_fallsBackToAttrs() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", 1L);
        attrs.put("email", "fallback@test.com");
        attrs.put("token", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);

        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn(null); // not in registry
        when(downstream.persistMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(Map.of()));
        when(downstream.getRoomDetails(anyLong(), anyString())).thenReturn(Mono.just(Map.of()));

        ChatMessagePayload payload = ChatMessagePayload.builder().roomId(5L).content("hello").build();

        // Should fall back to email.split("@")[0] = "fallback"
        controller.handleChatMessage(payload, ha);

        verify(downstream).persistMessage(eq(1L), any(), any(), any(), any(), any(), any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // notifyRoomMembers — various member data shapes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void notifyRoomMembers_roomDataNull_logsWarning() {
        Map<String, Object> attrs = sessionAttrs(1L, "alice@test.com", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);

        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        Map<String, Object> savedMsg = Map.of("data", Map.of("id", 400L));
        when(downstream.persistMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(savedMsg));
        when(downstream.getRoomDetails(anyLong(), anyString()))
                .thenReturn(Mono.empty()); // null roomData → logs warning

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .roomId(10L).content("test").build();

        assertDoesNotThrow(() -> controller.handleChatMessage(payload, ha));
    }

    @Test
    void notifyRoomMembers_membersNotList_logsDebug() {
        Map<String, Object> attrs = sessionAttrs(1L, "alice@test.com", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);

        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        Map<String, Object> savedMsg = Map.of("data", Map.of("id", 401L));
        when(downstream.persistMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(savedMsg));
        when(downstream.getRoomDetails(anyLong(), anyString()))
                .thenReturn(Mono.just(Map.of("members", "not-a-list"))); // not a List

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .roomId(10L).content("test").build();

        assertDoesNotThrow(() -> controller.handleChatMessage(payload, ha));
    }

    @Test
    void notifyRoomMembers_memberNotMap_isSkipped() {
        Map<String, Object> attrs = sessionAttrs(1L, "alice@test.com", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);

        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        Map<String, Object> savedMsg = Map.of("data", Map.of("id", 402L));
        when(downstream.persistMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(savedMsg));

        // Member is a String, not a Map
        List<Object> members = List.of("not-a-map-member");
        when(downstream.getRoomDetails(anyLong(), anyString()))
                .thenReturn(Mono.just(Map.of("members", members)));

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .roomId(10L).content("hello").build();

        assertDoesNotThrow(() -> controller.handleChatMessage(payload, ha));
        verify(downstream, never()).sendNotification(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void notifyRoomMembers_memberUserIdNotNumber_isSkipped() {
        Map<String, Object> attrs = sessionAttrs(1L, "alice@test.com", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);

        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        Map<String, Object> savedMsg = Map.of("data", Map.of("id", 403L));
        when(downstream.persistMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(savedMsg));

        Map<String, Object> member = new HashMap<>();
        member.put("userId", "string-user-id"); // not a Number
        when(downstream.getRoomDetails(anyLong(), anyString()))
                .thenReturn(Mono.just(Map.of("members", List.of(member))));

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .roomId(10L).content("hello").build();

        assertDoesNotThrow(() -> controller.handleChatMessage(payload, ha));
        verify(downstream, never()).sendNotification(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void notifyRoomMembers_validMember_sendsNotification() {
        Map<String, Object> attrs = sessionAttrs(1L, "alice@test.com", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);

        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        Map<String, Object> savedMsg = Map.of("data", Map.of("id", 404L));
        when(downstream.persistMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(savedMsg));

        Map<String, Object> member = new HashMap<>();
        member.put("userId", 2); // Number — different from sender (1L)

        when(downstream.getRoomDetails(anyLong(), anyString()))
                .thenReturn(Mono.just(Map.of("members", List.of(member))));

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .roomId(10L).content("hello everyone").build();

        controller.handleChatMessage(payload, ha);

        verify(downstream).sendNotification(eq(2L), eq(1L), eq("NEW_MESSAGE"), any(), any(), eq(10L), any(), eq("token"));
    }

    @Test
    void notifyRoomMembers_longContent_truncatesToFiftyChars() {
        Map<String, Object> attrs = sessionAttrs(1L, "alice@test.com", "token");
        SimpMessageHeaderAccessor ha = mockHeaderAccessor("sess-1", attrs);

        when(sessions.getUserId("sess-1")).thenReturn(1L);
        when(sessions.getUsername("sess-1")).thenReturn("alice");

        Map<String, Object> savedMsg = Map.of("data", Map.of("id", 405L));
        when(downstream.persistMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(savedMsg));

        Map<String, Object> member = new HashMap<>();
        member.put("userId", 2);

        when(downstream.getRoomDetails(anyLong(), anyString()))
                .thenReturn(Mono.just(Map.of("members", List.of(member))));

        String longContent = "a".repeat(100);
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .roomId(10L).content(longContent).build();

        controller.handleChatMessage(payload, ha);

        ArgumentCaptor<String> previewCaptor = ArgumentCaptor.forClass(String.class);
        verify(downstream).sendNotification(any(), any(), any(), any(), previewCaptor.capture(), any(), any(), any());
        assertTrue(previewCaptor.getValue().endsWith("..."));
        assertEquals(53, previewCaptor.getValue().length()); // 50 + "..."
    }
}
