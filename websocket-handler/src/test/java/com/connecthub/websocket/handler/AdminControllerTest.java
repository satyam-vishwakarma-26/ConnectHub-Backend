package com.connecthub.websocket.handler;

import com.connecthub.websocket.service.SessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link AdminController}.
 * Security filters disabled so the endpoint can be tested in isolation.
 */
@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionRegistry sessions;

    @Test
    void getStats_returnsOkWithStats() throws Exception {
        Map<String, Object> stats = Map.of(
                "totalConnections", 3,
                "totalOnlineUsers", 2,
                "onlineUserIds", List.of(1L, 2L)
        );
        when(sessions.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/ws/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalConnections").value(3))
                .andExpect(jsonPath("$.totalOnlineUsers").value(2));

        verify(sessions).getStats();
    }

    @Test
    void getStats_emptyRegistry_returnsZeroes() throws Exception {
        when(sessions.getStats()).thenReturn(Map.of(
                "totalConnections", 0,
                "totalOnlineUsers", 0,
                "onlineUserIds", List.of()
        ));

        mockMvc.perform(get("/api/ws/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalConnections").value(0));
    }
}
