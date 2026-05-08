package com.connecthub.presence.repository;

import com.connecthub.presence.entity.UserPresence;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PresenceRepository extends CrudRepository<UserPresence, String> {

    // Find ALL active sessions for a user (multi-device)
    List<UserPresence> findByUserId(Long userId);

    // Find by WebSocket sessionId (unique per connection)
    Optional<UserPresence> findBySessionId(String sessionId);

    // Find all users currently ONLINE
    List<UserPresence> findByStatus(String status);

    // Remove all sessions for a user (full logout)
    void deleteByUserId(Long userId);

    // Remove a specific session (single device disconnect)
    void deleteBySessionId(String sessionId);
}
