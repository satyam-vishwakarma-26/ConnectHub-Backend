package com.connecthub.admin.service;

import com.connecthub.admin.entity.AuditLog;
import com.connecthub.admin.repository.AuditLogRepository;
import com.connecthub.admin.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AdminServiceImpl adminService;

    // ── shared mocks ──────────────────────────────────────────────────────────
    private Authentication authentication;
    private SecurityContext securityContext;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adminService, "authServiceUrl",         "http://auth-service");
        ReflectionTestUtils.setField(adminService, "roomServiceUrl",         "http://room-service");
        ReflectionTestUtils.setField(adminService, "messageServiceUrl",      "http://message-service");
        ReflectionTestUtils.setField(adminService, "presenceServiceUrl",     "http://presence-service");
        ReflectionTestUtils.setField(adminService, "notificationServiceUrl", "http://notification-service");

        // Request context with Authorization header
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token123");
        request.setRemoteAddr("192.168.1.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // Security context — actorId = 1L
        AuthenticatedUser user = new AuthenticatedUser(1L, "admin@example.com", "PLATFORM_ADMIN");
        authentication  = mock(Authentication.class);
        securityContext = mock(SecurityContext.class);
        lenient().when(authentication.getPrincipal()).thenReturn(user);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    /** Stubs the REST call that getAllUsers() makes. */
    private void stubGetUsers(List<Object> dataList) {
        Map<String, Object> body = new HashMap<>();
        body.put("data", dataList);
        when(restTemplate.exchange(
                eq("http://auth-service/auth/admin/users"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(body));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getAnalytics
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getAnalytics_allServicesSucceed_returnsPopulatedMap() {
        // users
        Map<String, Object> authBody = new HashMap<>();
        authBody.put("data", Arrays.asList(new Object(), new Object()));
        when(restTemplate.exchange(
                eq("http://auth-service/auth/admin/users"), eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(authBody));

        // rooms
        Map<String, Object> roomBody = new HashMap<>();
        roomBody.put("data", Arrays.asList(new Object(), new Object(), new Object()));
        when(restTemplate.exchange(
                eq("http://room-service/rooms/admin/all"), eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(roomBody));

        // presence
        Map<String, Object> presenceData = new HashMap<>();
        presenceData.put("onlineCount", 5);
        Map<String, Object> presenceBody = new HashMap<>();
        presenceBody.put("data", presenceData);
        when(restTemplate.exchange(
                eq("http://presence-service/presence/online/count"), eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(presenceBody));

        // messages
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("totalMessages", 100);
        Map<String, Object> messageBody = new HashMap<>();
        messageBody.put("data", messageData);
        when(restTemplate.exchange(
                eq("http://message-service/messages/admin/count"), eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(messageBody));

        Map<String, Object> analytics = adminService.getAnalytics();

        assertEquals(2,   analytics.get("totalUsers"));
        assertEquals(3,   analytics.get("totalRooms"));
        assertEquals(5,   analytics.get("activeConnections"));
        assertEquals(100, analytics.get("totalMessages"));
    }

    @Test
    void getAnalytics_allServicesFail_returnsZeroes() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenThrow(new RuntimeException("service down"));

        Map<String, Object> analytics = adminService.getAnalytics();

        assertEquals(0, analytics.get("totalUsers"));
        assertEquals(0, analytics.get("totalRooms"));
        assertEquals(0, analytics.get("activeConnections"));
        assertEquals(0, analytics.get("totalMessages"));
    }

    @Test
    void getAnalytics_nullBodyForUsers_defaultsToZero() {
        // Users → null body
        when(restTemplate.exchange(
                eq("http://auth-service/auth/admin/users"), eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(null));

        // Rooms → null body
        when(restTemplate.exchange(
                eq("http://room-service/rooms/admin/all"), eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(null));

        // Presence → null body
        when(restTemplate.exchange(
                eq("http://presence-service/presence/online/count"), eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(null));

        // Messages → null body
        when(restTemplate.exchange(
                eq("http://message-service/messages/admin/count"), eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(null));

        Map<String, Object> analytics = adminService.getAnalytics();

        assertEquals(0, analytics.get("totalUsers"));
        assertEquals(0, analytics.get("totalRooms"));
        assertEquals(0, analytics.get("activeConnections"));
        assertEquals(0, analytics.get("totalMessages"));
    }

    @Test
    void getAnalytics_dataNotList_defaultsToZeroForUsersAndRooms() {
        // Users — data is not a List
        Map<String, Object> authBody = new HashMap<>();
        authBody.put("data", "not-a-list");
        when(restTemplate.exchange(
                eq("http://auth-service/auth/admin/users"), eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(authBody));

        // Rooms — data is not a List
        Map<String, Object> roomBody = new HashMap<>();
        roomBody.put("data", "not-a-list");
        when(restTemplate.exchange(
                eq("http://room-service/rooms/admin/all"), eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(roomBody));

        // Presence — data is not a Map
        Map<String, Object> presenceBody = new HashMap<>();
        presenceBody.put("data", "not-a-map");
        when(restTemplate.exchange(
                eq("http://presence-service/presence/online/count"), eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(presenceBody));

        // Messages — data is not a Map
        Map<String, Object> messageBody = new HashMap<>();
        messageBody.put("data", "not-a-map");
        when(restTemplate.exchange(
                eq("http://message-service/messages/admin/count"), eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(messageBody));

        Map<String, Object> analytics = adminService.getAnalytics();

        assertEquals(0, analytics.get("totalUsers"));
        assertEquals(0, analytics.get("totalRooms"));
        assertEquals(0, analytics.get("activeConnections"));
        assertEquals(0, analytics.get("totalMessages"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getAllUsers
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getAllUsers_returnsList() {
        stubGetUsers(Arrays.asList("User1", "User2"));

        List<Object> users = adminService.getAllUsers();

        assertEquals(2, users.size());
    }

    @Test
    void getAllUsers_nullBody_returnsEmptyList() {
        when(restTemplate.exchange(
                eq("http://auth-service/auth/admin/users"), eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(null));

        List<Object> users = adminService.getAllUsers();

        assertTrue(users.isEmpty());
    }

    @Test
    void getAllUsers_bodyWithoutDataKey_returnsEmptyList() {
        Map<String, Object> body = new HashMap<>();
        body.put("other", "value");
        when(restTemplate.exchange(
                eq("http://auth-service/auth/admin/users"), eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(body));

        List<Object> users = adminService.getAllUsers();

        assertTrue(users.isEmpty());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  suspendUser
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void suspendUser_callsRestTemplateAndLogsAudit() {
        when(restTemplate.exchange(
                eq("http://auth-service/auth/admin/users/1/suspend"),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        adminService.suspendUser(1L);

        verify(restTemplate).exchange(
                eq("http://auth-service/auth/admin/users/1/suspend"),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Void.class));
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  reactivateUser
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void reactivateUser_callsRestTemplateAndLogsAudit() {
        when(restTemplate.exchange(
                eq("http://auth-service/auth/admin/users/1/reactivate"),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        adminService.reactivateUser(1L);

        verify(restTemplate).exchange(
                eq("http://auth-service/auth/admin/users/1/reactivate"),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Void.class));
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  deleteUser
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void deleteUser_callsRestTemplateAndLogsAudit() {
        when(restTemplate.exchange(
                eq("http://auth-service/auth/admin/users/1"),
                eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        adminService.deleteUser(1L);

        verify(restTemplate).exchange(
                eq("http://auth-service/auth/admin/users/1"),
                eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class));
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  promoteUser
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void promoteUser_callsRestTemplateAndLogsAudit() {
        when(restTemplate.exchange(
                eq("http://auth-service/auth/admin/users/1/promote"),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        adminService.promoteUser(1L);

        verify(restTemplate).exchange(
                eq("http://auth-service/auth/admin/users/1/promote"),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Void.class));
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  demoteUser
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void demoteUser_callsRestTemplateAndLogsAudit() {
        when(restTemplate.exchange(
                eq("http://auth-service/auth/admin/users/1/demote"),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        adminService.demoteUser(1L);

        verify(restTemplate).exchange(
                eq("http://auth-service/auth/admin/users/1/demote"),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Void.class));
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getAllRooms
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getAllRooms_returnsList() {
        Map<String, Object> body = new HashMap<>();
        body.put("data", Arrays.asList("Room1", "Room2"));
        when(restTemplate.exchange(
                eq("http://room-service/rooms/admin/all"), eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(body));

        List<Object> rooms = adminService.getAllRooms();

        assertEquals(2, rooms.size());
    }

    @Test
    void getAllRooms_nullBody_returnsEmptyList() {
        when(restTemplate.exchange(
                eq("http://room-service/rooms/admin/all"), eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(null));

        assertTrue(adminService.getAllRooms().isEmpty());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  deleteRoom
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void deleteRoom_callsRestTemplateAndLogsAudit() {
        when(restTemplate.exchange(
                eq("http://room-service/rooms/admin/1"),
                eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        adminService.deleteRoom(1L);

        verify(restTemplate).exchange(
                eq("http://room-service/rooms/admin/1"),
                eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class));
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  deleteMessage
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void deleteMessage_callsRestTemplateAndLogsAudit() {
        when(restTemplate.exchange(
                eq("http://message-service/messages/admin/1"),
                eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        adminService.deleteMessage(1L);

        verify(restTemplate).exchange(
                eq("http://message-service/messages/admin/1"),
                eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class));
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  broadcast
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Actor id = 1L (from setUp security context).
     * Recipients: user id 2 (Integer) and 3 (Long) → both differ from actor so both get a DM room.
     * Both also get a direct message.
     * One broadcast notification is sent.
     */
    @Test
    void broadcast_sendsRoomsMessagesAndNotification() {
        Map<String, Object> user2 = new HashMap<>();
        user2.put("id", 2);           // Integer — exercises the Integer branch

        Map<String, Object> user3 = new HashMap<>();
        user3.put("id", 3L);          // Long    — exercises the Long branch

        stubGetUsers(Arrays.asList(user2, user3));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        Map<String, String> payload = new HashMap<>();
        payload.put("title",   "Test Broadcast");
        payload.put("message", "Hello everyone.");

        adminService.broadcast(payload);

        // DM room creation for each recipient (actor=1, both recipients ≠ 1)
        verify(restTemplate).exchange(eq("http://room-service/rooms/dm/2"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class));
        verify(restTemplate).exchange(eq("http://room-service/rooms/dm/3"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class));

        // Direct messages for every recipient (2 calls)
        verify(restTemplate, times(2)).exchange(eq("http://message-service/messages/direct"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class));

        // Broadcast notification (1 call)
        verify(restTemplate).exchange(eq("http://notification-service/notifications/admin/broadcast"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class));

        // Audit log
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    /**
     * One recipient is the actor himself (id = 1).
     * The DM-room creation for that recipient must be skipped; the direct message is still sent.
     */
    @Test
    void broadcast_skipsRoomCreationForActorItself() {
        Map<String, Object> actorUser = new HashMap<>();
        actorUser.put("id", 1);       // same as actorId in security context

        stubGetUsers(Collections.singletonList(actorUser));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        Map<String, String> payload = new HashMap<>();
        payload.put("title",   "Self Broadcast");
        payload.put("message", "No room creation for self.");

        adminService.broadcast(payload);

        // DM room creation must NOT happen for the actor
        verify(restTemplate, never()).exchange(eq("http://room-service/rooms/dm/1"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class));

        // Direct message IS still sent for every recipient (including actor)
        verify(restTemplate, times(1)).exchange(eq("http://message-service/messages/direct"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class));

        // Notification broadcast
        verify(restTemplate).exchange(eq("http://notification-service/notifications/admin/broadcast"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class));

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    /**
     * User map has null id → the null filter must discard it.
     * No recipients → no DM rooms, no DM messages, only the notification.
     */
    @Test
    void broadcast_filtersOutNullIds() {
        Map<String, Object> badUser = new HashMap<>();
        badUser.put("id", null);

        stubGetUsers(Collections.singletonList(badUser));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        Map<String, String> payload = new HashMap<>();
        payload.put("title",   "Null ID Broadcast");
        payload.put("message", "No recipients.");

        adminService.broadcast(payload);

        // Zero DM-room creations
        verify(restTemplate, never()).exchange(contains("/rooms/dm/"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class));

        // Zero direct messages
        verify(restTemplate, never()).exchange(eq("http://message-service/messages/direct"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class));

        // Notification still sent (with empty recipient list)
        verify(restTemplate).exchange(eq("http://notification-service/notifications/admin/broadcast"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class));

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    /**
     * Room creation throws → warning logged, execution continues.
     * Direct message throws → warning logged, execution continues.
     */
    @Test
    void broadcast_dmExceptionsAreSwallowed() {
        Map<String, Object> user2 = new HashMap<>();
        user2.put("id", 2);
        stubGetUsers(Collections.singletonList(user2));

        // DM room creation throws
        when(restTemplate.exchange(eq("http://room-service/rooms/dm/2"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new RuntimeException("room service down"));

        // Direct message send throws
        when(restTemplate.exchange(eq("http://message-service/messages/direct"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new RuntimeException("message service down"));

        // Notification succeeds
        when(restTemplate.exchange(eq("http://notification-service/notifications/admin/broadcast"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        Map<String, String> payload = new HashMap<>();
        payload.put("title",   "Error Broadcast");
        payload.put("message", "Errors are expected.");

        // Must not throw
        assertDoesNotThrow(() -> adminService.broadcast(payload));

        // Notification + audit still executed
        verify(restTemplate).exchange(eq("http://notification-service/notifications/admin/broadcast"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class));
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    /**
     * No authentication principal in context → actorId defaults to 1L (fallback in broadcast).
     * Also covers the branch where auth is null.
     */
    @Test
    void broadcast_withoutAuthPrincipal_usesDefaultActorId() {
        // Override security context to return non-AuthenticatedUser principal
        when(securityContext.getAuthentication()).thenReturn(null);

        Map<String, Object> user2 = new HashMap<>();
        user2.put("id", 2);
        stubGetUsers(Collections.singletonList(user2));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        Map<String, String> payload = new HashMap<>();
        payload.put("title",   "No Auth Broadcast");
        payload.put("message", "Default actor.");

        assertDoesNotThrow(() -> adminService.broadcast(payload));

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    /**
     * Users list contains a non-Map element → stream must skip it gracefully.
     */
    @Test
    void broadcast_nonMapUserEntryIsSkipped() {
        stubGetUsers(Arrays.asList("not-a-map", 42));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        Map<String, String> payload = new HashMap<>();
        payload.put("title",   "Skip Non-Map");
        payload.put("message", "Should not crash.");

        assertDoesNotThrow(() -> adminService.broadcast(payload));
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getAuditLogs
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getAuditLogs_mapsFieldsCorrectly() {
        AuditLog log1 = AuditLog.builder()
                .id(1L)
                .actorUsername("admin@example.com")
                .actionType("DELETE")
                .entityType("USER")
                .entityId("2")
                .description("Deleted user")
                .ipAddress("127.0.0.1")
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30, 0))
                .build();

        when(auditLogRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(Collections.singletonList(log1));

        List<Object> logs = adminService.getAuditLogs();

        assertEquals(1, logs.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> logMap = (Map<String, Object>) logs.get(0);

        assertEquals(1L,                        logMap.get("id"));
        assertEquals("admin@example.com",       logMap.get("actorUsername"));
        assertEquals("DELETE",                  logMap.get("actionType"));
        assertEquals("USER",                    logMap.get("entityType"));
        assertEquals("2",                       logMap.get("entityId"));
        assertEquals("Deleted user",            logMap.get("description"));
        assertEquals("127.0.0.1",               logMap.get("ipAddress"));
        assertNotNull(                          logMap.get("createdAt"));
    }

    @Test
    void getAuditLogs_emptyRepository_returnsEmptyList() {
        when(auditLogRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(Collections.emptyList());

        assertTrue(adminService.getAuditLogs().isEmpty());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getHeaders — covers the branch where RequestAttributes is null
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getHeaders_whenNoRequestContext_noAuthorizationHeaderSet() {
        // Clear request context so attributes == null
        RequestContextHolder.resetRequestAttributes();

        // getAllUsers() calls getHeaders() internally
        Map<String, Object> body = new HashMap<>();
        body.put("data", Collections.emptyList());
        when(restTemplate.exchange(
                anyString(), eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(body));

        assertDoesNotThrow(() -> adminService.getAllUsers());
    }

    @Test
    void getHeaders_whenAuthorizationHeaderIsNull_noHeaderSet() {
        // Replace request with one that has no Authorization header
        MockHttpServletRequest requestNoAuth = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requestNoAuth));

        Map<String, Object> body = new HashMap<>();
        body.put("data", Collections.emptyList());
        when(restTemplate.exchange(
                anyString(), eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(body));

        assertDoesNotThrow(() -> adminService.getAllUsers());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  logAction — covers the branch where RequestAttributes is null (IP = "")
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void logAction_whenNoRequestContextForIp_savesWithEmptyIpAddress() {
        RequestContextHolder.resetRequestAttributes();

        when(restTemplate.exchange(
                eq("http://auth-service/auth/admin/users/5/suspend"),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        adminService.suspendUser(5L);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertEquals("", captor.getValue().getIpAddress());
    }
}
