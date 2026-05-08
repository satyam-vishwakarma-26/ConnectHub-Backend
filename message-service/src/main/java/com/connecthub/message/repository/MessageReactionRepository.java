package com.connecthub.message.repository;

import com.connecthub.message.entity.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    interface MessageReactionCountProjection {
        Long getMessageId();
        String getEmoji();
        Long getCount();
    }

    boolean existsByMessageIdAndUserIdAndEmoji(Long messageId, Long userId, String emoji);

    @Modifying
    void deleteByMessageIdAndUserIdAndEmoji(Long messageId, Long userId, String emoji);

    @Modifying
    void deleteByMessageId(Long messageId);

    @Modifying
    void deleteByMessageIdIn(Collection<Long> messageIds);

    @Query("SELECT r.messageId AS messageId, r.emoji AS emoji, COUNT(r.id) AS count " +
           "FROM MessageReaction r " +
           "WHERE r.messageId IN :messageIds " +
           "GROUP BY r.messageId, r.emoji")
    List<MessageReactionCountProjection> countGroupedByMessageIds(@Param("messageIds") Collection<Long> messageIds);
}
