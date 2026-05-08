package com.connecthub.websocket.handler;

import com.connecthub.websocket.service.SessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AdminController — exposes live WebSocket connection stats.
 * Used by the Platform Admin dashboard.
 *
 * Endpoint: GET /api/ws/stats
 * Returns:  totalConnections, totalOnlineUsers, onlineUserIds
 */
@RestController
@RequestMapping("/api/ws")
@RequiredArgsConstructor
public class AdminController {

    private final SessionRegistry sessions;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(sessions.getStats());
    }
}
