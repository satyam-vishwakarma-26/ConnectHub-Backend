package com.connecthub.room.repository;

import com.connecthub.room.entity.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    // All members of a room
    List<RoomMember> findByRoomId(Long roomId);

    // All rooms a user belongs to (returns membership rows)
    List<RoomMember> findByUserId(Long userId);

    // Single membership record
    Optional<RoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

    // Check membership
    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    // Members with a specific role in a room
    List<RoomMember> findByRoomIdAndRole(Long roomId, RoomMember.MemberRole role);

    // Update lastReadAt for a user in a room
    @Modifying
    @Query("UPDATE RoomMember m SET m.lastReadAt = :readAt " +
           "WHERE m.room.id = :roomId AND m.userId = :userId")
    int updateLastReadAt(@Param("roomId") Long roomId,
                         @Param("userId") Long userId,
                         @Param("readAt") LocalDateTime readAt);

    // Delete all members when a room is deleted
    void deleteByRoomId(Long roomId);

    // Count admins in a room (used before removing an admin)
    @Query("SELECT COUNT(m) FROM RoomMember m " +
           "WHERE m.room.id = :roomId AND m.role = 'ADMIN'")
    long countAdmins(@Param("roomId") Long roomId);
}
