package com.connecthub.room.repository;

import com.connecthub.room.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    // All rooms created by a user
    List<Room> findByCreatedByIdOrderByLastMessageAtDesc(Long createdById);

    // All rooms a specific user is a member of — sorted by lastMessageAt
    @Query("SELECT r FROM Room r " +
           "JOIN r.members m " +
           "WHERE m.userId = :userId " +
           "ORDER BY r.lastMessageAt DESC NULLS LAST")
    List<Room> findRoomsByUserId(@Param("userId") Long userId);

    // Find rooms by type
    List<Room> findByType(Room.RoomType type);

    // Find DM between exactly two users
    @Query("SELECT r FROM Room r " +
           "JOIN r.members m1 ON m1.userId = :userId1 " +
           "JOIN r.members m2 ON m2.userId = :userId2 " +
           "WHERE r.type = 'DM'")
    Optional<Room> findDmBetween(@Param("userId1") Long userId1,
                                 @Param("userId2") Long userId2);

    // Find room by invite code
    Optional<Room> findByInviteCode(String inviteCode);

    // Count total members in a room
    @Query("SELECT COUNT(m) FROM RoomMember m WHERE m.room.id = :roomId")
    int countMembersByRoomId(@Param("roomId") Long roomId);

    // Check if room name is taken (for GROUP rooms)
    boolean existsByNameAndType(String name, Room.RoomType type);
}
