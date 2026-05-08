package com.connecthub.admin.controller;

import com.connecthub.admin.service.AdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.connecthub.admin.security.JwtUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link AdminController}.
 * Security filters are disabled so the controller logic can be tested in isolation.
 */
@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @MockBean
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    // ── getAnalytics ──────────────────────────────────────────────────────────

    @Test
    void getAnalytics_returnsOkWithBody() throws Exception {
        Map<String, Object> analytics = new HashMap<>();
        analytics.put("totalUsers", 10);
        analytics.put("totalRooms", 5);
        when(adminService.getAnalytics()).thenReturn(analytics);

        mockMvc.perform(get("/admin/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(10))
                .andExpect(jsonPath("$.totalRooms").value(5));

        verify(adminService).getAnalytics();
    }

    // ── getAllUsers ───────────────────────────────────────────────────────────

    @Test
    void getAllUsers_returnsOkWithList() throws Exception {
        Map<String, Object> u1 = new HashMap<>();
        u1.put("id", 1);
        u1.put("email", "user1@test.com");

        when(adminService.getAllUsers()).thenReturn(Arrays.asList(u1));

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("user1@test.com"));

        verify(adminService).getAllUsers();
    }

    @Test
    void getAllUsers_emptyList_returnsOkWithEmptyArray() throws Exception {
        when(adminService.getAllUsers()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── suspendUser ───────────────────────────────────────────────────────────

    @Test
    void suspendUser_returnsOk() throws Exception {
        mockMvc.perform(put("/admin/users/42/suspend"))
                .andExpect(status().isOk());

        verify(adminService).suspendUser(42L);
    }

    // ── reactivateUser ────────────────────────────────────────────────────────

    @Test
    void reactivateUser_returnsOk() throws Exception {
        mockMvc.perform(put("/admin/users/7/reactivate"))
                .andExpect(status().isOk());

        verify(adminService).reactivateUser(7L);
    }

    // ── deleteUser ────────────────────────────────────────────────────────────

    @Test
    void deleteUser_returnsOk() throws Exception {
        mockMvc.perform(delete("/admin/users/3"))
                .andExpect(status().isOk());

        verify(adminService).deleteUser(3L);
    }

    // ── promoteUser ───────────────────────────────────────────────────────────

    @Test
    void promoteUser_returnsOk() throws Exception {
        mockMvc.perform(put("/admin/users/5/promote"))
                .andExpect(status().isOk());

        verify(adminService).promoteUser(5L);
    }

    // ── demoteUser ────────────────────────────────────────────────────────────

    @Test
    void demoteUser_returnsOk() throws Exception {
        mockMvc.perform(put("/admin/users/5/demote"))
                .andExpect(status().isOk());

        verify(adminService).demoteUser(5L);
    }

    // ── getAllRooms ───────────────────────────────────────────────────────────

    @Test
    void getAllRooms_returnsOkWithList() throws Exception {
        when(adminService.getAllRooms()).thenReturn(Arrays.asList("Room1", "Room2"));

        mockMvc.perform(get("/admin/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(adminService).getAllRooms();
    }

    // ── deleteRoom ────────────────────────────────────────────────────────────

    @Test
    void deleteRoom_returnsOk() throws Exception {
        mockMvc.perform(delete("/admin/rooms/10"))
                .andExpect(status().isOk());

        verify(adminService).deleteRoom(10L);
    }

    // ── deleteMessage ─────────────────────────────────────────────────────────

    @Test
    void deleteMessage_returnsOk() throws Exception {
        mockMvc.perform(delete("/admin/messages/99"))
                .andExpect(status().isOk());

        verify(adminService).deleteMessage(99L);
    }

    // ── broadcast ─────────────────────────────────────────────────────────────

    @Test
    void broadcast_returnsOk() throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("title",   "Announcement");
        payload.put("message", "System maintenance tonight.");

        mockMvc.perform(post("/admin/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        verify(adminService).broadcast(any(Map.class));
    }

    // ── getAuditLogs ──────────────────────────────────────────────────────────

    @Test
    void getAuditLogs_returnsOkWithList() throws Exception {
        Map<String, Object> entry = new HashMap<>();
        entry.put("actionType", "DELETE");
        entry.put("entityType", "USER");

        when(adminService.getAuditLogs()).thenReturn(Collections.singletonList(entry));

        mockMvc.perform(get("/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].actionType").value("DELETE"));

        verify(adminService).getAuditLogs();
    }
}
