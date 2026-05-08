package com.connecthub.notification.repository;

import com.connecthub.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    List<Notification> findByRecipientIdAndIsReadOrderByCreatedAtDesc(
            Long recipientId, Boolean isRead);

    long countByRecipientIdAndIsRead(Long recipientId, Boolean isRead);

    List<Notification> findByType(Notification.NotificationType type);

    List<Notification> findByRoomId(Long roomId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :id AND n.recipientId = :recipientId")
    int markOneRead(@Param("id") Long id, @Param("recipientId") Long recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipientId = :recipientId")
    int markAllRead(@Param("recipientId") Long recipientId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.recipientId = :recipientId AND n.isRead = true")
    int deleteReadByRecipient(@Param("recipientId") Long recipientId);
}
