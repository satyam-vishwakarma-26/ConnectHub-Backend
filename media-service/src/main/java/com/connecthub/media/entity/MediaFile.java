package com.connecthub.media.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a file or image uploaded by a user and stored in Cloudinary.
 *
 * <p>Upload flow:
 * <pre>
 *   Client → POST /api/media/upload (multipart)
 *     → MediaServiceImpl stores file to Cloudinary
 *     → For images: Thumbnailator generates thumbnail, stored to Cloudinary under thumbnails/
 *     → MediaFile row persisted with url + thumbnailUrl (cloudinary secure_url)
 *     → Client embeds url in subsequent STOMP CHAT_MESSAGE frame (type=IMAGE or FILE)
 * </pre>
 *
 * <p>Downloads use the Cloudinary secure_url directly (public CDN URLs).
 */
@Entity
@Table(name = "media_files", indexes = {
        @Index(name = "idx_media_uploader_id", columnList = "uploader_id"),
        @Index(name = "idx_media_room_id",     columnList = "room_id"),
        @Index(name = "idx_media_message_id",  columnList = "message_id"),
        @Index(name = "idx_media_mime_type",   columnList = "mime_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Ownership (cross-service references — no FK) ───────
    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    /** Set after the associated chat message is created; may be null initially. */
    @Column(name = "message_id")
    private Long messageId;

    // ── File metadata ──────────────────────────────────────
    /** Storage filename on Cloudinary (UUID-prefixed to avoid collisions). */
    @Column(nullable = false, length = 500)
    @Builder.Default
    private String filename = "unnamed";

    /** Original filename as provided by the client. */
    @Column(name = "original_name", nullable = false, length = 300)
    private String originalName;

    /** Cloudinary secure_url for the full-resolution file (public CDN URL). */
    @Column(nullable = false, length = 1000)
    private String url;

    /**
     * Cloudinary secure_url for the generated thumbnail.
     * Null for non-image files.
     */
    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    /** MIME type: image/jpeg, image/png, application/pdf, etc. */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_kb", nullable = false)
    private Long sizeKb;

    /** Image width in pixels — null for non-image files. */
    @Column
    private Integer width;

    /** Image height in pixels — null for non-image files. */
    @Column
    private Integer height;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 10)
    @Builder.Default
    private MediaType mediaType = MediaType.FILE;

    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;

    // ── Cloudinary public ID (stored for deletion) ──
    @Column(name = "cloudinary_public_id", nullable = false, length = 600)
    private String cloudinaryPublicId;

    @Column(name = "thumbnail_cloudinary_public_id", length = 600)
    private String thumbnailCloudinaryPublicId;

    // ── Enum ──────────────────────────────────────────────
    public enum MediaType {
        IMAGE,   // JPEG, PNG, GIF, WebP
        FILE     // PDF, DOCX, ZIP, etc.
    }
}
