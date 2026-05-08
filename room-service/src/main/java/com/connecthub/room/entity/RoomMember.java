package com.connecthub.room.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "room_members",
       uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── FK to rooms table ──────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    // ── userId references auth-service — no FK across services ──
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private MemberRole role = MemberRole.MEMBER;

    // lastReadAt: tracks up to which message the user has read
    // used to compute unread count = messages sent after this timestamp
    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    @Column(name = "is_muted", nullable = false)
    @Builder.Default
    private Boolean isMuted = false;

    @Column(name = "unread_count", nullable = false)
    @Builder.Default
    private Integer unreadCount = 0;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;

    // ── Enum ────────────────────────────────────────────────
    public enum MemberRole {
        ADMIN,
        MEMBER
    }
}
