package com.connecthub.message.repository;

import com.connecthub.message.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // ── Fetch by room — paginated (infinite scroll, newest first) ──
    Page<Message> findByRoomIdAndIsDeletedFalseOrderBySentAtDesc(Long roomId, Pageable pageable);

    // ── Fetch messages before a cursor timestamp (infinite scroll upward) ──
    Page<Message> findByRoomIdAndSentAtBeforeAndIsDeletedFalseOrderBySentAtDesc(
            Long roomId, LocalDateTime before, Pageable pageable);

    // ── Fetch by sender ────────────────────────────────────
    List<Message> findBySenderIdAndIsDeletedFalse(Long senderId);

    // ── Fetch single message (including soft-deleted for admin) ──
    Optional<Message> findById(Long id);

    // ── Messages after a given timestamp (for unread count) ──
    @Query("SELECT COUNT(m) FROM Message m " +
           "WHERE m.roomId = :roomId " +
           "AND m.sentAt > :after " +
           "AND m.isDeleted = false")
    long countUnreadMessages(@Param("roomId") Long roomId,
                             @Param("after")  LocalDateTime after);

    // ── Unread messages list (for notification dispatch) ──
    @Query("SELECT m FROM Message m " +
           "WHERE m.roomId = :roomId " +
           "AND m.sentAt > :after " +
           "AND m.isDeleted = false " +
           "ORDER BY m.sentAt ASC")
    List<Message> findUnreadMessages(@Param("roomId") Long roomId,
                                     @Param("after")  LocalDateTime after);

    // ── Full-text search within a room ─────────────────────
    @Query("SELECT m FROM Message m " +
           "WHERE m.roomId = :roomId " +
           "AND m.isDeleted = false " +
           "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY m.sentAt DESC")
    List<Message> searchInRoom(@Param("roomId")  Long roomId,
                               @Param("keyword") String keyword);

    // ── Total message count for a room ─────────────────────
    long countByRoomIdAndIsDeletedFalse(Long roomId);

    // ── Pinned messages in a room ──────────────────────────
    List<Message> findByRoomIdAndIsPinnedTrueAndIsDeletedFalseOrderBySentAtDesc(Long roomId);

    // ── Direct messages between two users (newest first) ─
    @Query("SELECT m FROM Message m " +
           "WHERE m.roomId IS NULL " +
           "AND m.isDeleted = false " +
           "AND ((m.senderId = :userA AND m.recipientId = :userB) " +
           "  OR (m.senderId = :userB AND m.recipientId = :userA)) " +
           "ORDER BY m.sentAt DESC")
    Page<Message> findDirectConversation(@Param("userA") Long userA,
                                         @Param("userB") Long userB,
                                         Pageable pageable);

    // ── Direct messages before a cursor timestamp (newest first) ─
    @Query("SELECT m FROM Message m " +
           "WHERE m.roomId IS NULL " +
           "AND m.isDeleted = false " +
           "AND m.sentAt < :before " +
           "AND ((m.senderId = :userA AND m.recipientId = :userB) " +
           "  OR (m.senderId = :userB AND m.recipientId = :userA)) " +
           "ORDER BY m.sentAt DESC")
    Page<Message> findDirectConversationBefore(@Param("userA") Long userA,
                                               @Param("userB") Long userB,
                                               @Param("before") LocalDateTime before,
                                               Pageable pageable);

    // ── Bulk soft-delete all messages in a room (admin: clear history) ──
    @Modifying
    @Query("UPDATE Message m SET m.isDeleted = true, m.content = '[cleared]' " +
           "WHERE m.roomId = :roomId")
    int clearRoomHistory(@Param("roomId") Long roomId);

       @Query("SELECT m.id FROM Message m WHERE m.roomId = :roomId")
       List<Long> findIdsByRoomId(@Param("roomId") Long roomId);

    // ── Hard-delete all messages in a room (room deletion cascade) ──
    @Modifying
    @Query("DELETE FROM Message m WHERE m.roomId = :roomId")
    int deleteAllByRoomId(@Param("roomId") Long roomId);

    // ── Delivery status bulk update (DELIVERED) ────────────
    @Modifying
    @Query("UPDATE Message m SET m.deliveryStatus = :status " +
           "WHERE m.id = :id AND m.deliveryStatus <> 'READ'")
    int updateDeliveryStatus(@Param("id")     Long id,
                             @Param("status") Message.DeliveryStatus status);

    // ── Latest message in a room (for room-service lastMessageAt sync) ──
    @Query("SELECT m FROM Message m " +
           "WHERE m.roomId = :roomId AND m.isDeleted = false " +
           "ORDER BY m.sentAt DESC")
    List<Message> findLatestInRoom(@Param("roomId") Long roomId, Pageable pageable);
}
