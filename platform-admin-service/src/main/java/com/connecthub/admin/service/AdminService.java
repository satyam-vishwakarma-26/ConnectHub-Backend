package com.connecthub.admin.service;

import java.util.List;
import java.util.Map;

public interface AdminService {
    Map<String, Object> getAnalytics();
    List<Object> getAllUsers();
    void suspendUser(Long userId);
    void reactivateUser(Long userId);
    void deleteUser(Long userId);
    void promoteUser(Long userId);
    void demoteUser(Long userId);
    List<Object> getAllRooms();
    void deleteRoom(Long roomId);
    void deleteMessage(Long messageId);
    void broadcast(Map<String, String> payload);
    List<Object> getAuditLogs();
}
