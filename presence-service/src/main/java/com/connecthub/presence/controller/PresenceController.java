package com.connecthub.presence.controller;

import com.connecthub.presence.config.AuthUser;
import com.connecthub.presence.dto.request.*;
import com.connecthub.presence.dto.response.ApiResponse;
import com.connecthub.presence.dto.response.PresenceResponse;
import com.connecthub.presence.service.PresenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/presence")
@RequiredArgsConstructor
@Tag(name = "Presence", description = "Online status, session lifecycle, bulk lookups, heartbeat")
@SecurityRequirement(name = "bearerAuth")
public class PresenceController {

    private final PresenceService presenceService;

    // ────────────────────────────────────────────────────────
    // SESSION LIFECYCLE — called by websocket-handler
    // ────────────────────────────────────────────────────────

    @PostMapping("/online")
    @Operation(summary = "Mark user as online — called by websocket-handler on connect")
    public ResponseEntity<ApiResponse<PresenceResponse>> setOnline(
            @Valid @RequestBody SetOnlineRequest request) {

        PresenceResponse p = presenceService.setOnline(request);
        return ResponseEntity.ok(ApiResponse.success("User is online", p));
    }

    @PostMapping("/offline/{userId}")
    @Operation(summary = "Mark user as offline — called by websocket-handler on disconnect")
    public ResponseEntity<ApiResponse<Void>> setOffline(
            @PathVariable Long userId,
            @RequestParam(required = false) String sessionId) {

        presenceService.setOffline(userId, sessionId);
        return ResponseEntity.ok(ApiResponse.success("User is offline", null));
    }

    @PostMapping("/offline/session/{sessionId}")
    @Operation(summary = "Mark session offline by sessionId — called on WebSocket disconnect")
    public ResponseEntity<ApiResponse<Void>> setOfflineBySession(
            @PathVariable String sessionId) {

        presenceService.setOfflineBySessionId(sessionId);
        return ResponseEntity.ok(ApiResponse.success("Session closed", null));
    }

    // ────────────────────────────────────────────────────────
    // STATUS
    // ────────────────────────────────────────────────────────

    @PutMapping("/status")
    @Operation(summary = "Update online status (ONLINE / AWAY / DND / INVISIBLE)")
    public ResponseEntity<ApiResponse<PresenceResponse>> updateStatus(
            @AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody UpdateStatusRequest request) {

        PresenceResponse p = presenceService.updateStatus(me.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Status updated", p));
    }

    // ────────────────────────────────────────────────────────
    // READ
    // ────────────────────────────────────────────────────────

    @GetMapping("/{userId}")
    @Operation(summary = "Get presence for a single user")
    public ResponseEntity<ApiResponse<PresenceResponse>> getPresence(
            @PathVariable Long userId) {

        return ResponseEntity.ok(ApiResponse.success(
                presenceService.getPresence(userId)));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk presence lookup for multiple userIds — used to populate room member list")
    public ResponseEntity<ApiResponse<List<PresenceResponse>>> getBulk(
            @Valid @RequestBody BulkPresenceRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                presenceService.getBulkPresence(request.getUserIds())));
    }

    @GetMapping("/online")
    @Operation(summary = "Get all currently online users")
    public ResponseEntity<ApiResponse<List<PresenceResponse>>> getOnlineUsers() {
        return ResponseEntity.ok(ApiResponse.success(presenceService.getOnlineUsers()));
    }

    @GetMapping("/online/count")
    @Operation(summary = "Get total count of online users")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getOnlineCount() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("onlineCount", presenceService.getOnlineCount())));
    }

    @GetMapping("/{userId}/online")
    @Operation(summary = "Check if a specific user is online")
    public ResponseEntity<ApiResponse<Map<String, Object>>> isOnline(
            @PathVariable Long userId) {

        return ResponseEntity.ok(ApiResponse.success(
                Map.of("userId", userId, "isOnline", presenceService.isOnline(userId))));
    }

    // ────────────────────────────────────────────────────────
    // HEARTBEAT
    // ────────────────────────────────────────────────────────

    @PostMapping("/ping/{sessionId}")
    @Operation(summary = "Heartbeat ping — resets Redis TTL and lastPingAt")
    public ResponseEntity<ApiResponse<Void>> ping(@PathVariable String sessionId) {
        presenceService.ping(sessionId);
        return ResponseEntity.ok(ApiResponse.success("Pong", null));
    }

    // ────────────────────────────────────────────────────────
    // ADMIN
    // ────────────────────────────────────────────────────────

    @PostMapping("/admin/cleanup")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Admin — manually trigger stale session cleanup")
    public ResponseEntity<ApiResponse<Void>> cleanup() {
        presenceService.cleanStaleSessions();
        return ResponseEntity.ok(ApiResponse.success("Cleanup complete", null));
    }
}
