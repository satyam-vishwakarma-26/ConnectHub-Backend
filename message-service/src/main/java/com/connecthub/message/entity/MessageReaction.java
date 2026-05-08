package com.connecthub.message.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_reactions",
       uniqueConstraints = @UniqueConstraint(name = "uk_message_reaction_unique", columnNames = {"message_id", "user_id", "emoji"}),
       indexes = {
               @Index(name = "idx_message_reactions_message_id", columnList = "message_id"),
               @Index(name = "idx_message_reactions_user_id", columnList = "user_id")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "emoji", nullable = false, length = 50)
    private String emoji;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
