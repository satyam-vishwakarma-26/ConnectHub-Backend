package com.connecthub.presence.controller;

import com.connecthub.presence.config.AuthUser;
import com.connecthub.presence.dto.request.*;
import com.connecthub.presence.dto.response.PresenceResponse;
import com.connecthub.presence.service.PresenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link PresenceController}.
 * Security filters are disabled so controller logic is tested in isolation.
 */
@WebMvcTest(PresenceController.class)
@AutoConfigureMockMvc(addFilters = false)
class PresenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PresenceService presenceService;

    @MockBean
    private com.connecthub.presence.config.JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    // ── Shared fixture ────────────────────────────────────────────────────────

    private PresenceResponse sampleResponse(Long userId, String status) {
        return PresenceResponse.builder()
                .userId(userId)
                .status(status)
                .sessionId("sess-1")
                .deviceType("WEB")
                .isOnline("ONLINE".equals(status) || "AWAY".equals(status))
                .connectedAt(LocalDateTime.now())
                .lastPingAt(LocalDateTime.now())
                .build();
    }

    /** Inject an authenticated principal into the security context for @AuthenticationPrincipal. */
    private void authenticateAs(Long userId, String email, String role) {
        AuthUser authUser = new AuthUser(userId, email, role);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(authUser, null, authUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /presence/online
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void setOnline_validRequest_returns200WithBody() throws Exception {
        SetOnlineRequest req = new SetOnlineRequest();
        req.setUserId(1L);
        req.setSessionId("sess-1");
        req.setStatus("ONLINE");
        req.setDeviceType("WEB");

        when(presenceService.setOnline(any(SetOnlineRequest.class)))
                .thenReturn(sampleResponse(1L, "ONLINE"));

        mockMvc.perform(post("/presence/online")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User is online"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.status").value("ONLINE"));

        verify(presenceService).setOnline(any(SetOnlineRequest.class));
    }

    @Test
    void setOnline_missingUserId_returns400() throws Exception {
        // userId is @NotNull — omit it to trigger validation
        String json = "{\"sessionId\":\"sess-1\",\"status\":\"ONLINE\"}";

        mockMvc.perform(post("/presence/online")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setOnline_missingSessionId_returns400() throws Exception {
        String json = "{\"userId\":1,\"status\":\"ONLINE\"}";

        mockMvc.perform(post("/presence/online")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /presence/offline/{userId}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void setOffline_withSessionId_returns200() throws Exception {
        mockMvc.perform(post("/presence/offline/1")
                        .param("sessionId", "sess-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User is offline"));

        verify(presenceService).setOffline(1L, "sess-1");
    }

    @Test
    void setOffline_withoutSessionId_returns200() throws Exception {
        mockMvc.perform(post("/presence/offline/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User is offline"));

        verify(presenceService).setOffline(2L, null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /presence/offline/session/{sessionId}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void setOfflineBySession_returns200() throws Exception {
        mockMvc.perform(post("/presence/offline/session/sess-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Session closed"));

        verify(presenceService).setOfflineBySessionId("sess-abc");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /presence/status
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void updateStatus_validRequest_returns200() throws Exception {
        authenticateAs(1L, "user@test.com", "USER");

        UpdateStatusRequest req = new UpdateStatusRequest();
        req.setStatus("AWAY");
        req.setCustomMessage("Lunch break");

        when(presenceService.updateStatus(eq(1L), any(UpdateStatusRequest.class)))
                .thenReturn(sampleResponse(1L, "AWAY"));

        mockMvc.perform(put("/presence/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AWAY"));

        verify(presenceService).updateStatus(eq(1L), any(UpdateStatusRequest.class));
    }

    @Test
    void updateStatus_missingStatus_returns400() throws Exception {
        authenticateAs(1L, "user@test.com", "USER");

        String json = "{\"customMessage\":\"hi\"}"; // status is @NotBlank

        mockMvc.perform(put("/presence/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /presence/{userId}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getPresence_returns200WithPresenceData() throws Exception {
        when(presenceService.getPresence(5L)).thenReturn(sampleResponse(5L, "ONLINE"));

        mockMvc.perform(get("/presence/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(5))
                .andExpect(jsonPath("$.data.status").value("ONLINE"));

        verify(presenceService).getPresence(5L);
    }

    @Test
    void getPresence_offlineUser_returns200WithOfflineData() throws Exception {
        PresenceResponse offline = PresenceResponse.offline(99L);
        when(presenceService.getPresence(99L)).thenReturn(offline);

        mockMvc.perform(get("/presence/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INVISIBLE"))
                .andExpect(jsonPath("$.data.isOnline").value(false));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /presence/bulk
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getBulk_validRequest_returnsListOf200() throws Exception {
        BulkPresenceRequest req = new BulkPresenceRequest();
        req.setUserIds(List.of(1L, 2L));

        when(presenceService.getBulkPresence(List.of(1L, 2L)))
                .thenReturn(List.of(sampleResponse(1L, "ONLINE"), sampleResponse(2L, "AWAY")));

        mockMvc.perform(post("/presence/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].status").value("ONLINE"))
                .andExpect(jsonPath("$.data[1].status").value("AWAY"));
    }

    @Test
    void getBulk_emptyUserIds_returns400() throws Exception {
        // userIds is @NotEmpty
        String json = "{\"userIds\":[]}";

        mockMvc.perform(post("/presence/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /presence/online
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getOnlineUsers_returns200WithList() throws Exception {
        when(presenceService.getOnlineUsers())
                .thenReturn(List.of(sampleResponse(1L, "ONLINE"), sampleResponse(2L, "ONLINE")));

        mockMvc.perform(get("/presence/online"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        verify(presenceService).getOnlineUsers();
    }

    @Test
    void getOnlineUsers_noUsers_returnsEmptyList() throws Exception {
        when(presenceService.getOnlineUsers()).thenReturn(List.of());

        mockMvc.perform(get("/presence/online"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /presence/online/count
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getOnlineCount_returns200WithCount() throws Exception {
        when(presenceService.getOnlineCount()).thenReturn(7);

        mockMvc.perform(get("/presence/online/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onlineCount").value(7));

        verify(presenceService).getOnlineCount();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /presence/{userId}/online
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void isOnline_userOnline_returns200True() throws Exception {
        when(presenceService.isOnline(3L)).thenReturn(true);

        mockMvc.perform(get("/presence/3/online"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(3))
                .andExpect(jsonPath("$.data.isOnline").value(true));
    }

    @Test
    void isOnline_userOffline_returns200False() throws Exception {
        when(presenceService.isOnline(4L)).thenReturn(false);

        mockMvc.perform(get("/presence/4/online"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isOnline").value(false));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /presence/ping/{sessionId}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void ping_returns200Pong() throws Exception {
        mockMvc.perform(post("/presence/ping/sess-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Pong"));

        verify(presenceService).ping("sess-xyz");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /presence/admin/cleanup
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void cleanup_returns200CleanupComplete() throws Exception {
        mockMvc.perform(post("/presence/admin/cleanup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cleanup complete"));

        verify(presenceService).cleanStaleSessions();
    }
}
