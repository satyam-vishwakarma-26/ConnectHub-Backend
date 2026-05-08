package com.connecthub.admin.service;

import com.connecthub.admin.entity.AuditLog;
import com.connecthub.admin.repository.AuditLogRepository;
import com.connecthub.admin.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServiceImpl implements AdminService {

    private final RestTemplate restTemplate;
    private final AuditLogRepository auditLogRepository;

    @Value("${app.services.auth-service}")
    private String authServiceUrl;

    @Value("${app.services.room-service}")
    private String roomServiceUrl;

    @Value("${app.services.message-service}")
    private String messageServiceUrl;

    @Value("${app.services.presence-service}")
    private String presenceServiceUrl;

    @Value("${app.services.notification-service}")
    private String notificationServiceUrl;

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String token = attributes.getRequest().getHeader("Authorization");
            if (token != null) {
                headers.set("Authorization", token);
            }
        }
        return headers;
    }

    private void logAction(String actionType, String entityType, String entityId, String description) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Long actorId = 1L;
        String actorUsername = "system";

        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser) {
            AuthenticatedUser user = (AuthenticatedUser) auth.getPrincipal();
            actorId = user.getUserId();
            actorUsername = user.getEmail();
        }

        String ipAddress = "";
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            ipAddress = attributes.getRequest().getRemoteAddr();
        }

        AuditLog logEntry = AuditLog.builder()
                .actorId(actorId)
                .actorUsername(actorUsername)
                .actionType(actionType)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(logEntry);
    }

    @Override
    public Map<String, Object> getAnalytics() {
        Map<String, Object> analytics = new HashMap<>();
        
        try {
            ResponseEntity<Map<String, Object>> usersResponse = restTemplate.exchange(
                    authServiceUrl + "/auth/admin/users", HttpMethod.GET,
                    new HttpEntity<>(getHeaders()), new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = usersResponse.getBody();
            if (body != null && body.get("data") instanceof List) {
                List<?> users = (List<?>) body.get("data");
                analytics.put("totalUsers", users.size());
            } else {
                analytics.put("totalUsers", 0);
            }
        } catch (Exception e) {
            log.error("Failed to fetch users for analytics: ", e);
            analytics.put("totalUsers", 0);
        }

        try {
            ResponseEntity<Map<String, Object>> roomsResponse = restTemplate.exchange(
                    roomServiceUrl + "/rooms/admin/all", HttpMethod.GET,
                    new HttpEntity<>(getHeaders()), new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = roomsResponse.getBody();
            if (body != null && body.get("data") instanceof List) {
                List<?> rooms = (List<?>) body.get("data");
                analytics.put("totalRooms", rooms.size());
            } else {
                analytics.put("totalRooms", 0);
            }
        } catch (Exception e) {
            log.error("Failed to fetch rooms for analytics: ", e);
            analytics.put("totalRooms", 0);
        }

        try {
            ResponseEntity<Map<String, Object>> presenceResponse = restTemplate.exchange(
                    presenceServiceUrl + "/presence/online/count", HttpMethod.GET,
                    new HttpEntity<>(getHeaders()), new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = presenceResponse.getBody();
            if (body != null && body.get("data") instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                analytics.put("activeConnections", data.get("onlineCount"));
            } else {
                analytics.put("activeConnections", 0);
            }
        } catch (Exception e) {
            log.error("Failed to fetch active connections for analytics: ", e);
            analytics.put("activeConnections", 0);
        }

        try {
            ResponseEntity<Map<String, Object>> messageResponse = restTemplate.exchange(
                    messageServiceUrl + "/messages/admin/count", HttpMethod.GET,
                    new HttpEntity<>(getHeaders()), new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = messageResponse.getBody();
            if (body != null && body.get("data") instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                analytics.put("totalMessages", data.get("totalMessages"));
            } else {
                analytics.put("totalMessages", 0);
            }
        } catch (Exception e) {
            log.error("Failed to fetch total messages for analytics: ", e);
            analytics.put("totalMessages", 0);
        }

        return analytics;
    }

    @Override
    public List<Object> getAllUsers() {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                authServiceUrl + "/auth/admin/users", HttpMethod.GET,
                new HttpEntity<>(getHeaders()), new ParameterizedTypeReference<Map<String, Object>>() {});
        Map<String, Object> body = response.getBody();
        if (body != null && body.get("data") instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<Object> data = (List<Object>) body.get("data");
            return data;
        }
        return List.of();
    }

    @Override
    public void suspendUser(Long userId) {
        restTemplate.exchange(authServiceUrl + "/auth/admin/users/" + userId + "/suspend",
                HttpMethod.PUT, new HttpEntity<>(getHeaders()), Void.class);
        logAction("SUSPEND", "USER", userId.toString(), "Suspended user account");
    }

    @Override
    public void reactivateUser(Long userId) {
        restTemplate.exchange(authServiceUrl + "/auth/admin/users/" + userId + "/reactivate",
                HttpMethod.PUT, new HttpEntity<>(getHeaders()), Void.class);
        logAction("REACTIVATE", "USER", userId.toString(), "Reactivated user account");
    }

    @Override
    public void deleteUser(Long userId) {
        restTemplate.exchange(authServiceUrl + "/auth/admin/users/" + userId,
                HttpMethod.DELETE, new HttpEntity<>(getHeaders()), Void.class);
        logAction("DELETE", "USER", userId.toString(), "Permanently deleted user account");
    }

    @Override
    public void promoteUser(Long userId) {
        restTemplate.exchange(authServiceUrl + "/auth/admin/users/" + userId + "/promote",
                HttpMethod.PUT, new HttpEntity<>(getHeaders()), Void.class);
        logAction("PROMOTE", "USER", userId.toString(), "Promoted user to platform admin");
    }

    @Override
    public void demoteUser(Long userId) {
        restTemplate.exchange(authServiceUrl + "/auth/admin/users/" + userId + "/demote",
                HttpMethod.PUT, new HttpEntity<>(getHeaders()), Void.class);
        logAction("DEMOTE", "USER", userId.toString(), "Demoted platform admin to regular user");
    }

    @Override
    public List<Object> getAllRooms() {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                roomServiceUrl + "/rooms/admin/all", HttpMethod.GET,
                new HttpEntity<>(getHeaders()), new ParameterizedTypeReference<Map<String, Object>>() {});
        Map<String, Object> body = response.getBody();
        if (body != null && body.get("data") instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<Object> data = (List<Object>) body.get("data");
            return data;
        }
        return List.of();
    }

    @Override
    public void deleteRoom(Long roomId) {
        restTemplate.exchange(roomServiceUrl + "/rooms/admin/" + roomId,
                HttpMethod.DELETE, new HttpEntity<>(getHeaders()), Void.class);
        logAction("DELETE", "ROOM", roomId.toString(), "Deleted chat room");
    }

    @Override
    public void deleteMessage(Long messageId) {
        // Implementation for message deletion
        restTemplate.exchange(messageServiceUrl + "/messages/admin/" + messageId,
                HttpMethod.DELETE, new HttpEntity<>(getHeaders()), Void.class);
        logAction("DELETE", "MESSAGE", messageId.toString(), "Deleted individual message");
    }

    @Override
    public void broadcast(Map<String, String> payload) {
        // Fetch all users to notify
        List<Object> users = getAllUsers();
        List<Long> recipientIds = users.stream()
                .map(u -> {
                    if (u instanceof Map<?, ?>) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> userMap = (Map<String, Object>) u;
                        Object idObj = userMap.get("id");
                        if (idObj instanceof Integer) {
                            return ((Integer) idObj).longValue();
                        }
                        return (Long) idObj;
                    }
                    return null;
                })
                .filter(id -> id != null)
                .collect(Collectors.toList());

        String formattedMessage = "📢 **" + payload.get("title") + "**\n\n" + payload.get("message");

        var auth = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser currentAdmin = auth != null && auth.getPrincipal() instanceof AuthenticatedUser
                ? (AuthenticatedUser) auth.getPrincipal() : null;
        Long actorId = currentAdmin != null ? currentAdmin.getUserId() : 1L;

        // Send a direct message to each user so it appears in their Platform Administrator DM chat
        for (Long recipientId : recipientIds) {
            if (!recipientId.equals(actorId)) {
                try {
                    restTemplate.exchange(roomServiceUrl + "/rooms/dm/" + recipientId,
                            HttpMethod.POST, new HttpEntity<>(null, getHeaders()), Void.class);
                } catch (Exception e) {
                    log.warn("Failed to create DM room for broadcast to user {}: {}", recipientId, e.getMessage());
                }
            }

            Map<String, Object> dmRequest = new HashMap<>();
            dmRequest.put("recipientId", recipientId);
            dmRequest.put("type", "TEXT");
            dmRequest.put("content", formattedMessage);

            try {
                restTemplate.exchange(messageServiceUrl + "/messages/direct",
                        HttpMethod.POST, new HttpEntity<>(dmRequest, getHeaders()), Void.class);
            } catch (Exception e) {
                log.warn("Failed to send broadcast DM to user {}: {}", recipientId, e.getMessage());
            }
        }

        // Send notification to all users
        Map<String, Object> notificationRequest = new HashMap<>();
        notificationRequest.put("recipientIds", recipientIds);
        notificationRequest.put("actorId", actorId); // Link broadcast notification to the actual admin user
        notificationRequest.put("type", "SYSTEM");
        notificationRequest.put("title", payload.get("title"));
        notificationRequest.put("message", payload.get("message"));

        restTemplate.exchange(notificationServiceUrl + "/notifications/admin/broadcast",
                HttpMethod.POST, new HttpEntity<>(notificationRequest, getHeaders()), Void.class);

        logAction("BROADCAST", "SYSTEM", "ALL", "Sent system broadcast: " + payload.get("title"));
    }

    @Override
    public List<Object> getAuditLogs() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(log -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", log.getId());
                    map.put("actorUsername", log.getActorUsername());
                    map.put("actionType", log.getActionType());
                    map.put("entityType", log.getEntityType());
                    map.put("entityId", log.getEntityId());
                    map.put("description", log.getDescription());
                    map.put("ipAddress", log.getIpAddress());
                    map.put("createdAt", log.getCreatedAt().toString());
                    return map;
                })
                .collect(Collectors.toList());
    }
}
