package com.connecthub.media.repository;

import com.connecthub.media.entity.MediaFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MediaRepository extends JpaRepository<MediaFile, Long> {

    // ── By uploader ────────────────────────────────────────
    List<MediaFile> findByUploaderIdOrderByUploadedAtDesc(Long uploaderId);

    // ── By room (media gallery) ────────────────────────────
    List<MediaFile> findByRoomIdOrderByUploadedAtDesc(Long roomId);

    // ── By room filtered to images only (image gallery) ───
    List<MediaFile> findByRoomIdAndMediaTypeOrderByUploadedAtDesc(
            Long roomId, MediaFile.MediaType mediaType);

    // ── By message (to get media attached to a specific message) ──
    List<MediaFile> findByMessageId(Long messageId);

    // ── By mime type ───────────────────────────────────────
    List<MediaFile> findByMimeTypeContainingIgnoreCase(String mimeType);

    // ── Count by room (for storage analytics) ─────────────
    long countByRoomId(Long roomId);

    // ── Total storage used in KB (admin analytics) ─────────
    @Query("SELECT COALESCE(SUM(m.sizeKb), 0) FROM MediaFile m")
    Long sumTotalSizeKb();

    // ── Total storage used by a specific room ──────────────
    @Query("SELECT COALESCE(SUM(m.sizeKb), 0) FROM MediaFile m WHERE m.roomId = :roomId")
    long sumSizeKbByRoomId(@Param("roomId") Long roomId);

    // ── By Cloudinary public ID (used internally for cleanup verification) ──
    Optional<MediaFile> findByCloudinaryPublicId(String cloudinaryPublicId);

    // ── Find all for a specific uploader in a room ─────────
    List<MediaFile> findByUploaderIdAndRoomIdOrderByUploadedAtDesc(Long uploaderId, Long roomId);
}
