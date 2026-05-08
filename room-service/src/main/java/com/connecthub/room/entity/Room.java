package com.connecthub.room.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private RoomType type = RoomType.GROUP;

    @Column(name = "created_by_id", nullable = false)
    private Long createdById;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "is_private", nullable = false)
    @Builder.Default
    private Boolean isPrivate = false;

    @Column(name = "max_members")
    @Builder.Default
    private Integer maxMembers = 100;

    @Column(name = "invite_code", unique = true, length = 20)
    private String inviteCode;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Relationship ────────────────────────────────────────
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RoomMember> members = new ArrayList<>();

    // ── Enum ────────────────────────────────────────────────
    public enum RoomType {
        GROUP,   // Multi-user named channel
        DM       // Private 1-to-1, exactly 2 members
    }
}
